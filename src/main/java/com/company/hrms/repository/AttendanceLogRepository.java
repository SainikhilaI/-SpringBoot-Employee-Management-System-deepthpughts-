package com.company.hrms.repository;



import com.company.hrms.entity.AttendanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    // Finds an open attendance log for a worker (where clockOutTime is null)
    Optional<AttendanceLog> findByWorkerIdAndClockOutTimeIsNull(Long workerId);

    // Optimized paginated search using EntityGraph to fetch Worker and Site in a single JOIN query (Fixes Ticket LF-203)
    @EntityGraph(attributePaths = {"worker", "site"})
    @Query("SELECT a FROM AttendanceLog a WHERE a.worker.id = :workerId " +
           "AND a.clockInTime >= :fromDate AND a.clockInTime <= :toDate")
    Page<AttendanceLog> findAttendanceHistory(
        @Param("workerId") Long workerId,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );
}
