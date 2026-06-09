package com.company.hrms.dto;



import com.company.hrms.entity.AttendanceLog;
import java.time.LocalDateTime;

public class AttendanceLogResponse {
    private Long id;
    private Long workerId;
    private Long siteId;
    private LocalDateTime clockInTime;
    private LocalDateTime clockOutTime;
    private Double totalHours;
    private Double overtimeHours;
    private boolean flagged;

    // Map your Entity data directly to simple primitives/objects
    public AttendanceLogResponse(AttendanceLog log) {
        this.id = log.getId();
        this.workerId = log.getWorker() != null ? log.getWorker().getId() : null;
        this.siteId = log.getSite() != null ? log.getSite().getId() : null;
        this.clockInTime = log.getClockInTime();
        this.clockOutTime = log.getClockOutTime();
        this.totalHours = log.getTotalHours();
        this.overtimeHours = log.getOvertimeHours();
        this.flagged = log.isFlagged();
    }

    // Standard Getters
    public Long getId() { return id; }
    public Long getWorkerId() { return workerId; }
    public Long getSiteId() { return siteId; }
    public LocalDateTime getClockInTime() { return clockInTime; }
    public LocalDateTime getClockOutTime() { return clockOutTime; }
    public Double getTotalHours() { return totalHours; }
    public Double getOvertimeHours() { return overtimeHours; }
    public boolean isFlagged() { return flagged; }
}
