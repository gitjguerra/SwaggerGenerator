package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.mercantil.swaggergenerator.util.AbbreviationUtil;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.SmartExampleUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

@Component
public class ExampleGenerator {

	private final TypeUtil typeUtil;
	private final ParserUtil parserUtil;
	private final HeaderExampleProvider headerProvider;
	private final ClassIndexer classIndexer;
	private final RuleEngine ruleEngine;

	public ExampleGenerator(TypeUtil typeUtil, ParserUtil parserUtil, HeaderExampleProvider headerProvider,
			ClassIndexer classIndexer, RuleEngine ruleEngine) {
		this.typeUtil = typeUtil;
		this.parserUtil = parserUtil;
		this.headerProvider = headerProvider;
		this.classIndexer = classIndexer;
		this.ruleEngine = ruleEngine;
	}

	// =========================================================
	// ✅ CONTEXTO CONSISTENTE
	// =========================================================
	private Map<String, Object> dataContext = new LinkedHashMap<>();

	// =========================================================
	// ✅ SCHEMAS
	// =========================================================
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	// =========================================================
	// ✅ CONTEXTO PATH
	// =========================================================
	private String currentApiPath;

	public void setCurrentApiPath(String apiPath) {
		this.currentApiPath = apiPath;
	}

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {

		this.schemaMap = schemaMap;

	}

	// =========================================================
	// ✅ BUILD PRINCIPAL
	// =========================================================
	public Object buildExampleFromType(String type) {

		String apiName = currentApiPath;

		if (apiName == null || apiName.isBlank()) {
			// 👉 evitar que se genere example sin endpoint
			return new LinkedHashMap<>();
		}

		return buildExampleFromType(type, "", apiName);

	}

