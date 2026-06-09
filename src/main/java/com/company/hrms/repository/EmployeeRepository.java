package com.company.hrms.repository;



import com.company.hrms.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    // This interface instantly gives you save(), findAll(), findById(), and deleteById()
}
