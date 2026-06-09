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
    public Map<String, String> resolveRequestBodies(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap) {

        Map<String, String> result =
                new LinkedHashMap<>();

        String requestType = null;
        String bodyType = null;
        String bodyFieldName = null;

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

                        if (!key.toLowerCase()
                                .startsWith("bodyentrada")) {

                            continue;
                        }

                        Map<String, Object> refObj =
                                (Map<String, Object>) entry.getValue();

                        if (!refObj.containsKey("$ref")) {
                            continue;
                        }

                        String ref =
                                refObj.get("$ref").toString();

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

        // ✅ fallback
        if (bodyType == null
                && requestType != null) {

            bodyType =
                    requestType.replace(
                            "Request",
                            "BodyEntrada");

            bodyFieldName =
                    "bodyEntrada"
                            + bodyType.replace(
                                    "BodyEntrada",
                                    "");
        }

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
    // =========================================================
    public Map<String, String> resolveResponseBodies(
            String responseType,
            String operationName,
            Map<String, Map<String, Object>> schemaMap) {

        Map<String, String> bodies =
                new LinkedHashMap<>();

        Map<String, Object> responseSchema =
                schemaMap.get(responseType);

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

                    if (!key.toLowerCase().startsWith("body")) {
                        continue;
                    }

                    Map<String, Object> refObj =
                            (Map<String, Object>) entry.getValue();

                    if (!refObj.containsKey("$ref")) {
                        continue;
                    }

                    String ref =
                            refObj.get("$ref").toString();

                    String refType =
                            ref.substring(
                                    ref.lastIndexOf("/") + 1);

                    bodies.put(
                            key,
                            refType);
                }
            }
        }

        // ✅ fallback
        if (bodies.isEmpty()) {

            String bodyClass =
                    "BodySalida" + operationName;

            bodies.put(
                    "bodySalida" + operationName,
                    bodyClass);

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
}