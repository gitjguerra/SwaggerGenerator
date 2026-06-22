package com.mercantil.swaggergenerator.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.util.ParserUtil;

@Component
public class RequestExampleProvider {

	private final RuleEngine ruleEngine;
	private final ClassIndexer classIndexer;
	private final ExamplePathResolver examplePathResolver;
	private final ParserUtil parserUtil;

	public RequestExampleProvider(RuleEngine ruleEngine, ClassIndexer classIndexer,
			ExamplePathResolver examplePathResolver, ParserUtil parserUtil) {
		this.ruleEngine = ruleEngine;
		this.classIndexer = classIndexer;
		this.examplePathResolver = examplePathResolver;
		this.parserUtil = parserUtil;
	}

	public String getRuleValue(String endpointPath, String fieldPath) {
		return ruleEngine.getRequestValue(endpointPath, fieldPath);
	}

	// =========================================================
	// ✅ EXPOSE PARSE (SIN ROMPER NADA)
	// =========================================================
	public Object parseValuePublic(String key, String value) {
		return parseValue(key, value);
	}

	// =========================================================
	// ✅ CACHE
	// =========================================================
	private final Map<String, Object> exampleCache = new HashMap<>();

	// =========================================================
	// ✅ BUILD REQUEST EXAMPLE
	// =========================================================
	public Object build(String endpointPath, String bodyType, Map<String, Map<String, Object>> schemaMap,
			Map<String, Object> exampleMap) {

		if (bodyType == null) {
			return new LinkedHashMap<>();
		}

		Object bodyExample = exampleMap.get(bodyType);

		bodyExample = flattenIfWrapper(bodyExample);

		if (!(bodyExample instanceof Map) || ((Map<?, ?>) bodyExample).isEmpty()) {

			bodyExample = buildExampleFromSchema(endpointPath, bodyType, schemaMap);

			bodyExample = flattenIfWrapper(bodyExample);
		}

		return bodyExample;
	}

	// =========================================================
	// ✅ CACHE + SAFE COPY
	// =========================================================
	private Object buildExampleFromSchema(String endpointPath, String type,
			Map<String, Map<String, Object>> schemaMap) {

		String cacheKey = endpointPath + "::" + type;

		if (exampleCache.containsKey(cacheKey)) {
			return new LinkedHashMap<>((Map<String, Object>) exampleCache.get(cacheKey));
		}

		Object result = buildExampleFromSchema(endpointPath, type, schemaMap, new HashSet<>());

		if (result instanceof Map) {
			exampleCache.put(cacheKey, new LinkedHashMap<>((Map<String, Object>) result));
		}

		return result;
	}

	// =========================================================
	// ✅ BUILD RECURSIVO
	// =========================================================

	private Object buildExampleFromSchema(String endpointPath, String type, Map<String, Map<String, Object>> schemaMap,
			Set<String> visited) {

		if (type == null) {
			return new LinkedHashMap<>();
		}

		if (visited.contains(type)) {
			return "(circular)";
		}

		visited.add(type);

		Map<String, Object> schema = schemaMap.get(type);

		if (schema == null) {
			return new LinkedHashMap<>();
		}

		Object propsObj = schema.get("properties");

		if (!(propsObj instanceof Map)) {
			return new LinkedHashMap<>();
		}

		Map<String, Object> props = (Map<String, Object>) propsObj;

		Map<String, Object> example = new LinkedHashMap<>();

		for (Map.Entry<String, Object> entry : props.entrySet()) {

			
			String fieldName = entry.getKey();
			String jsonName = resolveJsonName(type, fieldName);


System.out.println(
    "type=" + type +
    " field=" + fieldName +
    " jsonName=" + jsonName
);

			Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

			String ruleValue = ruleEngine.getRequestValue(endpointPath, jsonName);

			if (fieldDef.containsKey("$ref")) {

				String ref = fieldDef.get("$ref").toString();

				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				example.put(jsonName, buildExampleFromSchema(endpointPath, refType, schemaMap, visited));

			} else if ("array".equals(fieldDef.get("type"))) {

				Object items = fieldDef.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();

						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(jsonName,
								List.of(buildExampleFromSchema(endpointPath, refType, schemaMap, visited)));

					} else {

						// ✅ NUEVO: buscar valor definido en rules.xml
						String arrayRuleValue = ruleEngine.getRequestValue(endpointPath, jsonName + "[0]");

						if (arrayRuleValue != null) {

							example.put(jsonName, List.of(arrayRuleValue));

						} else {

							example.put(jsonName, List.of(mockValue((String) itemMap.get("type"))));
						}
					}
				}

			} else if (ruleValue != null) {

				examplePathResolver.setNestedValue(example, jsonName,
						examplePathResolver.parseValue(jsonName, ruleValue));

			} else {

				example.put(jsonName, mockValue((String) fieldDef.get("type")));
			}
		}

		if (example.size() == 1) {

			Object onlyValue = example.values().iterator().next();

			if (onlyValue instanceof Map) {
				return onlyValue;
			}
		}

		return example;
	}

	// =========================================================
	// ✅ RESOLVER JSON PROPERTY
	// =========================================================
	private String resolveJsonName(String className, String fieldName) {

		return classIndexer.findClass(className)

				.flatMap(clazz ->

				clazz.getFields().stream()

						.filter(field -> field.getVariables().stream()
								.anyMatch(v -> fieldName.equals(v.getNameAsString())))

						.findFirst()

						.map(field -> parserUtil.resolveJsonName(field, fieldName)))

				.orElse(fieldName);
	}

	// =========================================================
	// ✅ MOCK VALUES
	// =========================================================
	private Object mockValue(String type) {

		if (type == null) {
			return "";
		}

		switch (type) {
		case "string":
			return "";
		case "integer":
			return 123;
		case "number":
			return 123.45;
		case "boolean":
			return true;
		case "array":
			return new java.util.ArrayList<>();
		default:
			return "";
		}
	}

	// =========================================================
	// ✅ FLATTEN WRAPPER SIMPLE
	// =========================================================
	private Object flattenIfWrapper(Object example) {

		if (!(example instanceof Map)) {
			return example;
		}

		Map<?, ?> map = (Map<?, ?>) example;

		if (map.size() == 1) {

			Object onlyValue = map.values().iterator().next();

			if (onlyValue instanceof Map) {
				return onlyValue;
			}
		}

		return example;
	}

	// =========================================================
	// ✅ NO TOCAR (TU MÉTODO ORIGINAL)
	// =========================================================
	private Object parseValue(String key, String value) {

		if (value == null || value.isEmpty()) {

			return "";
		}

		String lower = key.toLowerCase();

		// ✅ SIEMPRE STRING
		if (lower.contains("cuenta") || lower.contains("cta") || lower.contains("tarj") || lower.contains("card")
				|| lower.contains("identificador") || lower.contains("telf") || lower.contains("telefono")
				|| lower.contains("cel") || value.startsWith("0")) {

			return value;
		}

		// ✅ INTEGER
		if (value.matches("-?\\d+")) {

			try {

				return Integer.parseInt(value);

			} catch (Exception e) {

				return Long.parseLong(value);
			}
		}

		// ✅ DECIMAL
		if (value.matches("-?\\d+\\.\\d+")) {

			return Double.parseDouble(value);
		}

		// ✅ BOOLEAN
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {

			return Boolean.parseBoolean(value);
		}

		return value;
	}
}