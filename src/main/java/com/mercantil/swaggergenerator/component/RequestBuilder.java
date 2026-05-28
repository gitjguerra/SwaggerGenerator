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
	private ParserUtil parserUtil;

	@Autowired
	private HeaderExampleProvider headerProvider;

	public Map<String, Object> build(MethodDeclaration method, Map<String, Map<String, Object>> schemaMap,
			Map<String, Object> exampleMap, List<String> ignoredTypes) {

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

			// ✅ detectar request wrapper
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

		// ✅ JSON final
		Map<String, Object> requestJson = new LinkedHashMap<>();
		requestJson.put("schema", requestSchema);

		// ✅ ejemplo
		Map<String, Object> requestExample = new LinkedHashMap<>();

		requestExample.put("headerEntrada", headerProvider.buildHeaderEntrada());

		if (requestBodyType != null) {

			Object bodyExample = exampleMap.get(requestBodyType);

			if (!(bodyExample instanceof Map)) {
				bodyExample = new LinkedHashMap<>();
			}

			requestExample.put("bodyEntrada" + bodyName, bodyExample);
		}

		Map<String, Object> exampleWrapper = new LinkedHashMap<>();

		Map<String, Object> defaultExample = new LinkedHashMap<>();
		defaultExample.put("summary", "Ejemplo generado");
		defaultExample.put("value", requestExample);

		exampleWrapper.put("default", defaultExample);

		requestJson.put("examples", exampleWrapper);

		return Map.of("required", true, "content", Map.of("application/json", requestJson));
	}

}