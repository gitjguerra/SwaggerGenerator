package com.mercantil.swaggergenerator.component;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

@Component
public class SchemaBuilder {

	@Autowired
	private TypeUtil typeUtil;

	@Autowired
	private ParserUtil parserUtil;

	@Autowired
	private ClassIndexer classIndexer;

	private Map<String, Map<String, Object>> schemaMap;
	private Map<String, EnumDeclaration> enumMap;

	// ✅ evita loops recursivos
	private final Set<String> processing = new HashSet<>();

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
		this.schemaMap = schemaMap;
	}

	public void setEnumMap(Map<String, EnumDeclaration> enumMap) {
		this.enumMap = enumMap;
	}

	private static final Set<String> IGNORED_TYPES = Set.of("ConstructorRequired", "BeansZOS", "SendRequestRest");

	public Map<String, Object> build(ClassOrInterfaceDeclaration clazz) {

		String className = clazz.getNameAsString();

		// ✅ evita reprocesar
		// if (schemaMap.containsKey(className)) {
		// return schemaMap.get(className);
		// }

		// ✅ evita loops infinitos
		if (processing.contains(className)) {
			return Map.of("type", "object");
		}

		processing.add(className);

		Map<String, Object> properties = new LinkedHashMap<>();

		// ✅ ENUM
		if (clazz.isEnumDeclaration()) {
			List<String> values = clazz.asEnumDeclaration().getEntries().stream().map(e -> e.getNameAsString())
					.collect(Collectors.toList());

			Map<String, Object> enumSchema = Map.of("type", "string", "enum", values);

			schemaMap.put(className, enumSchema);
			processing.remove(className);

			return enumSchema;
		}

		// ✅ HERENCIA
		clazz.getExtendedTypes().forEach(ext -> {
			classIndexer.findClass(ext.getNameAsString()).ifPresent(parent -> {

				Map<String, Object> parentSchema = build(parent);

				Object props = parentSchema.get("properties");

				if (props instanceof Map) {
					properties.putAll((Map<String, Object>) props);
				}
			});
		});

		// ✅ CAMPOS
		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = parserUtil.resolveJsonName(field, var.getNameAsString());

				//System.out.println("SCHEMA FIELD: " + var.getNameAsString() + " -> " + name);

				Map<String, Object> prop = new LinkedHashMap<>();

				var typeNode = field.getElementType();
				String rawType = typeNode.asString();

				boolean isArray = typeNode.isArrayType();
				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;

				String simpleType = parserUtil.resolveFinalType(cleanType);

				// ✅ evitar basura: Map<K,V> mal parseado o genéricos inválidos
				if (simpleType == null || simpleType.contains(","))
					return;

				String resolved = extractSimpleName(resolveFullType(simpleType, clazz));

				// =========================================================
				// ✅ ARRAY
				// =========================================================
				if (isArray) {

					String elementType = typeNode.asArrayType().getComponentType().asString();

					if ("byte".equalsIgnoreCase(elementType)) {

						prop.put("type", "string");
						prop.put("format", "byte");

					} else {

						prop.put("type", "array");

						String res = extractSimpleName(resolveFullType(elementType, clazz));

						if (typeUtil.isPrimitive(res)) {

							prop.put("items", Map.of("type", typeUtil.mapType(res)));

						} else {

							ensureSchemaWithParsing(res, clazz);

							prop.put("items", Map.of("$ref", "#/components/schemas/" + res));
						}
					}
				}

				// =========================================================
				// ✅ LIST<T>
				// =========================================================
				else if (rawType.contains("List<")) {

					String generic = parserUtil.extractGeneric(rawType);

					if (generic.contains(","))
						return;

					String res = extractSimpleName(resolveFullType(generic, clazz));

					prop.put("type", "array");

					if (typeUtil.isPrimitive(res)) {

						prop.put("items", Map.of("type", typeUtil.mapType(res)));

					} else {

						ensureSchemaWithParsing(res, clazz);

						prop.put("items", Map.of("$ref", "#/components/schemas/" + res));
					}
				}

				// =========================================================
				// ✅ MAP<K,V>
				// =========================================================
				else if (rawType.contains("Map<")) {

					prop.put("type", "object");

					String valueType = parserUtil.extractMapValue(rawType);

					if (valueType != null && !valueType.contains(",")) {

						String res = extractSimpleName(resolveFullType(valueType, clazz));

						if (typeUtil.isPrimitive(res)) {

							prop.put("additionalProperties", Map.of("type", typeUtil.mapType(res)));

						} else {

							ensureSchemaWithParsing(res, clazz);

							prop.put("additionalProperties", Map.of("$ref", "#/components/schemas/" + res));
						}
					}
				}

				// =========================================================
				// ✅ PRIMITIVO
				// =========================================================
				else if (typeUtil.isPrimitive(resolved)) {

					prop.put("type", typeUtil.mapType(resolved));
					applyFormat(prop, resolved);
				}

				// =========================================================
				// ✅ OBJETO
				// =========================================================

				else {

					if ("byte".equalsIgnoreCase(resolved) || "String".equals(resolved)) {

						prop.put("type", "string");

					} else if (enumMap != null && enumMap.containsKey(resolved)) {

						ensureEnumSchema(resolved);

						prop.put("$ref", "#/components/schemas/" + resolved);

					} else {

						// 🔥 IGNORAR CLASES EXTERNAS (CRÍTICO)
						if (IGNORED_TYPES.contains(resolved)) {

							// fallback limpio (NO $ref)
							prop.put("type", "object");

						} else {

							ensureSchemaWithParsing(resolved, clazz);

							prop.put("$ref", "#/components/schemas/" + resolved);
						}
					}
				}

				if (isOptional) {
					prop.put("nullable", true);
				}

				applyAnnotations(field, prop);

				properties.put(name, prop);
			});
		});

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);

		if (properties.isEmpty()) {
			schema.put("description", "Clase sin propiedades o no detectadas");
		}

		schemaMap.put(className, schema);
		processing.remove(className);

		return schema;
	}

	// =========================================================
	// ✅ ENSURE SCHEMA
	// =========================================================
	private void ensureSchemaWithParsing(String type, ClassOrInterfaceDeclaration context) {

		if (type == null || type.isBlank())
			return;
		if (schemaMap.containsKey(type))
			return;
		if (processing.contains(type))
			return;

		// ✅ evitar tipos inválidos
		if ("String".equals(type))
			return;
		if ("byte".equalsIgnoreCase(type))
			return;
		if (type.contains(","))
			return;
		if (!isValidModel(type))
			return;

		Optional<ClassOrInterfaceDeclaration> clazzOpt = classIndexer.findClass(type);

		if (clazzOpt.isEmpty()) {
			clazzOpt = findClassFromImports(type, context);
		}

		clazzOpt.ifPresent(this::build);
	}

	private Optional<ClassOrInterfaceDeclaration> findClassFromImports(String type,
			ClassOrInterfaceDeclaration contextClass) {

		return contextClass.findCompilationUnit().flatMap(cu -> cu.getImports().stream().filter(i -> !i.isAsterisk())
				.filter(i -> i.getName().getIdentifier().equals(type)).findFirst()).flatMap(importDecl -> {

					String fullName = importDecl.getNameAsString();

					return classIndexer.getAllClasses().values().stream()
							.filter(c -> c.findCompilationUnit().flatMap(u -> u.getPackageDeclaration())
									.map(p -> p.getNameAsString() + "." + c.getNameAsString()).orElse("")
									.equals(fullName))
							.findFirst();
				});
	}

	private String resolveFullType(String simpleType, ClassOrInterfaceDeclaration clazz) {
		if (simpleType == null)
			return null;
		if (simpleType.contains("."))
			return simpleType;

		return clazz.findCompilationUnit()
				.map(cu -> cu.getImports().stream().filter(i -> !i.isAsterisk())
						.filter(i -> i.getName().getIdentifier().equals(simpleType)).map(i -> i.getNameAsString())
						.findFirst().orElse(simpleType))
				.orElse(simpleType);
	}

	private String extractSimpleName(String fullType) {
		if (fullType == null)
			return null;
		return fullType.contains(".") ? fullType.substring(fullType.lastIndexOf(".") + 1) : fullType;
	}

	private void ensureEnumSchema(String enumName) {

		if (schemaMap.containsKey(enumName))
			return;
		if (enumMap == null || !enumMap.containsKey(enumName))
			return;

		EnumDeclaration enumDecl = enumMap.get(enumName);

		List<String> values = enumDecl.getEntries().stream().map(e -> e.getNameAsString()).collect(Collectors.toList());

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "string");
		schema.put("enum", values);

		schemaMap.put(enumName, schema);
	}

	private void applyAnnotations(FieldDeclaration field, Map<String, Object> prop) {

		for (AnnotationExpr ann : field.getAnnotations()) {

			if (ann.getNameAsString().equals("NotNull")) {
				prop.put("nullable", false);
			}

			if (ann.getNameAsString().equals("Size")) {
				ann.ifNormalAnnotationExpr(a -> {
					a.getPairs().forEach(p -> {
						if (p.getNameAsString().equals("min")) {
							prop.put("minLength", Integer.parseInt(p.getValue().toString()));
						}
						if (p.getNameAsString().equals("max")) {
							prop.put("maxLength", Integer.parseInt(p.getValue().toString()));
						}
					});
				});
			}
		}
	}

	private boolean isValidModel(String name) {
		return !(name.endsWith("Impl") || name.contains("Logger") || name.contains("Client") || name.contains("Config")
				|| name.contains("Filter"));
	}

	private void applyFormat(Map<String, Object> prop, String type) {

		if ("UUID".equals(type))
			prop.put("format", "uuid");
		if ("LocalDate".equals(type))
			prop.put("format", "date");
		if ("LocalDateTime".equals(type) || "Date".equals(type))
			prop.put("format", "date-time");
		if ("BigDecimal".equals(type))
			prop.put("format", "double");
	}
}