package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the Spring Boot demo.
 * This class lives in the API/bootstrap layer and should only be used to wire the
 * application context and hand over control to Spring.
 */
@SpringBootApplication
public class DemoApplication {

	/**
	 * Boots the Spring container and exposes the HTTP endpoints defined under the interfaces layer.
	 *
	 * @param args optional command line arguments passed by the JVM
	 */
	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
