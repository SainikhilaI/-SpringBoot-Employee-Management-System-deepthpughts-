package com.company.hrms.repository;


import com.company.hrms.entity.OvertimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    @Query("SELECT o FROM OvertimeEntry o WHERE o.worker.id = :workerId " +
           "AND o.date >= :startDate AND o.date <= :endDate")
    List<OvertimeEntry> findByWorkerIdAndMonth(
        @Param("workerId") Long workerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(SUM(o.overtimeHours), 0.0) FROM OvertimeEntry o " +
           "WHERE o.worker.id = :workerId AND o.date >= :startDate AND o.date <= :endDate")
    Double getTotalOvertimeHoursForMonth(
        @Param("workerId") Long workerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
