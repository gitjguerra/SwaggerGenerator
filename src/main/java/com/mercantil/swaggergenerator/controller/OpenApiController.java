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

	@GetMapping("/{name}")
	public OpenApiDoc get(@PathVariable String name) {

		// =========================================================
		// ✅ CASO ALL
		// =========================================================
		if ("all".equalsIgnoreCase(name)) {

			OpenApiDoc merged = new OpenApiDoc();

			// ✅ base config opcional
			merged.info.title = "API ALL";
			merged.security = List.of(Map.of("bearerAuth", List.of()));

			config.getList().forEach(s -> {

				OpenApiDoc doc = service.generate(s);

				// ✅ 1. MERGE PATHS
				merged.paths.putAll(doc.paths);

				// ✅ 2. MERGE SCHEMAS
				Map<String, Object> mergedSchemas = (Map<String, Object>) merged.components.computeIfAbsent("schemas",
						k -> new LinkedHashMap<>());

				Map<String, Object> schemas = (Map<String, Object>) doc.components.get("schemas");

				if (schemas != null) {
					mergedSchemas.putAll(schemas);
				}

				// ✅ 3. MERGE TAGS (SIN DUPLICAR)
				doc.tags.forEach(tag -> {
					boolean exists = merged.tags.stream().anyMatch(t -> t.get("name").equals(tag.get("name")));

					if (!exists) {
						merged.tags.add(tag);
					}
				});

				// ✅ 4. SERVERS (opcional: agregar todos)
				if (doc.servers != null) {
					merged.servers.addAll(doc.servers);
				}
			});

			return merged;
		}

		// =========================================================
		// ✅ CASO NORMAL
		// =========================================================
		return config.getList().stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst()
				.map(service::generate).orElseThrow(
						() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Servicio no encontrado: " + name));
	}

	@GetMapping("/all")
	public String generateAll() {

		// ✅ tomar ruta desde application.yaml
		String outputDir = swaggerConfig.getOutputDir();

		// ✅ validación (muy recomendable)
		if (outputDir == null || outputDir.isBlank()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"La propiedad swagger.output-dir no está configurada");
		}

		// ✅ generar archivos
		List<String> files = service.generateAllAndSave(config.getList(), outputDir);

		return "✅ Archivos generados con éxito:\n" + String.join("\n", files);
	}

}