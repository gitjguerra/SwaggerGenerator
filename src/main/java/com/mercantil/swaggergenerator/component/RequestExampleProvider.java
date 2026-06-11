package com.mercantil.swaggergenerator.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	// =========================================================
	// ✅ CACHE
	// =========================================================
	private final Map<String, Object> exampleCache = new HashMap<>();

	// =========================================================
	// ✅ BUILD REQUEST EXAMPLE
	// ✅ lookup usando endpointPath
	// =========================================================
	public Object build(String endpointPath, String bodyType, Map<String, Map<String, Object>> schemaMap,
			Map<String, Object> exampleMap) {

		if (bodyType == null) {

			return new LinkedHashMap<>();
		}

		// =====================================================
		// ✅ PRIORIDAD 1
		// ✅ exampleMap generado previamente
		// =====================================================
		Object bodyExample = exampleMap.get(bodyType);

		bodyExample = flattenIfWrapper(bodyExample);

		// =====================================================
		// ✅ PRIORIDAD 2
		// ✅ resolver por schema traversal
		// =====================================================
		if (!(bodyExample instanceof Map) || ((Map<?, ?>) bodyExample).isEmpty()) {

			bodyExample = buildExampleFromSchema(endpointPath, bodyType, schemaMap);

			bodyExample = flattenIfWrapper(bodyExample);
		}

		return bodyExample;
	}

	// =========================================================
	// ✅ CACHE + SAFE COPY
	// =========================================================
	@SuppressWarnings("unchecked")
	private Object buildExampleFromSchema(String endpointPath, String type,
			Map<String, Map<String, Object>> schemaMap) {

		String cacheKey = endpointPath + "::" + type;

		if (exampleCache.containsKey(cacheKey)) {

			return new LinkedHashMap<>((Map<String, Object>) exampleCache.get(cacheKey));
		}

		Object result = buildExampleFromSchema(endpointPath, type, schemaMap, new HashSet<>());

		if (result instanceof Map) {

			exampleCache.put(

					cacheKey,

					new LinkedHashMap<>((Map<String, Object>) result));
		}

		return result;
	}

	// =========================================================
	// ✅ BUILD RECURSIVO
	// =========================================================
	@SuppressWarnings("unchecked")
	private Object buildExampleFromSchema(String endpointPath, String type, Map<String, Map<String, Object>> schemaMap,
			Set<String> visited) {

		if (type == null) {

			return new LinkedHashMap<>();
		}

		// =====================================================
		// ✅ PREVENIR REFERENCIAS CIRCULARES
		// =====================================================
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

		// =====================================================
		// ✅ RECORRER PROPERTIES
		// =====================================================
		for (Map.Entry<String, Object> entry : props.entrySet()) {

			String fieldName = entry.getKey();

			String jsonName = resolveJsonName(type, fieldName);

			Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

			// =================================================
			// ✅ RULES.XML OVERRIDE
			// ✅ path-based lookup
			// =================================================
			String ruleValue = ruleEngine.getRequestValue(endpointPath, jsonName);

			// =================================================
			// ✅ OBJECT REF
			// =================================================
			if (fieldDef.containsKey("$ref")) {

				String ref = fieldDef.get("$ref").toString();

				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				example.put(

						jsonName,

						buildExampleFromSchema(endpointPath, refType, schemaMap, visited));

				continue;
			}

			// =================================================
			// ✅ ARRAY
			// =================================================
			if ("array".equals(fieldDef.get("type"))) {

				Object items = fieldDef.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					// =========================================
					// ✅ ARRAY REF
					// =========================================
					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();

						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(

								jsonName,

								List.of(

										buildExampleFromSchema(endpointPath, refType, schemaMap, visited)));
					}

					// =========================================
					// ✅ ARRAY PRIMITIVO
					// =========================================
					else {

						example.put(

								jsonName,

								List.of(

										mockValue((String) itemMap.get("type"))));
					}
				}

				continue;
			}

			// =================================================
			// ✅ VALUE DESDE RULES.XML
			// =================================================
			if (ruleValue != null) {

				examplePathResolver.setNestedValue(

						example,

						jsonName,

						examplePathResolver.parseValue(jsonName, ruleValue));

				continue;
			}

			// =================================================
			// ✅ MOCK DEFAULT
			// =================================================
			example.put(

					jsonName,

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
	// ✅ RESOLVER JSON PROPERTY
	// =========================================================
	private String resolveJsonName(String className, String fieldName) {

		return classIndexer.findClass(className)

				.map(clazz -> clazz.getConstructors().stream()

						.flatMap(c -> c.getParameters().stream())

						.filter(param -> param.getNameAsString().equals(fieldName))

						.map(param -> param.getAnnotationByName("JsonProperty"))

						.flatMap(java.util.Optional::stream)

						.map(ann -> {

							// ✅ CASO 1: @JsonProperty("abc")
							if (ann.isSingleMemberAnnotationExpr()) {

								return ann.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
							}

							// ✅ CASO 2: @JsonProperty(value="abc", required=true)
							if (ann.isNormalAnnotationExpr()) {

								return ann.asNormalAnnotationExpr().getPairs().stream()
										.filter(pair -> "value".equals(pair.getNameAsString()))
										.map(pair -> pair.getValue().toString().replace("\"", "")).findFirst()
										.orElse(fieldName);
							}

							return fieldName;
						})

						.findFirst()

						.orElse(fieldName))

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
}