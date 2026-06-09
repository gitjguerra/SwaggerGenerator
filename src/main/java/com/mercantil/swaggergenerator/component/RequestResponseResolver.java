package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class RequestResponseResolver {

    // =========================================================
    // ✅ RESOLVE REQUEST
    // =========================================================
    @SuppressWarnings("unchecked")
    public Map<String, String> resolveRequestBodies(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap) {

        Map<String, String> result =
                new LinkedHashMap<>();

        String requestType = null;
        String bodyType = null;
        String bodyFieldName = null;

        // =====================================================
        // ✅ BUSCAR REQUEST<T>
        // =====================================================
        Optional<String> requestOpt =
                method.getParameters()
                        .stream()

                        .map(p -> p.getType().asString())

                        .filter(t -> t.startsWith("Request"))

                        .findFirst();

        if (requestOpt.isPresent()) {

            requestType =
                    requestOpt.get();

            Map<String, Object> reqSchema =
                    schemaMap.get(requestType);

            // =================================================
            // ✅ BUSCAR bodyEntrada*
            // =================================================
            if (reqSchema != null) {

                Object propsObj =
                        reqSchema.get("properties");

                if (propsObj instanceof Map) {

                    Map<String, Object> props =
                            (Map<String, Object>) propsObj;

                    for (Map.Entry<String, Object> entry
                            : props.entrySet()) {

                        String key =
                                entry.getKey();

                        // ✅ SOLO bodyEntrada*
                        if (!key.toLowerCase()
                                .startsWith("bodyentrada")) {

                            continue;
                        }

                        Object value =
                                entry.getValue();

                        if (!(value instanceof Map)) {

                            continue;
                        }

                        Map<String, Object> refObj =
                                (Map<String, Object>) value;

                        if (!refObj.containsKey("$ref")) {

                            continue;
                        }

                        String ref =
                                refObj.get("$ref")
                                        .toString();

                        bodyType =
                                ref.substring(
                                        ref.lastIndexOf("/") + 1);

                        bodyFieldName =
                                key;

                        break;
                    }
                }
            }
        }

        // =====================================================
        // ✅ FALLBACK REQUEST
        // =====================================================
        if (bodyType == null
                && requestType != null) {

            bodyType =
                    requestType.replace(
                            "Request",
                            "BodyEntrada");

            bodyFieldName =
                    decapitalize(bodyType);
        }

        // =====================================================
        // ✅ RESULT
        // =====================================================
        if (bodyFieldName != null
                && bodyType != null) {

            result.put(
                    bodyFieldName,
                    bodyType);
        }

        return result;
    }

    // =========================================================
    // ✅ RESOLVE RESPONSE
    // ✅ FIX CONTAMINACIÓN ENTRE ENDPOINTS
    // =========================================================
    @SuppressWarnings("unchecked")
    public Map<String, String> resolveResponseBodies(
            String responseType,
            String operationName,
            Map<String, Map<String, Object>> schemaMap) {

        Map<String, String> bodies =
                new LinkedHashMap<>();

        // =====================================================
        // ✅ RESPONSE SCHEMA
        // =====================================================
        Map<String, Object> responseSchema =
                schemaMap.get(responseType);

        // =====================================================
        // ✅ BUSCAR SOLO bodySalida*
        // =====================================================
        if (responseSchema != null) {

            Object propsObj =
                    responseSchema.get("properties");

            if (propsObj instanceof Map) {

                Map<String, Object> props =
                        (Map<String, Object>) propsObj;

                for (Map.Entry<String, Object> entry
                        : props.entrySet()) {

                    String key =
                            entry.getKey();

                    // =================================================
                    // ✅ SOLO bodySalida*
                    // ✅ EVITA contaminación de requests
                    // ✅ EVITA contaminación entre endpoints
                    // =================================================
                    if (!key.toLowerCase()
                            .startsWith("bodysalida")) {

                        continue;
                    }

                    Object value =
                            entry.getValue();

                    if (!(value instanceof Map)) {

                        continue;
                    }

                    Map<String, Object> refObj =
                            (Map<String, Object>) value;

                    if (!refObj.containsKey("$ref")) {

                        continue;
                    }

                    String ref =
                            refObj.get("$ref")
                                    .toString();

                    String refType =
                            ref.substring(
                                    ref.lastIndexOf("/") + 1);

                    // =================================================
                    // ✅ MATCH EXACTO
                    // =================================================
                    bodies.put(
                            key,
                            refType);
                }
            }
        }

        // =====================================================
        // ✅ FALLBACK CONTROLADO
        // ✅ SOLO SI NO EXISTE RESPONSE SCHEMA
        // =====================================================
        if (bodies.isEmpty()
                && responseSchema == null) {

            String cleanedOperation =
                    capitalize(operationName);

            String bodyClass =
                    "BodySalida"
                            + cleanedOperation;

            String bodyField =
                    decapitalize(bodyClass);

            bodies.put(
                    bodyField,
                    bodyClass);

            // =================================================
            // ✅ CREAR SCHEMA VACÍO SOLO SI NO EXISTE
            // =================================================
            schemaMap.computeIfAbsent(

                    bodyClass,

                    k -> Map.of(

                            "type",
                            "object",

                            "properties",
                            new LinkedHashMap<>()));
        }

        return bodies;
    }

    // =========================================================
    // ✅ CAPITALIZE
    // =========================================================
    private String capitalize(
            String str) {

        if (str == null
                || str.isBlank()) {

            return "";
        }

        return Character.toUpperCase(
                str.charAt(0))
                + str.substring(1);
    }

    // =========================================================
    // ✅ DECAPITALIZE
    // =========================================================
    private String decapitalize(
            String str) {

        if (str == null
                || str.isBlank()) {

            return "";
        }

        return Character.toLowerCase(
                str.charAt(0))
                + str.substring(1);
    }
}