package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResponseExampleProvider {

	@Autowired
	private RuleEngine ruleEngine;

	@Autowired
	private ExampleGenerator exampleGenerator;

	@Autowired
	private ExamplePathResolver examplePathResolver;

	// =========================================================
	// ✅ BUILD RESPONSE EXAMPLE
	// =========================================================
	public Map<String, Object> build(String bodyType, String operationName, Map<String, Map<String, Object>> schemaMap,
			Map<String, Object> exampleMap) {

		Map<String, Object> response = new LinkedHashMap<>();

		if (bodyType == null) {
			return response;
		}

		// =====================================================
		// ✅ PRIORIDAD 1
		// ✅ RULES.XML RESPONSE
		// =====================================================
		String apiName = extractApiName(bodyType);

		Map<String, String> rules = ruleEngine.getResponseRules(apiName);

		if (!rules.isEmpty()) {

			buildFromRules(response, rules);

			return response;
		}

		// =====================================================
		// ✅ PRIORIDAD 2
		// ✅ FALLBACK AUTOMATICO
		// =====================================================
		Object generated = exampleMap.get(bodyType);

		if (generated == null) {

			generated = exampleGenerator.buildExampleFromType(bodyType);
		}

		response.put("bodySalida" + operationName, generated);

		return response;
	}

	// =========================================================
	// ✅ BUILD FROM RULES
	// =========================================================
	private void buildFromRules(Map<String, Object> response, Map<String, String> rules) {

		for (Map.Entry<String, String> entry : rules.entrySet()) {

			String path = entry.getKey();

			String value = entry.getValue();

			examplePathResolver.setNestedValue(response, path, examplePathResolver.parseValue(path, value));
		}
	}

	private String extractApiName(String type) {

		if (type == null) {
			return null;
		}

		String normalized = type.trim();

		if (normalized.startsWith("BodyEntrada")) {

			return normalized.substring("BodyEntrada".length()).trim();
		}

		if (normalized.startsWith("BodySalida")) {

			return normalized.substring("BodySalida".length()).trim();
		}

		return normalized;
	}

}