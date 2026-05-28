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
	private ParserUtil parserUtil;

	@Autowired
	private ExampleGenerator exampleGenerator; // 🔥 NUEVO

	public Map<String, Object> build(MethodDeclaration method, Map<String, Map<String, Object>> schemaMap,
			List<String> ignoredTypes) {

		java.util.function.Function<String, Map<String, Object>> safeRef = type -> {
			if (type == null || ignoredTypes.contains(type)) {
				return Map.of("type", "object");
			}
			return Map.of("$ref", "#/components/schemas/" + type);
		};

		String rawReturn = method.getType().asString();
		String responseType = unwrapResponseType(rawReturn);

		if (responseType == null || ignoredTypes.contains(responseType) || "ClientResponse".equals(responseType)) {
			responseType = null;
		}

		ensureSchemaExists(responseType, schemaMap);

		String responseBodyName = responseType != null ? capitalize(responseType.replace("Response", "")) : "";

		String expectedBodyClass = "BodySalida" + responseBodyName;

		if (ignoredTypes.contains(expectedBodyClass)) {
			expectedBodyClass = null;
		}

		boolean canHaveBody = expectedBodyClass != null && schemaMap.containsKey(expectedBodyClass);

		Map<String, Object> responseSchema = new LinkedHashMap<>();
		Map<String, Object> responseProps = new LinkedHashMap<>();

		responseProps.put("headerSalida", safeRef.apply("HeaderSalida"));

		if (canHaveBody) {
			responseProps.put("bodySalida" + responseBodyName, safeRef.apply(expectedBodyClass));
		}

		responseSchema.put("type", "object");
		responseSchema.put("properties", responseProps);

		// =========================================================
		// ✅ 🔥 GENERAR EXAMPLE COMPLETO (CLAVE)
		// =========================================================
		Map<String, Object> responseExample = new LinkedHashMap<>();

		responseExample.put("headerSalida", exampleGenerator.buildExampleFromType("HeaderSalida"));

		if (canHaveBody) {
			responseExample.put("bodySalida" + responseBodyName,
					exampleGenerator.buildExampleFromType(expectedBodyClass));
		}

		// =========================================================
		// ✅ JSON FINAL CON EXAMPLE
		// =========================================================
		Map<String, Object> responseJson = new LinkedHashMap<>();
		responseJson.put("schema", responseSchema);
		responseJson.put("example", responseExample); // 🔥 CLAVE

		Map<String, Object> responses = new LinkedHashMap<>();

		responses.put("200",
				Map.of("description", "Operación exitosa", "content", Map.of("application/json", responseJson)));

		return responses;
	}

	private void ensureSchemaExists(String typeName, Map<String, Map<String, Object>> schemaMap) {

		if (typeName == null || typeName.isBlank())
			return;

		if (schemaMap.containsKey(typeName))
			return;

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("additionalProperties", true);

		schemaMap.put(typeName, schema);
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty())
			return str;
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	private String unwrapResponseType(String type) {

		List<String> wrappers = List.of("ResponseEntity", "ClientResponse", "Optional");

		String current = type;

		while (current.contains("<") && current.contains(">")) {

			String outer = current.substring(0, current.indexOf("<")).trim();

			if (!wrappers.contains(outer))
				break;

			current = parserUtil.extractGeneric(current);
		}

		return parserUtil.resolveFinalType(current);
	}
}