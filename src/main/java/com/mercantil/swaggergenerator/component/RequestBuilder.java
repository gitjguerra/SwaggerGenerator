package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.mercantil.swaggergenerator.util.ParserUtil;

@Component
public class RequestBuilder {

	@Autowired
	private ExampleGenerator exampleGenerator;

	@Autowired
	private ParserUtil parserUtil;

	public Map<String, Object> build(MethodDeclaration method, Map<String, Map<String, Object>> schemaMap,
			List<String> ignoredTypes) {

		Map<String, Object> requestSchema = new LinkedHashMap<>();
		Map<String, Object> requestProps = new LinkedHashMap<>();

		// ✅ safe ref
		java.util.function.Function<String, Map<String, Object>> safeRef = type -> {
			if (type == null || ignoredTypes.contains(type)) {
				return Map.of("type", "object");
			}
			return Map.of("$ref", "#/components/schemas/" + type);
		};

		// ✅ siempre header
		requestProps.put("headerEntrada", safeRef.apply("HeaderEntrada"));

		String requestBodyType = null;
		String bodyName = null;

		for (Parameter p : method.getParameters()) {

			String rawType = p.getType().asString();

			// ✅ Optional<T>
			if (rawType.startsWith("Optional<")) {
				rawType = parserUtil.extractGeneric(rawType);
			}

			// ✅ detectar Request wrapper
			if (rawType.startsWith("Request")) {

				Map<String, Object> reqSchema = schemaMap.get(rawType);

				if (reqSchema != null && reqSchema.get("properties") instanceof Map) {

					Map<String, Object> props = (Map<String, Object>) reqSchema.get("properties");

					for (Map.Entry<String, Object> entry : props.entrySet()) {

						if (entry.getKey().startsWith("bodyEntrada")) {

							Map<String, Object> refObj = (Map<String, Object>) entry.getValue();

							if (refObj.containsKey("$ref")) {

								String ref = refObj.get("$ref").toString();
								requestBodyType = ref.substring(ref.lastIndexOf("/") + 1);

								if (ignoredTypes.contains(requestBodyType)) {
									requestBodyType = null;
									break;
								}

								bodyName = entry.getKey().replace("bodyEntrada", "");
								break;
							}
						}
					}
				}
			}
		}

		// ✅ agregar body si existe
		if (requestBodyType != null) {
			requestProps.put("bodyEntrada" + bodyName, safeRef.apply(requestBodyType));
		}

		requestSchema.put("type", "object");
		requestSchema.put("properties", requestProps);

		// GENERA EXAMPLE
		Object requestExample = buildRequestExample(requestProps);

		// ✅ JSON final SIN examples
		Map<String, Object> requestJson = new LinkedHashMap<>();
		requestJson.put("schema", requestSchema);
		requestJson.put("example", requestExample);

		return Map.of("required", true, "content", Map.of("application/json", requestJson));
	}

	private Object buildRequestExample(Map<String, Object> requestProps) {

		Map<String, Object> example = new LinkedHashMap<>();

		for (Map.Entry<String, Object> entry : requestProps.entrySet()) {

			String key = entry.getKey();
			Object value = entry.getValue();

			if (!(value instanceof Map))
				continue;

			Map<?, ?> prop = (Map<?, ?>) value;

			// ✅ $ref → construir objeto completo
			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String type = ref.substring(ref.lastIndexOf("/") + 1);

				example.put(key, exampleGenerator.buildExampleFromType(type));
			} else {
				// ✅ fallback simple
				example.put(key, exampleGenerator.generateSmartExample(key));
			}
		}

		return example;
	}

}