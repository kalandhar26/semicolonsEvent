package com.semicolon.healing_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(HealingServiceApplication.class, args);
	}

}
