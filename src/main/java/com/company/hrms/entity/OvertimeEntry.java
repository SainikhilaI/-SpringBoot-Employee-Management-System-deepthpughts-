package com.company.hrms.entity;



import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "overtime_entries",
    indexes = {
        @Index(name = "idx_worker_month", columnList = "worker_id, date")
    }
)
public class OvertimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_log_id", nullable = false, unique = true)
    private AttendanceLog attendanceLog;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "overtime_hours")
    private Double overtimeHours;

    @Column(name = "overtime_rate_applied", nullable = false, precision = 10, scale = 2)
    private BigDecimal overtimeRateApplied;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false)
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;

    // Boilerplate Getters and Setters
    public OvertimeEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Worker getWorker() { return worker; }
    public void setWorker(Worker worker) { this.worker = worker; }
    public AttendanceLog getAttendanceLog() { return attendanceLog; }
    public void setAttendanceLog(AttendanceLog attendanceLog) { this.attendanceLog = attendanceLog; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(Double overtimeHours) { this.overtimeHours = overtimeHours; }
    public BigDecimal getOvertimeRateApplied() { return overtimeRateApplied; }
    public void setOvertimeRateApplied(BigDecimal overtimeRateApplied) { this.overtimeRateApplied = overtimeRateApplied; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public SettlementStatus getSettlementStatus() { return settlementStatus; }
    public void setSettlementStatus(SettlementStatus settlementStatus) { this.settlementStatus = settlementStatus; }
}
