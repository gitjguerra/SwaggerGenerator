
package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.SmartExampleUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

@Component
public class ExampleGenerator {

	@Autowired
	private TypeUtil typeUtil;

	@Autowired
	private ParserUtil parserUtil;

	@Autowired
	private HeaderExampleProvider headerProvider;

	@Autowired
	private ClassIndexer classIndexer;

	@Autowired
	private RuleEngine ruleEngine;

	// =========================================================
	// ✅ CONTEXTO CONSISTENTE
	// =========================================================
	private Map<String, Object> dataContext = new LinkedHashMap<>();

	// =========================================================
	// ✅ CACHE
	// =========================================================
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// =========================================================
	// ✅ SCHEMAS
	// =========================================================
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {

		this.schemaMap = schemaMap;

		this.exampleMap = new LinkedHashMap<>();
	}

	// =========================================================
	// ✅ BUILD EXAMPLE PRINCIPAL
	// =========================================================
	@SuppressWarnings("unchecked")
	public Object buildExampleFromType(String type) {

		// =====================================================
		// ✅ HEADERS
		// =====================================================
		if ("HeaderEntrada".equals(type)) {

			return headerProvider.buildHeaderEntrada();
		}

		if ("HeaderSalida".equals(type)) {

			return headerProvider.buildHeaderSalida();
		}

		// =====================================================
		// ✅ NULL
		// =====================================================
		if (type == null) {

			return null;
		}

		// =====================================================
		// ✅ BYTE[]
		// =====================================================
		if ("byte".equals(type) || "byte[]".equals(type)) {

			return "base64-string";
		}

		// =====================================================
		// ✅ CACHE
		// =====================================================
		String cacheKey = type + "_CTX_" + System.identityHashCode(dataContext);

		if (exampleMap.containsKey(cacheKey)) {

			return exampleMap.get(cacheKey);
		}

		// =====================================================
		// ✅ SCHEMA
		// =====================================================
		Map<String, Object> schema = schemaMap.get(type);

		if (schema == null) {

			// ✅ SILENCIOSO
			return new LinkedHashMap<>();
		}

		// =====================================================
		// ✅ ENUM
		// =====================================================
		if (schema.containsKey("enum")) {

			List<?> values = (List<?>) schema.get("enum");

			if (!values.isEmpty()) {

				return values.get(0);
			}
		}

		// =====================================================
		// ✅ REF ROOT
		// =====================================================
		if (schema.containsKey("$ref")) {

			String ref = schema.get("$ref").toString();

			String refType = ref.substring(ref.lastIndexOf("/") + 1);

			return buildExampleFromType(refType);
		}

		Map<String, Object> example = new LinkedHashMap<>();

		Object propsObj = schema.get("properties");

		// =====================================================
		// ✅ SOPORTE allOf
		// =====================================================
		if (!(propsObj instanceof Map)) {

			if ("string".equals(schema.get("type"))) {

				return "";
			}

			if ("integer".equals(schema.get("type"))) {

				return 0;
			}

			if ("number".equals(schema.get("type"))) {

				return 0.0;
			}

			if ("boolean".equals(schema.get("type"))) {

				return false;
			}

			Map<String, Object> fallback = new LinkedHashMap<>();

			if (schema.containsKey("allOf")) {

				List<?> allOf = (List<?>) schema.get("allOf");

				for (Object obj : allOf) {

					if (!(obj instanceof Map)) {

						continue;
					}

					Map<String, Object> sub = (Map<String, Object>) obj;

					// ✅ REF
					if (sub.containsKey("$ref")) {

						String ref = sub.get("$ref").toString();

						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						Object nested = buildExampleFromType(refType);

						if (nested instanceof Map) {

							fallback.putAll((Map<String, Object>) nested);
						}
					}

					// ✅ INLINE PROPERTIES
					if (sub.containsKey("properties")) {

						Map<String, Object> inlineProps = (Map<String, Object>) sub.get("properties");

						inlineProps.forEach((k, v) -> {

							Object value = SmartExampleUtil.generate(k, null, dataContext);

							fallback.put(k, value != null ? value : "");
						});
					}
				}
			}

			return fallback;
		}

		// =====================================================
		// ✅ PROPERTIES
		// =====================================================
		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((key, val) -> {

			if (!(val instanceof Map)) {

				return;
			}

			Map<String, Object> prop = (Map<String, Object>) val;

			String jsonKey = resolveJsonNameFromClass(type, key);

			// =================================================
			// ✅ OBJETO NESTED
			// =================================================
			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();

				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				Object nested = buildExampleFromType(refType);

				if (nested == null) {

					nested = new LinkedHashMap<>();
				}

				// ✅ PRESERVAR ESTRUCTURA
				example.put(jsonKey, nested);

				return;
			}

			// =================================================
			// ✅ ARRAYS
			// =================================================
			if ("array".equals(prop.get("type"))) {

				Object items = prop.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					// ✅ ARRAY OBJETO
					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();

						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						Object nested = buildExampleFromType(refType);

						if (nested == null) {

							nested = new LinkedHashMap<>();
						}

						example.put(jsonKey, List.of(nested));
					}

					// ✅ ARRAY PRIMITIVO
					else {

						example.put(jsonKey, List.of(""));
					}
				}

				return;
			}

