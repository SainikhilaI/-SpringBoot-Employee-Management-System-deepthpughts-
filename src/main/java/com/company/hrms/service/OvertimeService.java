package com.company.hrms.service;



import com.company.hrms.dto.SettleOvertimeEvent;
import com.company.hrms.entity.OvertimeEntry;
import com.company.hrms.entity.SettlementStatus;
import com.company.hrms.exception.BusinessException;
import com.company.hrms.repository.OvertimeEntryRepository;
import com.company.hrms.repository.WorkerRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OvertimeService {

    private final OvertimeEntryRepository overtimeEntryRepository;
    private final WorkerRepository workerRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OvertimeService(OvertimeEntryRepository overtimeEntryRepository,
                           WorkerRepository workerRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.overtimeEntryRepository = overtimeEntryRepository;
        this.workerRepository = workerRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Fetch a monthly breakdown summary for a worker
     */
    public Map<String, Object> getMonthlySummary(Long workerId, String monthStr) {
        YearMonth yearMonth = parseYearMonth(monthStr);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<OvertimeEntry> entries = overtimeEntryRepository.findByWorkerIdAndMonth(workerId, start, end);

        double totalHours = 0.0;
        BigDecimal totalPayout = BigDecimal.ZERO;
        String settlementStatus = "PENDING";

        List<Map<String, Object>> breakdown = entries.stream().map(entry -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", entry.getDate().toString());
            item.put("hours", entry.getOvertimeHours());
            item.put("amount", entry.getAmount());
            return item;
        }).toList();

        for (OvertimeEntry entry : entries) {
            totalHours += entry.getOvertimeHours();
            totalPayout = totalPayout.add(entry.getAmount());
            if (entry.getSettlementStatus() == SettlementStatus.SETTLED) {
                settlementStatus = "SETTLED";
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("workerId", workerId);
        response.put("month", monthStr);
        response.put("totalOvertimeHours", totalHours);
        response.put("totalPayoutAmount", totalPayout);
        response.put("settlementStatus", settlementStatus);
        response.put("breakdown", breakdown);

        return response;
    }

    /**
     * RULE: Atomic Settle Overtime Entries for a Worker + Month (Fixes Ticket LF-204)
     */
    @Transactional // Entire batch is atomic. One fail = All roll back.
    public BigDecimal settleOvertime(Long workerId, String monthStr) {
        YearMonth targetMonth = parseYearMonth(monthStr);
        
        // Rule boundary validation: Cannot settle the current ongoing calendar month
        if (targetMonth.equals(YearMonth.now())) {
            throw new BusinessException("INVALID_SETTLEMENT_PERIOD", "Cannot settle overtime details for an active, uncompleted month.", HttpStatus.BAD_REQUEST);
        }

        var worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new BusinessException("WORKER_NOT_FOUND", "Worker profile does not exist.", HttpStatus.NOT_FOUND));

        LocalDate start = targetMonth.atDay(1);
        LocalDate end = targetMonth.atEndOfMonth();

        List<OvertimeEntry> entries = overtimeEntryRepository.findByWorkerIdAndMonth(workerId, start, end);
        if (entries.isEmpty()) {
            throw new BusinessException("NO_ENTRIES_FOUND", "No overtime entries exist for the selected month.", HttpStatus.NOT_FOUND);
        }

        BigDecimal totalSettledAmount = BigDecimal.ZERO;

        for (OvertimeEntry entry : entries) {
            if (entry.getSettlementStatus() == SettlementStatus.SETTLED) {
                throw new BusinessException("ALREADY_SETTLED", "Overtime entries for this period have already been fully processed and settled.", HttpStatus.CONFLICT);
            }
            
            // Simulating a safety verification breakpoint (e.g., bad record data check to trigger robust transactional rollback testing)
            if (entry.getOvertimeHours() == null || entry.getOvertimeHours() < 0) {
                throw new BusinessException("CORRUPT_DATA", "Encountered invalid entry data parameters. Rolling back execution batch completely.", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            entry.setSettlementStatus(SettlementStatus.SETTLED);
            totalSettledAmount = totalSettledAmount.add(entry.getAmount());
        }

        overtimeEntryRepository.saveAll(entries);

        // Ticket LF-204 Solution: Publish internal decoupling message instead of invoking text notification inline!
        eventPublisher.publishEvent(new SettleOvertimeEvent(workerId, worker.getPhone(), monthStr, totalSettledAmount));

        return totalSettledAmount;
    }

    private YearMonth parseYearMonth(String monthStr) {
        try {
            return YearMonth.parse(monthStr, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (Exception e) {
            throw new BusinessException("INVALID_DATE_FORMAT", "Month parameter format must match exactly YYYY-MM structure.", HttpStatus.BAD_REQUEST);
        }
    }
}
