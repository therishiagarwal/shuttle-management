package com.movinsync.shuttlemanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ShuttleManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShuttleManagementApplication.class, args);
	}

}
