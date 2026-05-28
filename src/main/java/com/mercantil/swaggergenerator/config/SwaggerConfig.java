package com.mercantil.swaggergenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "swagger")
public class SwaggerConfig {

    private String outputDir;

    // ✅ MÉTODO CLAVE (AGREGAR ESTO)
    public String requireOutputDir() {

        if (outputDir == null || outputDir.isBlank()) {
            throw new IllegalStateException("swagger.output-dir no configurado");
        }

        return outputDir;
    }
}