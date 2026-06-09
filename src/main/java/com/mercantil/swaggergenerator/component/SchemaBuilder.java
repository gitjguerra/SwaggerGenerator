package com.mercantil.swaggergenerator.component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
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

	// ✅ evita recursion infinita
	private final Set<String> processing = new HashSet<>();

	// ✅ clases ignoradas
	private static final Set<String> IGNORED_TYPES = Set.of("ConstructorRequired", "BeansZOS", "SendRequestRest");

	// =========================================================
	// ✅ SETTERS
	// =========================================================

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {

		this.schemaMap = schemaMap;
	}

	public void setEnumMap(Map<String, EnumDeclaration> enumMap) {

		this.enumMap = enumMap;
	}

	// =========================================================
	// ✅ BUILD PRINCIPAL
	// =========================================================
	public Map<String, Object> build(ClassOrInterfaceDeclaration clazz) {

		String className = clazz.getNameAsString();

		// ✅ evitar loops
		if (processing.contains(className)) {

			return Map.of("type", "object");
		}

		processing.add(className);

		Map<String, Object> properties = new LinkedHashMap<>();

		// =====================================================
		// ✅ ENUM
		// =====================================================
		if (clazz.isEnumDeclaration()) {

			List<String> values = clazz.asEnumDeclaration().getEntries().stream().map(e -> e.getNameAsString())
					.collect(Collectors.toList());

			Map<String, Object> enumSchema = Map.of("type", "string", "enum", values);

			schemaMap.put(className, enumSchema);

			processing.remove(className);

			return enumSchema;
		}

		// =====================================================
		// ✅ HERENCIA
		// =====================================================
		clazz.getExtendedTypes().forEach(ext -> {

			classIndexer.findClass(ext.getNameAsString()).ifPresent(parent -> {

				Map<String, Object> parentSchema = build(parent);

				Object props = parentSchema.get("properties");

				if (props instanceof Map) {

					properties.putAll((Map<String, Object>) props);
				}
			});
		});

		// =====================================================
		// ✅ CAMPOS
		// =====================================================
		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = parserUtil.resolveJsonName(field, var.getNameAsString());

				Map<String, Object> prop = new LinkedHashMap<>();

				var typeNode = var.getType();

				String rawType = typeNode.asString();

				boolean isArray = typeNode.isArrayType();

				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;

				String simpleType = parserUtil.resolveFinalType(cleanType);

				// ✅ evitar genericos inválidos
				if (simpleType == null || simpleType.contains(",")) {

					return;
				}

				String resolved = extractSimpleName(resolveFullType(simpleType, clazz));

				// =================================================
				// ✅ ARRAY
				// =================================================
				if (isArray) {

					String elementType = typeNode.asArrayType().getComponentType().asString();

					// ✅ byte[]
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

				// =================================================
				// ✅ LIST<T>
				// =================================================
				else if (rawType.contains("List<")) {

					String generic = parserUtil.extractGeneric(rawType);

					if (generic.contains(",")) {
						return;
					}

					String res = extractSimpleName(resolveFullType(generic, clazz));

					prop.put("type", "array");

					if (typeUtil.isPrimitive(res)) {

						prop.put("items", Map.of("type", typeUtil.mapType(res)));

					} else {

						ensureSchemaWithParsing(res, clazz);

						prop.put("items", Map.of("$ref", "#/components/schemas/" + res));
					}
				}

				// =================================================
				// ✅ MAP<K,V>
				// =================================================
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

				// =================================================
				// ✅ PRIMITIVO
				// =================================================
				else if (typeUtil.isPrimitive(resolved)) {

					prop.put("type", typeUtil.mapType(resolved));

					applyFormat(prop, resolved);
				}

				// =================================================
				// ✅ OBJETO
				// =================================================
				else {

					boolean flattened = false;

					// ✅ String especial
					if ("String".equals(resolved) || "byte".equalsIgnoreCase(resolved)) {

						prop.put("type", "string");
					}

					// ✅ WRAPPERS COBOL / MAINFRAME
					else if (shouldFlatten(resolved)) {

						flattenSchemaProperties(resolved, properties);

						flattened = true;
					}

					// ✅ ENUM
					else if (enumMap != null && enumMap.containsKey(resolved)) {

						ensureEnumSchema(resolved);

						prop.put("$ref", "#/components/schemas/" + resolved);
					}

					// ✅ OBJETO NORMAL
					else {

						// ✅ clases ignoradas
						if (IGNORED_TYPES.contains(resolved)) {

							prop.put("type", "object");

						} else {

							ensureSchemaWithParsing(resolved, clazz);

							prop.put("$ref", "#/components/schemas/" + resolved);
						}
					}

					// ✅ si el wrapper fue flatten
					// ✅ NO agregar el nombre wrapper
					if (flattened) {
						return;
					}
				}

				// =================================================
				// ✅ OPTIONAL
				// =================================================
				if (isOptional) {

					prop.put("nullable", true);
				}

				// ✅ annotations
				applyAnnotations(field, prop);

				// ✅ agregar propiedad
				properties.put(name, prop);
			});
		});

		// =====================================================
		// ✅ SCHEMA FINAL
		// =====================================================
		Map<String, Object> schema = new LinkedHashMap<>();

		schema.put("type", "object");
		schema.put("properties", properties);

		// ✅ sin propiedades
		if (properties.isEmpty()) {

			schema.put("description", "Clase sin propiedades o no detectadas");
		}

		// ✅ guardar schema
		schemaMap.put(className, schema);

		processing.remove(className);

		return schema;
	}

	// =========================================================
	// ✅ DETERMINA SI UNA CLASE ES WRAPPER
	// =========================================================
	private boolean shouldFlatten(String typeName) {

		if (typeName == null) {
			return false;
		}

		// ✅ SOLO wrappers internos
		return typeName.startsWith("Bean");
	}

	// =========================================================
	// ✅ FLATTEN RECURSIVO
	// =========================================================
	private void flattenSchemaProperties(String typeName, Map<String, Object> targetProperties) {

		Optional<ClassOrInterfaceDeclaration> childOpt = classIndexer.findClass(typeName);

		if (childOpt.isEmpty()) {
			return;
		}

		Map<String, Object> childSchema = build(childOpt.get());

		Object propsObj = childSchema.get("properties");

		if (!(propsObj instanceof Map)) {
			return;
		}

		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((k, v) -> {

			if (!(v instanceof Map)) {

				targetProperties.put(k, v);
				return;
			}

			Map<String, Object> value = (Map<String, Object>) v;

			Object ref = value.get("$ref");

			// ✅ recursion flatten
			if (ref != null) {

				String refType = ref.toString().substring(ref.toString().lastIndexOf("/") + 1);

				if (shouldFlatten(refType)) {

					flattenSchemaProperties(refType, targetProperties);

					return;
				}
			}

			targetProperties.put(k, v);
		});
	}

	// =========================================================
	// ✅ ENSURE SCHEMA
	// =========================================================
	private void ensureSchemaWithParsing(String type, ClassOrInterfaceDeclaration context) {

		if (type == null || type.isBlank()) {
			return;
		}

		if (schemaMap.containsKey(type)) {
			return;
		}

		if (processing.contains(type)) {
			return;
		}

		if ("String".equals(type)) {
			return;
		}

		if ("byte".equalsIgnoreCase(type)) {
			return;
		}

		if (type.contains(",")) {
			return;
		}

		if (!isValidModel(type)) {
			return;
		}

		Optional<ClassOrInterfaceDeclaration> clazzOpt = classIndexer.findClass(type);

		if (clazzOpt.isPresent()) {

			build(clazzOpt.get());
		}
	}

	// =========================================================
	// ✅ RESOLVE FULL TYPE
	// =========================================================
	private String resolveFullType(String simpleType, ClassOrInterfaceDeclaration clazz) {

		if (simpleType == null) {
			return null;
		}

		if (simpleType.contains(".")) {
			return simpleType;
		}

		return clazz.findCompilationUnit()
				.map(cu -> cu.getImports().stream().filter(i -> !i.isAsterisk())
						.filter(i -> i.getName().getIdentifier().equals(simpleType)).map(i -> i.getNameAsString())
						.findFirst().orElse(simpleType))
				.orElse(simpleType);
	}

	// =========================================================
	// ✅ SIMPLE NAME
	// =========================================================
	private String extractSimpleName(String fullType) {

		if (fullType == null) {
			return null;
		}

		return fullType.contains(".") ? fullType.substring(fullType.lastIndexOf(".") + 1) : fullType;
	}

	// =========================================================
	// ✅ ENUM SCHEMA
	// =========================================================
	private void ensureEnumSchema(String enumName) {

		if (schemaMap.containsKey(enumName)) {
			return;
		}

		if (enumMap == null || !enumMap.containsKey(enumName)) {
			return;
		}

		EnumDeclaration enumDecl = enumMap.get(enumName);

		List<String> values = enumDecl.getEntries().stream().map(e -> e.getNameAsString()).collect(Collectors.toList());

		Map<String, Object> schema = new LinkedHashMap<>();

		schema.put("type", "string");
		schema.put("enum", values);

		schemaMap.put(enumName, schema);
	}

	// =========================================================
	// ✅ VALID MODEL
	// =========================================================
	private boolean isValidModel(String name) {

		return !(name.endsWith("Impl") || name.contains("Logger") || name.contains("Client") || name.contains("Config")
				|| name.contains("Filter"));
	}

	// =========================================================
	// ✅ ANNOTATIONS
	// =========================================================
	private void applyAnnotations(FieldDeclaration field, Map<String, Object> prop) {

		for (AnnotationExpr ann : field.getAnnotations()) {

			// ✅ NOT NULL
			if (ann.getNameAsString().equals("NotNull")) {

				prop.put("nullable", false);
			}

			// ✅ SIZE
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

	// =========================================================
	// ✅ FORMAT
	// =========================================================
	private void applyFormat(Map<String, Object> prop, String type) {

		if ("UUID".equals(type)) {
			prop.put("format", "uuid");
		}

		if ("LocalDate".equals(type)) {
			prop.put("format", "date");
		}

		if ("LocalDateTime".equals(type) || "Date".equals(type)) {

			prop.put("format", "date-time");
		}

		if ("BigDecimal".equals(type)) {
			prop.put("format", "double");
		}
	}
}