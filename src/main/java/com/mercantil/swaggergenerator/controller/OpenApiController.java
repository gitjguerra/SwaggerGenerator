package com.mercantil.swaggergenerator.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mercantil.swaggergenerator.config.ServiceConfig;
import com.mercantil.swaggergenerator.config.SwaggerConfig;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;
import com.mercantil.swaggergenerator.service.OpenApiGeneratorService;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiController {

    @Autowired
    private OpenApiGeneratorService service;

    @Autowired
    private ServiceConfig config;

    @Autowired
    private SwaggerConfig swaggerConfig;

    // =========================================================
    // ✅ GENERAR UN SOLO SERVICIO (SIEMPRE GUARDA + DEVUELVE JSON)
    // =========================================================
    @GetMapping("/{name}")
    public OpenApiDoc get(@PathVariable String name) {

        // ✅ obtiene ruta validada desde configuración
        String outputDir = swaggerConfig.requireOutputDir();

        // ✅ buscar servicio por nombre (case-insensitive)
        ServiceItem serviceItem = config.getList().stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Servicio no encontrado: " + name)
                );

        // ✅ generar OpenAPI + guardar archivo + devolver JSON
        return service.generateAndSaveReturningDoc(serviceItem, outputDir);
    }

    // =========================================================
    // ✅ GENERAR TODOS LOS SERVICIOS (SIEMPRE GUARDA + MERGE)
    // =========================================================
    @GetMapping("/all")
    public OpenApiDoc generateAll() {

        // ✅ obtiene ruta validada desde configuración
        String outputDir = swaggerConfig.requireOutputDir();

        // ✅ documento final combinado
        OpenApiDoc merged = new OpenApiDoc();

        // ✅ configuración base
        merged.info.title = "API ALL";
        merged.security = List.of(Map.of("bearerAuth", List.of()));

        // ✅ inicializar schemas UNA SOLA VEZ (optimización)
        Map<String, Object> mergedSchemas =
                (Map<String, Object>) merged.components.computeIfAbsent("schemas",
                        k -> new LinkedHashMap<>());

        // ✅ recorrer todos los servicios configurados
        config.getList().forEach(s -> {

            // ✅ generar + guardar cada servicio
            OpenApiDoc doc = service.generateAndSaveReturningDoc(s, outputDir);

            // ✅ merge de paths
            merged.paths.putAll(doc.paths);

            // ✅ merge de schemas
            Map<String, Object> schemas =
                    (Map<String, Object>) doc.components.get("schemas");

            if (schemas != null) {
                mergedSchemas.putAll(schemas);
            }

            // ✅ merge de tags (evitar duplicados)
            doc.tags.forEach(tag -> {
                boolean exists = merged.tags.stream()
                        .anyMatch(t -> t.get("name").equals(tag.get("name")));

                if (!exists) {
                    merged.tags.add(tag);
                }
            });

            // ✅ merge de servers (opcional)
            if (doc.servers != null) {
                merged.servers.addAll(doc.servers);
            }
        });

        // ✅ devolver documento combinado
        return merged;
    }
}
