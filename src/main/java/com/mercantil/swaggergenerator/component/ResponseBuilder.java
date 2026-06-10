
package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;

@Component
public class ResponseBuilder {

	@Autowired
	private HeaderExampleProvider headerProvider;

	@Autowired
	private ParserUtil parserUtil;

	@Autowired
	private ResponseExampleProvider responseExampleProvider;

	@Autowired
	private RequestResponseResolver requestResponseResolver;

	// ✅ FIX CRÍTICO
	@Autowired
	private SchemaBuilder schemaBuilder;

	// =========================================================
	// ✅ BUILD RESPONSE
	// ✅ SOPORTA ENDPOINT PATH
	// ✅ EVITA BODY VACÍOS
	// ✅ REGISTRA HEADERSALIDA CORRECTAMENTE
	// =========================================================
	public Map<String, Object> build(String endpointPath, MethodDeclaration method,
			Map<String, Map<String, Object>> schemaMap, Map<String, Object> exampleMap, List<String> ignoredTypes) {

		String rawReturn = method.getType().asString();

		String responseType = parserUtil.extractGeneric(rawReturn);

		// =====================================================
		// ✅ OPERATION NAME
		// =====================================================
		String operationName = capitalize(method.getNameAsString());

		// =====================================================
		// ✅ RESOLVE RESPONSE BODIES
		// =====================================================
		Map<String, String> bodies = requestResponseResolver.resolveResponseBodies(responseType, operationName,
				schemaMap);

		// =====================================================
		// ✅ ASEGURAR SCHEMAS CORPORATIVOS
		// =====================================================
		schemaBuilder.setSchemaMap(schemaMap);

		schemaBuilder.registerKnownExternalSchema("HeaderSalida");

		schemaBuilder.registerKnownExternalSchema("HeaderEntrada");

		// =====================================================
		// ✅ FINAL RESPONSE SCHEMA
		// =====================================================
		Map<String, Object> propsFinal = new LinkedHashMap<>();

		// =====================================================
		// ✅ HEADER SALIDA
		// =====================================================
		propsFinal.put(

				"headerSalida",

				Map.of("$ref", "#/components/schemas/HeaderSalida"));

		// =====================================================
		// ✅ RESPONSE EXAMPLE
		// =====================================================
		Map<String, Object> responseExample = new LinkedHashMap<>();

		responseExample.put(

				"headerSalida",

				headerProvider.buildHeaderSalida());

		// =====================================================
		// ✅ BODY RESPONSES
		// =====================================================
		for (Map.Entry<String, String> entry : bodies.entrySet()) {

			String bodyKey = entry.getKey();

			String bodyType = entry.getValue();

			// =================================================
			// ✅ IGNORAR NULL/VACÍOS
			// =================================================
			if (bodyType == null || bodyType.isBlank()) {

				continue;
			}

			// =================================================
			// ✅ ASEGURAR SCHEMA SI ES ENTERPRISE
			// =================================================
			schemaBuilder.registerKnownExternalSchema(bodyType);

			// =================================================
			// ✅ VALIDAR SCHEMA
			// =================================================
			if (!schemaMap.containsKey(bodyType)) {

				System.out.println("⚠️ Schema no encontrado: " + bodyType);

				continue;
			}

			// =================================================
			// ✅ VALIDAR BODY
			// =================================================
			boolean hasBody = hasProperties(bodyType, schemaMap);

			// ✅ operaciones sin body
			if (!hasBody) {

				continue;
			}

			// =================================================
			// ✅ SCHEMA REF
			// =================================================
			propsFinal.put(

					bodyKey,

					Map.of("$ref", "#/components/schemas/" + bodyType));

			// =================================================
			// ✅ RESPONSE EXAMPLE
			// =================================================
			Map<String, Object> generated = responseExampleProvider.build(endpointPath, bodyKey, bodyType, schemaMap,
					exampleMap);

			// =================================================
			// ✅ BODY REAL
			// =================================================
			if (generated.containsKey(bodyKey)) {

				responseExample.put(

						bodyKey,

						generated.get(bodyKey));

				continue;
			}

			// =================================================
			// ✅ FALLBACK bodySalida
			// =================================================
			if (generated.containsKey("bodySalida")) {

				responseExample.put(

						bodyKey,

						generated.get("bodySalida"));

				continue;
			}

			// =================================================
			// ✅ FALLBACK exampleMap
			// =================================================
			Object fallback = exampleMap.get(bodyType);

			// ✅ generar automáticamente si no existe
			if (fallback == null) {

				fallback = responseExampleProvider.build(endpointPath, bodyKey, bodyType, schemaMap, exampleMap)

						.get(bodyKey);
			}

			// ✅ último fallback seguro
			if (fallback == null) {

				fallback = new LinkedHashMap<>();
			}

			responseExample.put(bodyKey, fallback);
		}

		// =====================================================
		// ✅ RESPONSE JSON
		// =====================================================
		Map<String, Object> responseJson = Map.of(

				"schema",

				Map.of(

						"type", "object",

						"properties", propsFinal),

				"examples",

				Map.of(

						"default",

						Map.of(

								"summary", "Ejemplo generado",

								"value", responseExample)));

		// =====================================================
		// ✅ HTTP 200
		// =====================================================
		return Map.of(

				"200",

				Map.of(

						"description", "Operación exitosa",

						"content",

						Map.of("application/json", responseJson)));
	}

	// =========================================================
	// ✅ VALIDAR SI EL SCHEMA TIENE PROPERTIES
	// =========================================================
	private boolean hasProperties(String type, Map<String, Map<String, Object>> schemaMap) {

		if (type == null) {

			return false;
		}

		Map<String, Object> schema = schemaMap.get(type);

		if (schema == null) {

			return false;
		}

		Object props = schema.get("properties");

		return props instanceof Map && !((Map<?, ?>) props).isEmpty();
	}

	// =========================================================
	// ✅ CAPITALIZE
	// =========================================================
	private String capitalize(String str) {

		if (str == null || str.isEmpty()) {

			return str;
		}

		return Character.toUpperCase(str.charAt(0))

				+ str.substring(1);
	}
}