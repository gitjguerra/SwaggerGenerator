package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;
import com.mercantil.swaggergenerator.util.SmartExampleUtil;

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

	// ✅ CONTEXTO DE VALORES CONSISTENTES
	private Map<String, Object> dataContext = new LinkedHashMap<>();

	// ✅ mapa de ejemplos (CACHE AISLADO POR EJECUCIÓN)
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ mapa de schemas
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
		this.schemaMap = schemaMap;

		// 🔥 FIX CRÍTICO: limpiar cache COMPLETO
		this.exampleMap = new LinkedHashMap<>();
	}

	// =========================================================
	// ✅ GENERADOR PRINCIPAL
	// =========================================================
	public Object buildExampleFromType(String type) {

		if ("HeaderEntrada".equals(type))
			return headerProvider.buildHeaderEntrada();

		if ("HeaderSalida".equals(type))
			return headerProvider.buildHeaderSalida();

		if (type == null)
			return null;

		if (type.equals("byte") || type.equals("byte[]"))
			return "base64-string";

		// 🔥 FIX: CLAVE DE CACHE AISLADA
		String cacheKey = type + "_CTX_" + System.identityHashCode(dataContext);

		if (exampleMap.containsKey(cacheKey)) {
			return exampleMap.get(cacheKey);
		}

		Map<String, Object> schema = schemaMap.get(type);

		if (schema == null) {

			System.out.println("⚠️ No schema encontrado para: " + type);

			return null;
		}

		// ✅ ENUM
		if (schema.containsKey("enum")) {
			List<?> values = (List<?>) schema.get("enum");
			if (!values.isEmpty())
				return values.get(0);
		}

		// ✅ RESOLVER REF ROOT
		if (schema.containsKey("$ref")) {

			String ref = schema.get("$ref").toString();
			String refType = ref.substring(ref.lastIndexOf("/") + 1);

			return buildExampleFromType(refType);
		}

		Map<String, Object> example = new LinkedHashMap<>();

		Object propsObj = schema.get("properties");

		// =====================================================
		// 🔥 FIX CRÍTICO: SOPORTE HERENCIA (allOf)
		// =====================================================
		if (!(propsObj instanceof Map)) {

			// ✅ soporte enum/string/etc
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

					if (obj instanceof Map) {

						Map<String, Object> sub = (Map<String, Object>) obj;

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

								Object value = SmartExampleUtil.generate(k, dataContext);
								fallback.put(k, value != null ? value : "");

							});
						}
					}
				}
			}

			return fallback;
		}

		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((key, val) -> {

			if (!(val instanceof Map))
				return;

			Map<String, Object> prop = (Map<String, Object>) val;

			String jsonKey = resolveJsonNameFromClass(type, key);

			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				Object nested = buildExampleFromType(refType);

				example.put(jsonKey, nested);

				return;
			}

			if ("array".equals(prop.get("type"))) {

				Object items = prop.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();
						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(jsonKey, List.of(buildExampleFromType(refType)));

					} else {

						example.put(jsonKey, List.of(""));
					}
				}
				return;
			}

			String apiName = extractApiName(type);

			String ruleValue = ruleEngine.getRequestValue(apiName, jsonKey);

			Object value;

			if (ruleValue != null) {
				value = parseValueWithSchema(type, key, ruleValue); // ✅ rules
			} else {
				Object smartValue = SmartExampleUtil.generate(key, dataContext);
				value = normalizeSmartValue(type, key, smartValue);
			}

			example.put(jsonKey, value != null ? value : "");
		});

		exampleMap.put(cacheKey, example);

		return example;

	}

	// =========================================================
	// ✅ BUILD FROM CLASS
	// =========================================================
	public Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

		// 🔥 FIX CRÍTICO
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

				if (cleanType.contains("List<")) {

					String generic = parserUtil.extractGeneric(cleanType);
					example.put(name, List.of(buildExampleFromType(generic)));
					return;
				}

				if (!typeUtil.isPrimitive(type)) {

					example.put(name, buildExampleFromType(type));
					return;
				}

				Object value = SmartExampleUtil.generate(name, dataContext);

				if (value != null)
					example.put(name, value);
				else
					example.put(name, "");
			});
		});

		return example;
	}

	private String resolveJsonNameFromClass(String className, String fieldName) {

		return classIndexer.findClass(className).map(clazz -> clazz.getFields().stream()
				.flatMap(f -> f.getVariables().stream().filter(v -> v.getNameAsString().equals(fieldName)).map(v -> f))
				.findFirst().map(f -> parserUtil.resolveJsonName(f, fieldName)).orElse(fieldName)).orElse(fieldName);
	}

	private String extractApiName(String type) {

		if (type == null)
			return null;

		String normalized = type.trim();

		if (normalized.startsWith("BodyEntrada")) {
			return normalized.substring("BodyEntrada".length()).trim();
		}

		if (normalized.startsWith("BodySalida")) {
			return normalized.substring("BodySalida".length()).trim();
		}

		return normalized;
	}

	private Object parseValue(String key, String value) {

		if (value == null || value.isEmpty()) {
			return "";
		}

		String lower = key.toLowerCase();

		// =========================================================
		// ✅ CAMPOS QUE DEBEN SER STRING SIEMPRE (CRÍTICO 🔥)
		// =========================================================
		if (lower.contains("cuenta") || lower.contains("cta") || lower.contains("tarj") || lower.contains("card")
				|| lower.contains("identificador") || lower.contains("telf") || lower.contains("telefono")
				|| lower.contains("cel") || value.startsWith("0")) {
			return value;
		}

		// =========================================================
		// ✅ ENTERO
		// =========================================================
		if (value.matches("-?\\d+")) {
			try {
				return Integer.parseInt(value);
			} catch (Exception e) {
				return Long.parseLong(value);
			}
		}

		// =========================================================
		// ✅ DECIMAL
		// =========================================================
		if (value.matches("-?\\d+\\.\\d+")) {
			return Double.parseDouble(value);
		}

		// =========================================================
		// ✅ BOOLEAN
		// =========================================================
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			return Boolean.parseBoolean(value);
		}

		return value;
	}

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

				// =========================================================
				// ✅ RESPETAR STRING CRÍTICO (CUENTAS / TARJETAS)
				// =========================================================
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
					// fallback abajo
				}
			}
		}

		// ✅ fallback inteligente
		return parseValue(field, value);
	}

	private Object normalizeSmartValue(String type, String field, Object value) {

		if (value == null)
			return null;

		String lower = field.toLowerCase();

		// =========================================================
		// ✅ PROTEGER CAMPOS QUE SIEMPRE SON STRING (CRÍTICO 🔥)
		// =========================================================
		if (lower.contains("telf") || lower.contains("telefono") || lower.contains("cel") || lower.contains("tarj")
				|| lower.contains("card") || lower.contains("cta") || lower.contains("cuenta")
				|| lower.contains("identificador") || lower.contains("rif") || lower.contains("codarea")
				|| lower.contains("codpais")) {
			return value.toString(); // ✅ FORZAR STRING SIEMPRE
		}

		// =========================================================
		// ✅ AHORA SÍ: si es número o boolean → respetar
		// =========================================================
		if (value instanceof Number || value instanceof Boolean) {
			return value;
		}

		String val = value.toString();

		// =========================================================
		// ✅ USAR SCHEMA COMO PRIORIDAD
		// =========================================================
		Map<String, Object> schema = schemaMap.get(type);

		if (schema != null && schema.containsKey("properties")) {

			Map<String, Object> props = (Map<String, Object>) schema.get("properties");

			if (props.containsKey(field)) {

				Map<String, Object> prop = (Map<String, Object>) props.get(field);

				String schemaType = (String) prop.get("type");

				try {
					if ("string".equals(schemaType)) {
						return val;
					}

					if ("integer".equals(schemaType)) {
						return Integer.parseInt(val);
					}

					if ("number".equals(schemaType)) {
						return Double.parseDouble(val.replace(",", "."));
					}

					if ("boolean".equals(schemaType)) {
						return Boolean.parseBoolean(val);
					}

				} catch (Exception e) {
					// fallback abajo
				}
			}
		}

		// =========================================================
		// ✅ FALLBACK FINAL
		// =========================================================
		return parseValue(field, val);
	}

}