package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ResponseExampleProvider {

	private static final Logger log = LogManager.getLogger(ResponseExampleProvider.class);
	
	private final RuleEngine ruleEngine;
	private final ExampleGenerator exampleGenerator;
	private final ExamplePathResolver examplePathResolver;

	public ResponseExampleProvider(RuleEngine ruleEngine, ExampleGenerator exampleGenerator,ExamplePathResolver examplePathResolver) {
		this.ruleEngine = ruleEngine;
		this.exampleGenerator = exampleGenerator;
		this.examplePathResolver = examplePathResolver;
	}


	// =========================================================
	// ✅ BUILD RESPONSE EXAMPLE
	// ✅ lookup usando endpointPath
	// ✅ usa bodyKey REAL del schema
	// ✅ evita contaminación por operationName
	// =========================================================
	public Map<String, Object> build(String endpointPath, String bodyKey, String bodyType,
			Map<String, Object> exampleMap) {

		Map<String, Object> response = new LinkedHashMap<>();

		// =====================================================
		// ✅ VALIDAR BODY
		// =====================================================
		if (bodyType == null || bodyType.isBlank()) {

			return response;
		}

		// =====================================================
		// ✅ BASE: EXAMPLE MAP o GENERACIÓN AUTOMÁTICA
		// ✅ USAR bodyKey REAL
		// ✅ NO:
		// ✅ bodySalida + operationName
		// =====================================================
		Object generated = exampleMap.get(bodyType);

		if (generated == null) {

			generated = exampleGenerator.buildExampleFromType(bodyType);
		}

		response.put(bodyKey, generated);

		// =====================================================
		// ✅ RULES.XML: sobreescribe SOLO los campos que define,
		// ✅ sin descartar el resto del ejemplo autogenerado
		// ✅ PATH-BASED LOOKUP
		// =====================================================

		log.info("endpointPath: {}", endpointPath);

		Map<String, String> rules = ruleEngine.getResponseRules(endpointPath);

		if (!rules.isEmpty()) {

			buildFromRules(response, rules);
		}

		return response;
	}

	// =========================================================
	// ✅ BUILD FROM RULES
	// =========================================================
	private void buildFromRules(Map<String, Object> response, Map<String, String> rules) {

		for (Map.Entry<String, String> entry : rules.entrySet()) {

			String path = entry.getKey();

			String value = entry.getValue();

			examplePathResolver.setNestedValue(

					response,

					path,

					examplePathResolver.parseValue(path, value));
		}
	}
}