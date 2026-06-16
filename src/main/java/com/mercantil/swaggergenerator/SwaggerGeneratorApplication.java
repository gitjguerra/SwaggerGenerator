package com.mercantil.swaggergenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.mercantil.swaggergenerator.config.InitEnvironment;

@SpringBootApplication
public class SwaggerGeneratorApplication {

	public static void main(String[] args) {
		InitEnvironment.getConfig();
		SpringApplication.run(SwaggerGeneratorApplication.class, args);
	}

}
