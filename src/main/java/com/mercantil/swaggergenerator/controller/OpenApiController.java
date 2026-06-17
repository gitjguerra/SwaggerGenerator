package com.mercantil.swaggergenerator.controller;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mercantil.swaggergenerator.config.ServiceLoader;
import com.mercantil.swaggergenerator.exception.ControllerException;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;
import com.mercantil.swaggergenerator.service.OpenApiGeneratorService;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiController {

	private static final Logger log = LogManager.getLogger(OpenApiController.class);

	private final OpenApiGeneratorService service;

	public OpenApiController(OpenApiGeneratorService service) {
		this.service = service;
	}

	private static final String SCHEMAS_KEY = "schemas";

	// =========================================================
	// ✅ GENERAR UN SOLO SERVICIO
	// =========================================================

	@GetMapping("/{name}")
	public OpenApiDoc get(@PathVariable String name) {

		String outputDir = getOutputDir();

		List<ServiceItem> services = ServiceLoader.load();

		ServiceItem serviceItem = services.stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst()
				.orElseThrow(
						() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Servicio no encontrado: " + name));

		// ✅ generar
		OpenApiDoc doc = service.generate(serviceItem);

		// ✅ guardar
		save(doc, serviceItem.getName(), outputDir);

		return doc;
	}

	// =========================================================
	// ✅ GENERAR TODOS LOS SERVICIOS
	// =========================================================

	@GetMapping("/all")
	public OpenApiDoc generateAll() {

		String outputDir = getOutputDir();

		OpenApiDoc merged = new OpenApiDoc();

		merged.getInfo().setTitle("API ALL");
		merged.setSecurity(List.of(Map.of("bearerAuth", List.of())));

		Map<String, Object> mergedSchemas = (Map<String, Object>) merged.getComponents().computeIfAbsent(SCHEMAS_KEY,
				k -> new LinkedHashMap<>());

		List<ServiceItem> services = ServiceLoader.load();

		services.forEach(s -> {

			// ✅ generar
			OpenApiDoc doc = service.generate(s);

			// ✅ guardar
			save(doc, s.getName(), outputDir);

			// ✅ merge paths
			merged.getPaths().putAll(doc.getPaths());

			// ✅ merge schemas
			Map<String, Object> schemas = (Map<String, Object>) doc.getComponents().get(SCHEMAS_KEY);

			if (schemas != null) {
				mergedSchemas.putAll(schemas);
			}

			// ✅ merge tags
			doc.getTags().forEach(tag -> {

				boolean exists = merged.getTags().stream().anyMatch(t -> t.get("name").equals(tag.get("name")));

				if (!exists) {
					merged.getTags().add(tag);
				}
			});

			// ✅ merge servers
			if (doc.getServers() != null) {
				merged.getServers().addAll(doc.getServers());
			}
		});

		return merged;
	}

	// =========================================================
	// ✅ UTILIDAD
	// =========================================================
	private String getOutputDir() {

		String outputDir = System.getProperty("pathOutput");

		if (outputDir == null || outputDir.isBlank()) {
			throw new ControllerException("pathOutput no configurado");
		}

		return outputDir;
	}

	private void save(OpenApiDoc doc, String serviceName, String outputDir) {

		try {

			File dir = new File(outputDir);

			if (!dir.exists()) {
				dir.mkdirs();
			}

			File file = new File(dir, serviceName + ".json");

			new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file, doc);

			log.info("Archivo generado: {}", file.getAbsolutePath());

		} catch (Exception e) {
			throw new ControllerException("Error guardando swagger", e);
		}
	}

}