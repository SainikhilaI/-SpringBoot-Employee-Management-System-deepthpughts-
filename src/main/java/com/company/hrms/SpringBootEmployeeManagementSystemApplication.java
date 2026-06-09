package com.company.hrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = "com.company.hrms.entity")
public class SpringBootEmployeeManagementSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootEmployeeManagementSystemApplication.class, args);
	}

}
