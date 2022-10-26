package com.example.demo;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Bean
	public Supplier<String> supply() {
		return () -> {
			logger.info("supply-continue");
			return "supply";
		};
	}
}
