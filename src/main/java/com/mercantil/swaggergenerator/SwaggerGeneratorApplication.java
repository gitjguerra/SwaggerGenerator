package com.mercantil.swaggergenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import com.mercantil.swaggergenerator.config.InitEnvironment;

@SpringBootApplication
public class SwaggerGeneratorApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {

        InitEnvironment.getConfig();

        return builder.sources(SwaggerGeneratorApplication.class);
    }

    public static void main(String[] args) {

        InitEnvironment.getConfig();

        SpringApplication.run(SwaggerGeneratorApplication.class, args);
    }
}