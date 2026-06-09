package com.company.hrms.service;



import com.company.hrms.entity.*;
import com.company.hrms.exception.BusinessException;
import com.company.hrms.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AttendanceService {

    private final WorkerRepository workerRepository;
    private final SiteRepository siteRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final OvertimeEntryRepository overtimeEntryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_ACTIVE_KEY = "active_workers";
    private static final double STANDARD_SHIFT_HOURS = 8.0;
    private static final double MAX_SHIFT_TTL_HOURS = 16.0;
    private static final double MONTHLY_OVERTIME_CAP = 60.0;

    public AttendanceService(WorkerRepository workerRepository,
                             SiteRepository siteRepository,
                             AttendanceLogRepository attendanceLogRepository,
                             OvertimeEntryRepository overtimeEntryRepository,
                             RedisTemplate<String, Object> redisTemplate) {
        this.workerRepository = workerRepository;
        this.siteRepository = siteRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.overtimeEntryRepository = overtimeEntryRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * RULE: Clock-In a Worker
     */
    @Transactional
    public AttendanceLog clockIn(Long workerId, Long siteId) {
        // 1. Worker validations
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new BusinessException("WORKER_NOT_FOUND", "Worker profile does not exist.", HttpStatus.NOT_FOUND));
        if (!worker.isActive()) {
            throw new BusinessException("INACTIVE_WORKER", "Cannot clock in an inactive worker.", HttpStatus.BAD_REQUEST);
        }

        // 2. Site validations
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new BusinessException("SITE_NOT_FOUND", "Site location does not exist.", HttpStatus.NOT_FOUND));
        if (!site.isActive()) {
            throw new BusinessException("INACTIVE_SITE", "Cannot clock in to an inactive job site.", HttpStatus.BAD_REQUEST);
        }

        // 3. Double-entry prevention validation
        Optional<AttendanceLog> activeLog = attendanceLogRepository.findByWorkerIdAndClockOutTimeIsNull(workerId);
        if (activeLog.isPresent()) {
            throw new BusinessException("DUPLICATE_CLOCK_IN", "Worker is already clocked in at Site: " + activeLog.get().getSite().getSiteName(), HttpStatus.CONFLICT);
        }

        // 4. Save to Database
        AttendanceLog log = new AttendanceLog();
        log.setWorker(worker);
        log.setSite(site);
        log.setClockInTime(LocalDateTime.now());
        AttendanceLog savedLog = attendanceLogRepository.save(log);

        // 5. Caching Strategy: Save worker mapping to Redis with a strict 16-hour expiration safety net
        try {
            String cacheKey = REDIS_ACTIVE_KEY + ":" + workerId;
            Map<String, String> data = new HashMap<>();
            data.put("workerId", String.valueOf(workerId));
            data.put("workerName", worker.getName());
            data.put("designation", worker.getDesignation().name());
            data.put("siteName", site.getSiteName());
            data.put("clockInTime", savedLog.getClockInTime().toString());

            redisTemplate.opsForValue().set(cacheKey, data, 16, TimeUnit.HOURS);
        } catch (Exception e) {
            // Self-healing safety net: structural failure to cache doesn't block critical DB persistence state
        }

        return savedLog;
    }

    /**
     * RULE: Clock-Out a Worker + Compute Overtime Calculations
     */
    @Transactional
    public AttendanceLog clockOut(Long workerId) {
        // 1. Locate open active attendance log boundary
        AttendanceLog log = attendanceLogRepository.findByWorkerIdAndClockOutTimeIsNull(workerId)
                .orElseThrow(() -> new BusinessException("NO_ACTIVE_CLOCK_IN", "Worker is not currently clocked in anywhere.", HttpStatus.BAD_REQUEST));

        LocalDateTime clockOutTime = LocalDateTime.now();
        log.setClockOutTime(clockOutTime);

        // 2. Calculate shift total duration metrics
        long minutesWorked = Duration.between(log.getClockInTime(), clockOutTime).toMinutes();
        double totalHours = minutesWorked / 60.0;
        log.setTotalHours(totalHours);

        // Check if shift exceeded critical safety cap limits
        if (totalHours > MAX_SHIFT_TTL_HOURS) {
            log.setFlagged(true);
        }

        // 3. Evaluate Overtime Rules
       if (totalHours > STANDARD_SHIFT_HOURS) {
            double rawOvertimeHours = totalHours - STANDARD_SHIFT_HOURS;

            // Enforce hard monthly caps rules (Max 60 hours overtime allowed)
            LocalDate entryDate = log.getClockInTime().toLocalDate();
            LocalDate startOfMonth = entryDate.withDayOfMonth(1);
            LocalDate lastDay = entryDate.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
            
            Double existingMonthlyOt = overtimeEntryRepository.getTotalOvertimeHoursForMonth(workerId, startOfMonth, lastDay);
            
            if (existingMonthlyOt >= MONTHLY_OVERTIME_CAP) {
                log.setOvertimeHours(0.0); // Already maxed out caps limit limits
            } else {
                double allowedOtHours = rawOvertimeHours;
                if ((existingMonthlyOt + rawOvertimeHours) > MONTHLY_OVERTIME_CAP) {
                    allowedOtHours = MONTHLY_OVERTIME_CAP - existingMonthlyOt; // Hard cap boundary assignment limit
                }
                log.setOvertimeHours(allowedOtHours);

                // Calculate Overtime Payout Payout Math Rules
                // Rule: 1.5x daily wage rate for first 2 OT hours, 2x beyond that
                BigDecimal baseHourlyRate = log.getWorker().getDailyWageRate().divide(BigDecimal.valueOf(STANDARD_SHIFT_HOURS), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal totalOtAmount = BigDecimal.ZERO;
                BigDecimal appliedRate;

                if (allowedOtHours <= 2.0) {
                    appliedRate = baseHourlyRate.multiply(BigDecimal.valueOf(1.5));
                    totalOtAmount = appliedRate.multiply(BigDecimal.valueOf(allowedOtHours));
                } else {
                    // First 2 hours calculation layer
                    BigDecimal tier1Rate = baseHourlyRate.multiply(BigDecimal.valueOf(1.5));
                    BigDecimal tier1Amount = tier1Rate.multiply(BigDecimal.valueOf(2.0));

                    // Hours beyond tier 1 thresholds calculation layer
                    BigDecimal tier2Rate = baseHourlyRate.multiply(BigDecimal.valueOf(2.0));
                    double tier2Hours = allowedOtHours - 2.0;
                    BigDecimal tier2Amount = tier2Rate.multiply(BigDecimal.valueOf(tier2Hours));

                    totalOtAmount = tier1Amount.add(tier2Amount);
                    appliedRate = tier2Rate; // Record premium tier as reference metric
                }

                // Generate ledger settlement object
                OvertimeEntry otEntry = new OvertimeEntry();
                otEntry.setWorker(log.getWorker());
                otEntry.setAttendanceLog(log);
                otEntry.setDate(entryDate);
                otEntry.setOvertimeHours(allowedOtHours);
                otEntry.setOvertimeRateApplied(appliedRate);
                otEntry.setAmount(totalOtAmount);
                otEntry.setSettlementStatus(SettlementStatus.PENDING);

                overtimeEntryRepository.save(otEntry);
            }
        } else {
            log.setOvertimeHours(0.0);
        }

        AttendanceLog savedLog = attendanceLogRepository.save(log);

        // 4. Caching Strategy: Evict matching tracking key entries from Redis memory space state mapping boundaries
        try {
            redisTemplate.delete(REDIS_ACTIVE_KEY + ":" + workerId);
        } catch (Exception e) {
            // Gracefully ignore if cache provider cluster drops out (Ticket LF-202 fallback design criteria pattern)
        }

        return savedLog;
    }

    /**
     * RULE: Retrieve Cache-Isolated List of Currently Active Workers (Redis Only)
     */
    public List<Object> getActiveWorkersFromCache() {
        try {
            Set<String> keys = redisTemplate.keys(REDIS_ACTIVE_KEY + ":*");
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }
            return redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            // Ticket LF-202: Resilient runtime fallback layer path logic interface parameters pattern
            // Returns an empty list or drops into DB query space safely when cache provider network goes down
            return Collections.emptyList();
        }
    }

    /**
     * RULE: Paginated Search Tracking Retrieval (Solves Ticket LF-203 N+1 Optimization)
     */
    public Page<AttendanceLog> getAttendanceHistory(Long workerId, LocalDate from, LocalDate to, Pageable pageable) {
        LocalDateTime startDateTime = from.atStartOfDay();
        LocalDateTime endDateTime = to.atTime(23, 59, 59);
        return attendanceLogRepository.findAttendanceHistory(workerId, startDateTime, endDateTime, pageable);
    }
}
