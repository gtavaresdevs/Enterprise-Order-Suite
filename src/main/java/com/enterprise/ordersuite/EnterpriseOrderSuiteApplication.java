package com.enterprise.ordersuite;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableScheduling
@SpringBootApplication
public class EnterpriseOrderSuiteApplication {

	public static void main(String[] args) {
		SpringApplication.run(EnterpriseOrderSuiteApplication.class, args);
	}

}
