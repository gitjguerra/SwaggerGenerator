package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	@Autowired
	private ExampleGenerator exampleGenerator;

	@SuppressWarnings("unused")
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

				// =========================================================
				// ✅ GENERAR EJEMPLO UNA SOLA VEZ (🔥 CLAVE)
				// =========================================================
				Object exampleValue = exampleGenerator.generateSmartExample(name);

				// =========================================================
				// ✅ LISTAS
				// =========================================================
				if (cleanType.startsWith("List<") || rawType.contains("List<")) {

					String generic = parserUtil.resolveFinalType(parserUtil.extractGeneric(cleanType));

					prop.put("type", "array");

					Map<String, Object> items = new LinkedHashMap<>();

					if (typeUtil.isPrimitive(generic)) {

						items.put("type", typeUtil.mapType(generic));

						if (exampleValue != null) {
							items.put("example", exampleValue);
							prop.put("example", List.of(exampleValue)); // ✅ ejemplo de array
						}

					} else {

						items.put("$ref", "#/components/schemas/" + generic);

						if (exampleValue != null) {
							prop.put("example", List.of(exampleValue)); // ✅ array de objetos
						}
					}

					prop.put("items", items);
				}

				// =========================================================
				// ✅ PRIMITIVOS
				// =========================================================
				else if (typeUtil.isPrimitive(type)) {

					String inferredType = exampleGenerator.inferType(name);

					Set<String> allowedTypes = Set.of("string", "integer", "number", "boolean");

					// ✅ tipo base
					if (inferredType != null && allowedTypes.contains(inferredType)) {
						prop.put("type", inferredType);
					} else {
						prop.put("type", typeUtil.mapType(type));
					}

					// ✅ Ajustar tipo según ejemplo (UNA SOLA VEZ)
					if (exampleValue instanceof String) {
						prop.put("type", "string");
					} else if (exampleValue instanceof Integer || exampleValue instanceof Long) {
						prop.put("type", "integer");
					} else if (exampleValue instanceof Double || exampleValue instanceof Float) {
						prop.put("type", "number");
					}

					// ✅ aplicar format después
					applyFormat(prop, type);

					// ✅ example
					if (exampleValue != null) {
						prop.put("example", exampleValue);
					}
				}

				// =========================================================
				// ✅ OBJETOS
				// =========================================================
				else {

					prop.put("$ref", "#/components/schemas/" + type);

					if (exampleValue != null) {
						prop.put("example", exampleValue);
					}
				}

				// =========================================================
				// ✅ OPTIONAL
				// =========================================================
				if (isOptional) {
					prop.put("nullable", true);
				}

				// =========================================================
				// ✅ REQUIRED
				// =========================================================
				if (field.getAnnotationByName("NotNull").isPresent()) {
					required.add(name);
				}

				// =========================================================
				// ✅ DEBUG LIMPIO
				// =========================================================
				System.out.println("\n==== DEBUG SchemaBuilder ====");
				System.out.println("FIELD: " + name);
				System.out.println("TYPE BASE: " + type);
				System.out.println("EXAMPLE: " + exampleValue);

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