package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.component.special.SpecialRequestDispatcher;


@Component
public class RequestBuilder {

    @Autowired
    private HeaderExampleProvider headerProvider;

    @Autowired
    private RequestExampleProvider requestExampleProvider;

    @Autowired
    private RequestResponseResolver requestResponseResolver;

    @Autowired
    private SpecialRequestDispatcher specialDispatcher;

    // =========================================================
    // ✅ BUILD REQUEST (RESTAURADO + SEGURO)
    // =========================================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> build(String endpointPath, MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        Map<String, Object> requestProps = new LinkedHashMap<>();

        // =====================================================
        // ✅ SAFE REF
        // =====================================================
        java.util.function.Function<String, Map<String, Object>> safeRef = type -> {

            if (type == null || ignoredTypes.contains(type)) {
                return Map.of("type", "object");
            }

            return Map.of("$ref", "#/components/schemas/" + type);
        };

        // =====================================================
        // ✅ HEADER
        // =====================================================
        requestProps.put("headerEntrada", safeRef.apply("HeaderEntrada"));

        // =====================================================
        // ✅ RESOLVER BODY
        // =====================================================
        Map<String, String> requestBodies =
                requestResponseResolver.resolveRequestBodies(method, schemaMap);

        String bodyFieldName = null;
        String bodyType = null;

        if (!requestBodies.isEmpty()) {
            Map.Entry<String, String> entry = requestBodies.entrySet().iterator().next();
            bodyFieldName = entry.getKey();
            bodyType = entry.getValue();
        }

        // =====================================================
        // ✅ ASEGURAR SCHEMA
        // =====================================================
        if (bodyType != null && !ignoredTypes.contains(bodyType)) {
            ensureSchemaExists(bodyType, schemaMap);
        }

        // =====================================================
        // ✅ VALIDAR BODY REAL (IMPORTANTE 🔥)
        // =====================================================
        boolean hasBody = hasProperties(bodyType, schemaMap);

        // =====================================================
        // ✅ AGREGAR BODY SCHEMA SOLO SI TIENE PROPS
        // =====================================================
        if (bodyType != null && hasBody) {
            requestProps.put(bodyFieldName, safeRef.apply(bodyType));
        }

        // =====================================================
        // ✅ REQUEST EXAMPLE
        // =====================================================
        Map<String, Object> requestExample = new LinkedHashMap<>();

        requestExample.put("headerEntrada",
                headerProvider.buildHeaderEntrada());

        // =====================================================
        // ✅ BODY EXAMPLE SOLO SI ES VÁLIDO
        // =====================================================
        if (bodyType != null && hasBody) {

            Object bodyExample = requestExampleProvider.build(
                    endpointPath, bodyType, schemaMap, exampleMap);

            if (bodyExample != null
                    && (!(bodyExample instanceof Map)
                    || !((Map<?, ?>) bodyExample).isEmpty())) {

                requestExample.put(bodyFieldName, bodyExample);
            }
        }

        // =====================================================
        // ✅ SPECIAL HANDLER
        // =====================================================
        boolean handled = specialDispatcher.applyIfMatch(
                endpointPath, hasBody, requestProps, requestExample);

        if (handled) {
            System.out.println("✅ Request manejado por SpecialHandler");
        }

        // =====================================================
        // ✅ REQUEST FINAL
        // =====================================================
        Map<String, Object> requestJson = Map.of(
                "schema", Map.of(
                        "type", "object",
                        "properties", requestProps
                ),
                "examples", Map.of(
                        "default",
                        Map.of(
                                "summary", "Ejemplo generado",
                                "value", requestExample
                        )
                )
        );

        return Map.of(
                "required", true,
                "content", Map.of("application/json", requestJson)
        );
    }

    // =========================================================
    // ✅ VALIDAR SI EL SCHEMA TIENE PROPERTIES
    // =========================================================
    private boolean hasProperties(String type,
            Map<String, Map<String, Object>> schemaMap) {

        if (type == null) return false;

        Map<String, Object> schema = schemaMap.get(type);
        if (schema == null) return false;

        Object props = schema.get("properties");

        return props instanceof Map && !((Map<?, ?>) props).isEmpty();
    }

    // =========================================================
    // ✅ ASEGURAR SCHEMA EXISTE
    // =========================================================
    private void ensureSchemaExists(String typeName,
            Map<String, Map<String, Object>> schemaMap) {

        if (typeName == null || typeName.isBlank()) {
            return;
        }

        schemaMap.computeIfAbsent(typeName, k ->
                Map.of(
                        "type", "object",
                        "properties", new LinkedHashMap<>()
                )
        );
    }
}