	// =========================================================
	// ✅ BUILD RECURSIVO
	// =========================================================
	@SuppressWarnings("unchecked")
	private Object buildExampleFromType(String type, String currentPath, String apiName) {

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
		// ✅ SCHEMA
		// =====================================================
		Map<String, Object> schema = schemaMap.get(type);

		if (schema == null) {

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
		// ✅ ROOT REF
		// =====================================================
		if (schema.containsKey("$ref")) {

			String ref = schema.get("$ref").toString();

			String refType = ref.substring(ref.lastIndexOf("/") + 1);

			return buildExampleFromType(refType, currentPath, apiName);
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

						Object nested = buildExampleFromType(refType, currentPath, apiName);

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

			// ✅ CLAVE: normalizar el key del schema directamente
			String jsonKey = key != null ? key : "";

			// =================================================
			// ✅ FULL PATH
			// =================================================
			String fullPath = currentPath == null || currentPath.isBlank() ? jsonKey : currentPath + "." + jsonKey;

			// =================================================
			// ✅ OBJETO NESTED
			// =================================================
			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				Object nested = buildExampleFromType(refType, fullPath, apiName);

				// ✅ NUEVO: verificar si el refType realmente existe como propiedad en schema
				Map<String, Object> currentSchema = schemaMap.get(type);

				boolean isActuallyPresent = false;

				if (currentSchema != null && currentSchema.containsKey("properties")) {

					Map<String, Object> currentProps = (Map<String, Object>) currentSchema.get("properties");

					// ✅ si NO está como propiedad → fue flatten
					isActuallyPresent = currentProps.containsKey(key);
				}

				// ✅ SI FUE FLATTEN → expandir
				if (!isActuallyPresent && nested instanceof Map) {

					((Map<?, ?>) nested).forEach((k, v) -> {
						example.put(k.toString(), v);
					});

					return;
				}

				// ✅ comportamiento normal
				if (nested == null) {
					nested = new LinkedHashMap<>();
				}

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

				    // ARRAY OBJETO
				    if (itemMap.containsKey("$ref")) {

				        String ref = itemMap.get("$ref").toString();

				        String refType = ref.substring(ref.lastIndexOf("/") + 1);

				        Object nested = buildExampleFromType(refType, fullPath, apiName);

				        if (nested == null) {

				            nested = new LinkedHashMap<>();
				        }

				        List<Object> arrayItems = new ArrayList<>();
				        arrayItems.add(nested);

				        example.put(jsonKey, arrayItems);
				    }

				    // ARRAY PRIMITIVO
				    else {

				        List<Object> arrayItems = new ArrayList<>();
				        arrayItems.add("");

				        example.put(jsonKey, arrayItems);
				    }
				}

				return;
			}

			// =================================================
			// ✅ RULES.XML
			// =================================================
			String ruleValue = ruleEngine.getRequestValue(apiName, fullPath);

			// ✅ 2. intentar sin bodyEntrada...
			if (ruleValue == null && fullPath.contains(".")) {

				String reducedPath = fullPath.substring(fullPath.indexOf(".") + 1);

				ruleValue = ruleEngine.getRequestValue(apiName, reducedPath);
			}

			// ✅ 3. fallback simple
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

			// ✅ eliminar cualquier key previa del schema
			example.remove(key);
			example.remove(jsonKey);

			// ✅ insertar el correcto
			example.put(jsonKey, value != null ? value : "");

		});

		return example;
	}

	// =========================================================
	// ✅ BUILD FROM CLASS
	// =========================================================
	@SuppressWarnings("unchecked")
	public Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

		// exampleMap = new LinkedHashMap<>();

		dataContext = new LinkedHashMap<>();

		Map<String, Object> example = new LinkedHashMap<>();

		String apiName = currentApiPath != null ? currentApiPath : extractApiName(clazz.getNameAsString());

		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = resolveJsonNameFromClass(clazz.getNameAsString(), var.getNameAsString());

				String rawType = field.getElementType().asString();

				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType =

						isOptional

								? parserUtil.extractGeneric(rawType)

								: rawType;

				String type = parserUtil.resolveFinalType(cleanType);

				// ✅ fallback seguridad
				if (type == null || type.isBlank()) {

					type = cleanType;
				}

				// ✅ LIST<T>
				if (cleanType.contains("List<")) {

					String generic = parserUtil.extractGeneric(cleanType);

					Object nested = buildExampleFromType(generic, name, apiName);

					if (nested == null) {

						nested = new LinkedHashMap<>();
					}

					List<Object> items = new ArrayList<>();
					items.add(nested);

					example.put(name, items);

					return;
				}

				// =====================================================
				// ✅ OBJETO COMPLEJO
				// =====================================================
				boolean primitive = typeUtil.isPrimitive(type);

				if (schemaMap.containsKey(type)) {

					primitive = false;
				}

				if (type != null && type.startsWith("Bean")) {

					primitive = false;
				}

				if (!primitive) {

					Object nested = buildExampleFromType(type, name, apiName);

					if (nested == null) {

						nested = new LinkedHashMap<>();
					}

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

				.map(clazz -> clazz.getFields().stream().flatMap(
						f -> f.getVariables().stream().filter(v -> v.getNameAsString().equals(fieldName)).map(v -> f))
						.findFirst().map(f -> {

							// ✅ 1. PRIORIDAD: JsonProperty
							String ctorJsonName = this.resolveJsonNameFromConstructor(clazz, fieldName);

							if (ctorJsonName != null && !ctorJsonName.isBlank() && !ctorJsonName.equals(fieldName)) {

								return ctorJsonName;
							}

							// ✅ 2. resolver base
							String base = parserUtil.resolveJsonName(f, fieldName);

							if (!base.equals(fieldName)) {
								return base;
							}

							return normalizeJsonKey(base);

						}).orElse(normalizeJsonKey(fieldName)))

				.orElse(normalizeJsonKey(fieldName));
	}

	// =========================================================
	// ✅ API PATH NORMALIZATION
	// =========================================================

	private String extractApiName(String type) {

		if (type == null || type.isBlank()) {
			return null;
		}

		String normalized = type.trim();

		if (normalized.startsWith("BodyEntrada")) {
			normalized = normalized.substring("BodyEntrada".length());
		} else if (normalized.startsWith("BodySalida")) {
			normalized = normalized.substring("BodySalida".length());
		}

		// TransferirOtrosBancosNacionales
		String kebab = normalized.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();

		// ✅ dividir en 2 niveles
		String[] parts = kebab.split("-", 2);

		if (parts.length == 2) {
			return "/" + parts[0] + "/" + parts[1];
		}

		return "/" + kebab;
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

				// ✅ si tampoco cabe en Long (ej. un ID/numero muy largo), se conserva
				// como string en vez de dejar que la excepción se propague sin capturar
				try {

					return Long.parseLong(value);

				} catch (Exception ex) {

					return value;
				}
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

					return parseValue(field, val);
				}
			}
		}

		return parseValue(field, val);
	}

	// =========================================================
	// ✅ RESOLVE JSON NAME FROM CTOR (FIX FINAL)
	// =========================================================
	public String resolveJsonNameFromConstructor(ClassOrInterfaceDeclaration clazz, String fieldName) {

		if (clazz == null || fieldName == null) {
			return fieldName;
		}

		String f = fieldName.toLowerCase();

		for (ConstructorDeclaration ctor : clazz.getConstructors()) {

			for (Parameter param : ctor.getParameters()) {

				String paramName = param.getNameAsString();
				String p = paramName.toLowerCase();

				// =====================================================
				// ✅ MATCH FLEXIBLE (CRÍTICO)
				// =====================================================
				boolean match = f.equals(p);
				
				if (!match) {
					continue;
				}

				// =====================================================
				// ✅ EXTRAER JsonProperty
				// =====================================================

				for (AnnotationExpr ann : param.getAnnotations()) {

					if (!"JsonProperty".equals(ann.getNameAsString())) {
						continue;
					}

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

				}
			}
		}

		return fieldName;
	}

	private String normalizeJsonKey(String name) {

		return AbbreviationUtil.normalizeJsonKey(name);
	}

}