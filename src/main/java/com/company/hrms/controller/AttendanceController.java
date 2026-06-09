package com.company.hrms.controller;

import com.company.hrms.dto.ClockInRequest;
import com.company.hrms.dto.AttendanceLogResponse;
import com.company.hrms.entity.AttendanceLog;
import com.company.hrms.service.AttendanceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * POST /api/attendance/clock-in
     * Explicitly enforces application/json payload tracking boundaries (Fixes Ticket LF-201)
     */
    @PostMapping(value = "/clock-in", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<AttendanceLogResponse> clockIn(@RequestBody ClockInRequest payload) {
        AttendanceLog log = attendanceService.clockIn(payload.getWorkerId(), payload.getSiteId());
        return new ResponseEntity<>(new AttendanceLogResponse(log), HttpStatus.CREATED);
    }

    /**
     * POST /api/attendance/clock-out
     */
    @PostMapping(value = "/clock-out", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<AttendanceLogResponse> clockOut(@RequestBody Map<String, Long> payload) {
        Long workerId = payload.get("workerId");
        
        AttendanceLog log = attendanceService.clockOut(workerId);
        return ResponseEntity.ok(new AttendanceLogResponse(log));
    }

    /**
     * GET /api/attendance/active
     * Fetches isolated active maps directly from Redis storage matrix space
     */
    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Object>> getActiveWorkers() {
        return ResponseEntity.ok(attendanceService.getActiveWorkersFromCache());
    }

    /**
     * GET /api/attendance/history
     * Paginated historical log query records (Fixes Ticket LF-203 N+1 Query patterns)
     */
    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true) // Keeps the session open while mapping elements to DTOs
    public ResponseEntity<Page<AttendanceLogResponse>> getHistory(
            @RequestParam Long workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        
        Page<AttendanceLog> history = attendanceService.getAttendanceHistory(workerId, from, to, pageable);
        
        // Convert Page<AttendanceLog> seamlessly into Page<AttendanceLogResponse>
        Page<AttendanceLogResponse> dtoHistory = history.map(AttendanceLogResponse::new);
        
        return ResponseEntity.ok(dtoHistory);
    }
}