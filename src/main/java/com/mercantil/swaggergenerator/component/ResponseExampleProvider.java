package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResponseExampleProvider {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private ExampleGenerator exampleGenerator;

    // =========================================================
    // ✅ BUILD RESPONSE EXAMPLE
    // =========================================================
    public Map<String, Object> build(
            String bodyType,
            String operationName,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        if (bodyType == null) {
            return response;
        }

        // =====================================================
        // ✅ PRIORIDAD 1
        // ✅ RULES.XML RESPONSE
        // =====================================================
        Map<String, String> rules =
                ruleEngine.getResponseRules(operationName);

        if (!rules.isEmpty()) {

            buildFromRules(
                    response,
                    rules);

            return response;
        }

        // =====================================================
        // ✅ PRIORIDAD 2
        // ✅ FALLBACK AUTOMATICO
        // =====================================================
        Object generated =
                exampleMap.get(bodyType);

        if (generated == null) {

            generated =
                    exampleGenerator.buildExampleFromType(
                            bodyType);
        }

        response.put(
                "bodySalida" + operationName,
                generated);

        return response;
    }

    // =========================================================
    // ✅ BUILD FROM RULES
    // =========================================================
    private void buildFromRules(
            Map<String, Object> response,
            Map<String, String> rules) {

        for (Map.Entry<String, String> entry
                : rules.entrySet()) {

            String path =
                    entry.getKey();

            String value =
                    entry.getValue();

            setNestedValue(
                    response,
                    path,
                    parseValue(path, value));
        }
    }

    // =========================================================
    // ✅ SET NESTED VALUE
    // ✅ SOPORTA:
    // ✅ cliente.nombre
    // ✅ cuentas[0].saldo
    // ✅ clientes[0].direccion.ciudad
    // =========================================================
    private void setNestedValue(
            Map<String, Object> root,
            String path,
            Object value) {

        String[] parts =
                path.split("\\.");

        Object current =
                root;

        for (int i = 0; i < parts.length; i++) {

            String part =
                    parts[i];

            boolean last =
                    i == parts.length - 1;

            // ================================================
            // ✅ ARRAY
            // ================================================
            if (part.contains("[")
                    && part.contains("]")) {

                String field =
                        part.substring(
                                0,
                                part.indexOf("["));

                int index =
                        Integer.parseInt(
                                part.substring(
                                        part.indexOf("[") + 1,
                                        part.indexOf("]")));

                Map<String, Object> currentMap =
                        (Map<String, Object>) current;

                Object listObj =
                        currentMap.get(field);

                if (!(listObj instanceof List)) {

                    listObj =
                            new ArrayList<>();

                    currentMap.put(
                            field,
                            listObj);
                }

                List<Object> list =
                        (List<Object>) listObj;

                while (list.size() <= index) {

                    list.add(
                            new LinkedHashMap<String, Object>());
                }

                if (last) {

                    list.set(index, value);
                    return;
                }

                current =
                        list.get(index);

                continue;
            }

            // ================================================
            // ✅ NORMAL OBJECT
            // ================================================
            Map<String, Object> currentMap =
                    (Map<String, Object>) current;

            if (last) {

                currentMap.put(
                        part,
                        value);

                return;
            }

            Object next =
                    currentMap.get(part);

            if (!(next instanceof Map)) {

                next =
                        new LinkedHashMap<String, Object>();

                currentMap.put(
                        part,
                        next);
            }

            current =
                    next;
        }
    }

    // =========================================================
    // ✅ PARSE VALUE
    // =========================================================
    private Object parseValue(
            String key,
            String value) {

        if (value == null
                || value.isEmpty()) {

            return "";
        }

        String lower =
                key.toLowerCase();

        // ✅ strings criticos
        if (lower.contains("cuenta")
                || lower.contains("cta")
                || lower.contains("tarj")
                || lower.contains("telefono")
                || lower.contains("telf")
                || lower.contains("cel")
                || lower.contains("identificador")
                || lower.contains("rif")
                || value.startsWith("0")) {

            return value;
        }

        // ✅ integer
        if (value.matches("-?\\d+")) {

            try {

                return Integer.parseInt(value);

            } catch (Exception e) {

                return Long.parseLong(value);
            }
        }

        // ✅ double
        if (value.matches("-?\\d+\\.\\d+")) {

            return Double.parseDouble(value);
        }

        // ✅ boolean
        if ("true".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value)) {

            return Boolean.parseBoolean(value);
        }

        return value;
    }
}