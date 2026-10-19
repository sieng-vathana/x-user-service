package com.x.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XUserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(XUserServiceApplication.class, args);
	}
}
