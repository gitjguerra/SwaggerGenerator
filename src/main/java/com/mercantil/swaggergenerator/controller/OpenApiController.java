package com.mercantil.swaggergenerator.controller;

import java.io.File;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mercantil.swaggergenerator.config.ServiceLoader;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;
import com.mercantil.swaggergenerator.service.OpenApiGeneratorService;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiController {

	@Autowired
	private OpenApiGeneratorService service;

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

		merged.info.title = "API ALL";
		merged.security = List.of(Map.of("bearerAuth", List.of()));

		Map<String, Object> mergedSchemas = (Map<String, Object>) merged.components.computeIfAbsent("schemas",
				k -> new LinkedHashMap<>());

		List<ServiceItem> services = ServiceLoader.load();

		services.forEach(s -> {

			// ✅ generar
			OpenApiDoc doc = service.generate(s);

			// ✅ guardar
			save(doc, s.getName(), outputDir);

			// ✅ merge paths
			merged.paths.putAll(doc.paths);

			// ✅ merge schemas
			Map<String, Object> schemas = (Map<String, Object>) doc.components.get("schemas");

			if (schemas != null) {
				mergedSchemas.putAll(schemas);
			}

			// ✅ merge tags
			doc.tags.forEach(tag -> {

				boolean exists = merged.tags.stream().anyMatch(t -> t.get("name").equals(tag.get("name")));

				if (!exists) {
					merged.tags.add(tag);
				}
			});

			// ✅ merge servers
			if (doc.servers != null) {
				merged.servers.addAll(doc.servers);
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
			throw new RuntimeException("❌ pathOutput no configurado");
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

			System.out.println("✅ Archivo generado: " + file.getAbsolutePath());

		} catch (Exception e) {
			throw new RuntimeException("❌ Error guardando swagger", e);
		}
	}

}