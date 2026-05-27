package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

@Component
public class SchemaBuilder {

	@Autowired
	private TypeUtil typeUtil;

	@Autowired
	private ParserUtil parserUtil;

	private Map<String, Map<String, Object>> schemaMap;

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
		this.schemaMap = schemaMap;
	}

	public Map<String, Object> build(ClassOrInterfaceDeclaration clazz) {

		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		// ✅ ENUM
		if (clazz.isEnumDeclaration()) {

			List<String> values = clazz.asEnumDeclaration().getEntries().stream().map(e -> e.getNameAsString())
					.collect(Collectors.toList());

			return Map.of("type", "string", "enum", values);
		}

		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = parserUtil.resolveJsonName(field, var.getNameAsString());

				String rawType = field.getElementType().asString();
				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;
				String type = parserUtil.resolveFinalType(cleanType);

				Map<String, Object> prop = new LinkedHashMap<>();

				// ✅ LIST
				if (cleanType.startsWith("List<") || rawType.contains("List<")) {
					handleList(prop, rawType);
				}

				// ✅ PRIMITIVE
				else if (typeUtil.isPrimitive(type)) {
					prop.put("type", typeUtil.mapType(type));
					applyFormat(prop, type);
				}

				// ✅ OBJECT
				else {
					if (!schemaMap.containsKey(type)) {
						Map<String, Object> fallback = new LinkedHashMap<>();
						fallback.put("type", "object");
						fallback.put("additionalProperties", true);
						schemaMap.put(type, fallback);
					}

					prop.put("$ref", "#/components/schemas/" + type);
				}

				if (isOptional) {
					prop.put("nullable", true);
				}

				if (field.getAnnotationByName("NotNull").isPresent()) {
					required.add(name);
				}

				properties.put(name, prop);
			});
		});

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");

		if (!properties.isEmpty()) {
			schema.put("properties", properties);
		}

		if (!required.isEmpty()) {
			schema.put("required", required);
		}

		return schema;
	}

	private void handleList(Map<String, Object> prop, String rawType) {

		String generic = parserUtil.resolveFinalType(parserUtil.extractGeneric(rawType));

		prop.put("type", "array");

		if (typeUtil.isPrimitive(generic)) {
			prop.put("items", Map.of("type", typeUtil.mapType(generic)));
		} else {

			if (!schemaMap.containsKey(generic)) {
				Map<String, Object> fallback = new LinkedHashMap<>();
				fallback.put("type", "object");
				fallback.put("additionalProperties", true);
				schemaMap.put(generic, fallback);
			}

			prop.put("items", Map.of("$ref", "#/components/schemas/" + generic));
		}
	}

	private void applyFormat(Map<String, Object> prop, String type) {

		if (type.equals("UUID"))
			prop.put("format", "uuid");
		if (type.equals("LocalDate"))
			prop.put("format", "date");
		if (type.equals("LocalDateTime") || type.equals("Date"))
			prop.put("format", "date-time");
		if (type.equals("BigDecimal"))
			prop.put("format", "double");
	}
}