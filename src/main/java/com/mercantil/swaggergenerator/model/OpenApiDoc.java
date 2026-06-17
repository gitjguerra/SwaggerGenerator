package com.mercantil.swaggergenerator.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenApiDoc {

    public static final String SCHEMAS = "schemas";
    public static final String SECURITY_SCHEMES = "securitySchemes";

    private String openapi = "3.0.1";

    private Info info = new Info();

    private List<Map<String, String>> servers = new ArrayList<>();

    private List<Map<String, String>> tags = new ArrayList<>();

    private Map<String, Object> paths = new LinkedHashMap<>();

    private Map<String, Object> components = new HashMap<>();

    private List<Map<String, List<String>>> security;

    public OpenApiDoc() {

        components.put(SCHEMAS, new LinkedHashMap<>());

        Map<String, Object> securitySchemes = new LinkedHashMap<>();

        securitySchemes.put("bearerAuth", Map.of(
                "type", "http",
                "scheme", "bearer",
                "bearerFormat", "JWT"));

        components.put(SECURITY_SCHEMES, securitySchemes);
    }
}