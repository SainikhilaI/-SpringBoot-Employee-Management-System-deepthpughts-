package com.company.hrms.service;



import com.company.hrms.entity.Employee;
import com.company.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    // Constructor Injection for the repository dependency
    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    // Business Method: Create/Save a new employee
    public Employee createEmployee(Employee employee) {
        // You can add data validation rules here if needed down the road
        return employeeRepository.save(employee);
    }

    // Business Method: Fetch all employees
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    // Business Method: Fetch employee by ID
    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }
}
