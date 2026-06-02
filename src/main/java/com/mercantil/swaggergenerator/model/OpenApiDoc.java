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

    public String openapi = "3.0.1";

    public Info info = new Info();

    public List<Map<String, String>> servers = new ArrayList<>();

    public List<Map<String, String>> tags = new ArrayList<>();

    public Map<String, Object> paths = new LinkedHashMap<>();

    public Map<String, Object> components = new HashMap<>();
    
    public List<Map<String, List<String>>> security;

	public OpenApiDoc() {
	
	    components.put("schemas", new LinkedHashMap<>());
	
	    // ✅ 🔐 SECURITY SCHEME
	    Map<String, Object> securitySchemes = new LinkedHashMap<>();
	
	    securitySchemes.put("bearerAuth", Map.of(
	        "type", "http",
	        "scheme", "bearer",
	        "bearerFormat", "JWT"
	    ));
	
	    components.put("securitySchemes", securitySchemes);
	}

}