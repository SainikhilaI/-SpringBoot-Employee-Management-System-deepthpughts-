package com.company.hrms.controller;

import com.company.hrms.service.OvertimeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    /**
     * GET /api/overtime/summary/{workerId}?month=YYYY-MM
     * Aligns perfectly with Part 1, Task 2 spec layout boundaries.
     */
    @GetMapping(value = "/summary/{workerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable Long workerId,
            @RequestParam String month) { // Format: YYYY-MM
        
        return ResponseEntity.ok(overtimeService.getMonthlySummary(workerId, month));
    }

    /**
     * POST /api/overtime/settle/{workerId}?month=YYYY-MM
     * Atomic process matching parameters handling transactional closures safely (Fixes Ticket LF-204)
     * Note: Removed 'consumes' boundary since inputs pass strictly via URL query segments.
     */
    @PostMapping(value = "/settle/{workerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> settleOvertime(
            @PathVariable Long workerId,
            @RequestParam String month) { // Format: YYYY-MM

        BigDecimal settledAmount = overtimeService.settleOvertime(workerId, month);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SETTLED");
        response.put("message", "Overtime processing completed successfully.");
        response.put("workerId", workerId);
        response.put("month", month);
        response.put("totalSettledAmount", settledAmount);

        return ResponseEntity.ok(response);
    }
}