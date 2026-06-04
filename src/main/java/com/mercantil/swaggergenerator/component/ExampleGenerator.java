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
			return new LinkedHashMap<>();
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
								fallback.put(k, value != null ? value : "string");

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

				example.put(jsonKey, buildExampleFromType(refType));
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

						example.put(jsonKey, List.of("string"));
					}
				}
				return;
			}

			Object value = SmartExampleUtil.generate(key, dataContext);

			example.put(jsonKey, value != null ? value : "string");
		});

		// =====================================================
		// 🔥 FIX FINAL: EVITA OBJETO VACÍO
		// =====================================================
		if (example.isEmpty()) {

			Map<String, Object> fallback = new LinkedHashMap<>();

			props.keySet().forEach(k -> fallback.put(k, "string"));

			return fallback;
		}

		// =====================================================
		// ✅ FLATTEN WRAPPER (RESTAURADO 🔥)
		// =====================================================
		if (example.size() == 1) {

			Object onlyValue = example.values().iterator().next();

			if (onlyValue instanceof Map) {
				return onlyValue;
			}
		}

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
					example.put(name, "string");
			});
		});

		return example;
	}

	private String resolveJsonNameFromClass(String className, String fieldName) {

		return classIndexer.findClass(className).map(clazz -> clazz.getFields().stream()
				.flatMap(f -> f.getVariables().stream().filter(v -> v.getNameAsString().equals(fieldName)).map(v -> f))
				.findFirst().map(f -> parserUtil.resolveJsonName(f, fieldName)).orElse(fieldName)).orElse(fieldName);
	}

}