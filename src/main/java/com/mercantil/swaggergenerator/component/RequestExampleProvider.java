package com.mercantil.swaggergenerator.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RequestExampleProvider {

	@Autowired
	private RuleEngine ruleEngine;

	@Autowired
	private ClassIndexer classIndexer;

	@Autowired
	private ExamplePathResolver examplePathResolver;

	private final Map<String, Object> exampleCache = new HashMap<>();

	// =========================================================
	// ✅ BUILD REQUEST EXAMPLE
	// =========================================================
	public Object build(String bodyType, Map<String, Map<String, Object>> schemaMap, Map<String, Object> exampleMap) {

		if (bodyType == null) {
			return new LinkedHashMap<>();
		}

		// =====================================================
		// ✅ PRIORIDAD 1:
		// ✅ examples generados previamente
		// =====================================================
		Object bodyExample = exampleMap.get(bodyType);

		bodyExample = flattenIfWrapper(bodyExample);

		// =====================================================
		// ✅ PRIORIDAD 2:
		// ✅ fallback schema traversal
		// =====================================================
		if (!(bodyExample instanceof Map) || ((Map<?, ?>) bodyExample).isEmpty()) {

			bodyExample = buildExampleFromSchema(bodyType, schemaMap);

			bodyExample = flattenIfWrapper(bodyExample);
		}

		return bodyExample;
	}

	// =========================================================
	// ✅ CACHE + SAFE COPY
	// =========================================================
	private Object buildExampleFromSchema(String type, Map<String, Map<String, Object>> schemaMap) {

		if (exampleCache.containsKey(type)) {

			return new LinkedHashMap<>((Map<String, Object>) exampleCache.get(type));
		}

		Object result = buildExampleFromSchema(type, schemaMap, new HashSet<>());

		if (result instanceof Map) {

			exampleCache.put(type, new LinkedHashMap<>((Map<String, Object>) result));
		}

		return result;
	}

	// =========================================================
	// ✅ BUILD RECURSIVO
	// =========================================================
	private Object buildExampleFromSchema(String type, Map<String, Map<String, Object>> schemaMap,
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

		String apiName = extractApiName(type);

		// =====================================================
		// ✅ RECORRER PROPS
		// =====================================================
		for (Map.Entry<String, Object> entry : props.entrySet()) {

			String fieldName = entry.getKey();

			String jsonName = resolveJsonName(type, fieldName);

			Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

			// =================================================
			// ✅ RULES.XML REQUEST OVERRIDE
			// =================================================
			String ruleValue = ruleEngine.getRequestValue(apiName, jsonName);

			// =================================================
			// ✅ REF
			// =================================================
			if (fieldDef.containsKey("$ref")) {

				String ref = fieldDef.get("$ref").toString();

				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				example.put(jsonName,

						buildExampleFromSchema(refType, schemaMap, visited));

				continue;
			}

			// =================================================
			// ✅ ARRAY
			// =================================================
			if ("array".equals(fieldDef.get("type"))) {

				Object items = fieldDef.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();

						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(jsonName,

								List.of(buildExampleFromSchema(refType, schemaMap, visited)));

					} else {

						example.put(jsonName,

								List.of(mockValue((String) itemMap.get("type"))));
					}
				}

				continue;
			}

			// =================================================
			// ✅ RULE VALUE
			// =================================================
			if (ruleValue != null) {

				examplePathResolver.setNestedValue(example, jsonName, examplePathResolver.parseValue(jsonName, ruleValue));

				continue;
			}

			// =================================================
			// ✅ DEFAULT MOCK
			// =================================================
			example.put(jsonName,

					mockValue((String) fieldDef.get("type")));
		}

		// =====================================================
		// ✅ FLATTEN WRAPPER
		// =====================================================
		if (example.size() == 1) {

			Object onlyValue = example.values().iterator().next();

			if (onlyValue instanceof Map) {

				return onlyValue;
			}
		}

		return example;
	}

	// =========================================================
	// ✅ API NAME
	// =========================================================
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

	// =========================================================
	// ✅ JSON NAME
	// =========================================================
	private String resolveJsonName(String className, String fieldName) {

		return classIndexer.findClass(className)

				.map(clazz -> clazz.getConstructors().stream()

						.flatMap(c -> c.getParameters().stream())

						.filter(p -> p.getNameAsString().equals(fieldName))

						.map(p -> p.getAnnotationByName("JsonProperty"))

						.flatMap(Optional::stream)

						.map(a -> a.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", ""))

						.findFirst()

						.orElse(fieldName))

				.orElse(fieldName);
	}

	// =========================================================
	// ✅ MOCK VALUE
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
	// ✅ FLATTEN
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

}