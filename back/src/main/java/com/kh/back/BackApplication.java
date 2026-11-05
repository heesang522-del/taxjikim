package com.kh.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// DormantScheduler의 @Scheduled가 실제로 동작하려면 이 어노테이션이 있어야 한다.
@SpringBootApplication
@EnableScheduling
public class BackApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackApplication.class, args);
	}

}
