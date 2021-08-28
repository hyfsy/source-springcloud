package com.hyf.lopenfeign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class LearnOpenfeignApplication {

	public static void main(String[] args) {
		SpringApplication.run(LearnOpenfeignApplication.class, args);
	}

}