			// =================================================
			// ✅ RULES.XML
			// =================================================
			String apiName = extractApiName(type);

			String fullPath = buildFieldPath(type, jsonKey);

			String ruleValue = ruleEngine.getRequestValue(apiName, fullPath);

			// ✅ FALLBACK SIMPLE
			if (ruleValue == null) {

				ruleValue = ruleEngine.getRequestValue(apiName, jsonKey);
			}

			Object value;

			// =================================================
			// ✅ RULE VALUE
			// =================================================
			if (ruleValue != null) {

				value = parseValueWithSchema(type, key, ruleValue);
			}

			// =================================================
			// ✅ SMART VALUE
			// =================================================
			else {

				String schemaType = (String) prop.get("type");

				Object smartValue = SmartExampleUtil.generate(key, schemaType, dataContext);

				value = normalizeSmartValue(type, key, smartValue);
			}

			example.put(jsonKey, value != null ? value : "");
		});

		// =====================================================
		// ✅ CACHE
		// =====================================================
		exampleMap.put(cacheKey, example);

		return example;
	}

	// =========================================================
	// ✅ BUILD FROM CLASS
	// =========================================================
	@SuppressWarnings("unchecked")
	public Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

		exampleMap = new LinkedHashMap<>();

		dataContext = new LinkedHashMap<>();

		Map<String, Object> example = new LinkedHashMap<>();

		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = parserUtil.resolveJsonName(field, var.getNameAsString());

				String rawType = field.getElementType().asString();

				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;

				String type = parserUtil.resolveFinalType(cleanType);

				// ✅ fallback seguridad
				if (type == null || type.isBlank()) {

					type = cleanType;
				}

				// ✅ LIST<T>
				if (cleanType.contains("List<")) {

					String generic = parserUtil.extractGeneric(cleanType);

					Object nested = buildExampleFromType(generic);

					if (nested == null) {

						nested = new LinkedHashMap<>();
					}

					example.put(name, List.of(nested));

					return;
				}

				// =====================================================
				// ✅ OBJETO COMPLEJO
				// =====================================================
				boolean primitive = typeUtil.isPrimitive(type);

				// ✅ si existe schema registrado NO puede ser primitive
				if (schemaMap.containsKey(type)) {

					primitive = false;
				}

				// ✅ fallback adicional
				if (type != null && type.startsWith("Bean")) {

					primitive = false;
				}

				if (!primitive) {

					Object nested = buildExampleFromType(type);

					if (nested == null) {

						nested = new LinkedHashMap<>();
					}

					// ✅ PRESERVAR ESTRUCTURA NESTED
					example.put(name, nested);

					return;
				}

				// ✅ PRIMITIVO
				Object value = SmartExampleUtil.generate(name, null, dataContext);

				example.put(name, value != null ? value : "");
			});
		});

		return example;
	}

	// =========================================================
	// ✅ RESOLVE JSON NAME
	// =========================================================
	private String resolveJsonNameFromClass(String className, String fieldName) {

		return classIndexer.findClass(className)

				.map(clazz ->

				clazz.getFields()

						.stream()

						.flatMap(f ->

						f.getVariables()

								.stream()

								.filter(v ->

								v.getNameAsString().equals(fieldName))

								.map(v -> f))

						.findFirst()

						.map(f ->

						parserUtil.resolveJsonName(f, fieldName))

						.orElse(fieldName))

				.orElse(fieldName);
	}

	// =========================================================
	// ✅ EXTRAER API NAME
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
	// ✅ BUILD FIELD PATH
	// =========================================================
	private String buildFieldPath(String type, String field) {

		if (type == null || field == null) {

			return field;
		}

		return type + "." + field;
	}

	// =========================================================
	// ✅ PARSE VALUE
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

	// =========================================================
	// ✅ PARSE VALUE WITH SCHEMA
	// =========================================================
	@SuppressWarnings("unchecked")
	private Object parseValueWithSchema(String type, String field, String value) {

		if (value == null || value.isEmpty()) {

			return "";
		}

		Map<String, Object> schema = schemaMap.get(type);

		if (schema != null && schema.containsKey("properties")) {

			Map<String, Object> props = (Map<String, Object>) schema.get("properties");

			if (props.containsKey(field)) {

				Map<String, Object> prop = (Map<String, Object>) props.get(field);

				String schemaType = (String) prop.get("type");

				// ✅ RESPETAR STRING
				if ("string".equals(schemaType)) {

					return value;
				}

				try {

					if ("integer".equals(schemaType)) {

						return Integer.parseInt(value);
					}

					if ("number".equals(schemaType)) {

						return Double.parseDouble(value.replace(",", "."));
					}

					if ("boolean".equals(schemaType)) {

						return Boolean.parseBoolean(value);
					}

				} catch (Exception e) {

					return parseValue(field, value);
				}
			}
		}

		return parseValue(field, value);
	}

	// =========================================================
	// ✅ SMART NORMALIZATION
	// =========================================================
	@SuppressWarnings("unchecked")
	private Object normalizeSmartValue(String type, String field, Object value) {

		if (value == null) {

			return null;
		}

		String lower = field.toLowerCase();

		// ✅ SIEMPRE STRING
		if (lower.contains("telf") || lower.contains("telefono") || lower.contains("cel") || lower.contains("tarj")
				|| lower.contains("card") || lower.contains("cta") || lower.contains("cuenta")
				|| lower.contains("identificador") || lower.contains("rif") || lower.contains("codarea")
				|| lower.contains("codpais")) {

			return value.toString();
		}

		// ✅ YA NUMERICOS
		if (value instanceof Number || value instanceof Boolean) {

			return value;
		}

		String val = value.toString();

		Map<String, Object> schema = schemaMap.get(type);

		if (schema != null && schema.containsKey("properties")) {

			Map<String, Object> props = (Map<String, Object>) schema.get("properties");

			if (props.containsKey(field)) {

				Map<String, Object> prop = (Map<String, Object>) props.get(field);

				String schemaType = (String) prop.get("type");

				try {

					// ✅ STRING
					if ("string".equals(schemaType)) {

						return val;
					}

					// ✅ INTEGER
					if ("integer".equals(schemaType)) {

						return Integer.parseInt(val);
					}

					// ✅ NUMBER
					if ("number".equals(schemaType)) {

						return Double.parseDouble(val.replace(",", "."));
					}

					// ✅ BOOLEAN
					if ("boolean".equals(schemaType)) {

						return Boolean.parseBoolean(val);
					}

				} catch (Exception e) {

					// ✅ FALLBACK SILENCIOSO
					return parseValue(field, val);
				}
			}
		}

		return parseValue(field, val);
	}
}