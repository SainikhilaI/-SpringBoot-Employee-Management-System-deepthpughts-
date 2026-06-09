package com.company.hrms.dto;


import java.math.BigDecimal;

public class SettleOvertimeEvent {
    private final Long workerId;
    private final String workerPhone;
    private final String month;
    private final BigDecimal totalAmount;

    public SettleOvertimeEvent(Long workerId, String workerPhone, String month, BigDecimal totalAmount) {
        this.workerId = workerId;
        this.workerPhone = workerPhone;
        this.month = month;
        this.totalAmount = totalAmount;
    }

    public Long getWorkerId() { return workerId; }
    public String getWorkerPhone() { return workerPhone; }
    public String getMonth() { return month; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
