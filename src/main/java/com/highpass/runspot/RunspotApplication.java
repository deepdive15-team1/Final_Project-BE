package com.highpass.runspot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RunspotApplication {

	public static void main(String[] args) {
		SpringApplication.run(RunspotApplication.class, args);
	}

}
