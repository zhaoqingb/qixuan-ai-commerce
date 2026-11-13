package com.qixuan.commerce;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
public class CommerceApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommerceApiApplication.class, args);
	}

}
