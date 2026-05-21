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

    public Map<String, Object> paths = new LinkedHashMap<>();

    public Map<String, Object> components = new HashMap<>();

    public OpenApiDoc() {
        components.put("schemas", new LinkedHashMap<>());
    }
}