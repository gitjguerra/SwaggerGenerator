package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
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
import com.mercantil.swaggergenerator.util.AbbreviationUtil;
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
	@SuppressWarnings("unchecked")
	public Map<String, Object> build(ClassOrInterfaceDeclaration clazz) {

		String className = clazz.getNameAsString();

		// =====================================================
		// ✅ recursion protection
		// =====================================================
		if (processing.contains(className)) {

			return schemaMap.getOrDefault(

					className,

					Map.of("type", "object",

							"properties", new LinkedHashMap<>()));
		}

		processing.add(className);

		try {

			Map<String, Object> properties = new LinkedHashMap<>();

			List<String> required = new ArrayList<>();

			// =================================================
			// ✅ EARLY REGISTRATION
			// =================================================
			Map<String, Object> schema = new LinkedHashMap<>();

			schema.put("type", "object");

			schema.put("properties", properties);

			schemaMap.put(className, schema);

			// =================================================
			// ✅ ENUMS
			// =================================================
			if (clazz.isEnumDeclaration()) {

				List<String> values = clazz.asEnumDeclaration().getEntries().stream()

						.map(e -> e.getNameAsString())

						.collect(Collectors.toList());

				Map<String, Object> enumSchema = Map.of(

						"type", "string",

						"enum", values);

				schemaMap.put(className, enumSchema);

				return enumSchema;
			}

			// =================================================
			// ✅ HERENCIA
			// =================================================
			clazz.getExtendedTypes().forEach(ext -> {

				classIndexer.findClass(ext.getNameAsString())

						.ifPresent(parent -> {

							Map<String, Object> parentSchema = build(parent);

							Object props = parentSchema.get("properties");

							if (props instanceof Map) {

								properties.putAll((Map<String, Object>) props);
							}
						});
			});

			// =================================================
			// ✅ CAMPOS
			// =================================================
			clazz.getFields()

					.stream()

					.filter(field -> !field.isStatic())

					.filter(field -> !field.isTransient())

					.forEach(field -> {

						field.getVariables()

								.stream()

								.filter(var ->

								!"serialVersionUID".equalsIgnoreCase(var.getNameAsString()))

								.forEach(var -> {

									String name = parserUtil.resolveJsonName(field, var.getNameAsString());
									if (!isWrapperName(name)) {
										name = normalizeJsonKey(name);
									}

									// ✅ evitar propiedades inválidas
									if (name == null || name.isBlank()) {

										return;
									}

									Map<String, Object> prop = new LinkedHashMap<>();

									var typeNode = var.getType();

									String rawType = typeNode.asString();

									boolean isArray = typeNode.isArrayType();

									boolean isOptional = rawType.startsWith("Optional<");

									String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;

									String simpleType = parserUtil.resolveFinalType(cleanType);

									// ✅ validar type
									if (simpleType == null || simpleType.isBlank() || simpleType.contains(",")) {

										return;
									}

									String resolved = extractSimpleName(

											resolveFullType(simpleType, clazz));

									// =================================================
									// ✅ ARRAY
									// =================================================
									if (isArray) {

										String elementType = typeNode.asArrayType().getComponentType().asString();

										// ✅ byte[]
										if ("byte".equalsIgnoreCase(elementType)) {

											prop.put("type", "string");

											prop.put("format", "byte");
										}

										// ✅ arrays normales
										else {

											prop.put("type", "array");

											String res = extractSimpleName(

													resolveFullType(elementType, clazz));

											// ✅ primitivo
											if (typeUtil.isPrimitive(res)) {

												prop.put(

														"items",

														Map.of("type", typeUtil.mapType(res)));
											}

											// ✅ objeto
											else {

												prop.put(

														"items",

														buildSafeSchemaReference(res, clazz));
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

										String res = extractSimpleName(

												resolveFullType(generic, clazz));

										prop.put("type", "array");

										// ✅ primitivo
										if (typeUtil.isPrimitive(res)) {

											prop.put(

													"items",

													Map.of("type", typeUtil.mapType(res)));
										}

										// ✅ objeto
										else {

											prop.put(

													"items",

													buildSafeSchemaReference(res, clazz));
										}
									}

									// =================================================
									// ✅ MAP<K,V>
									// =================================================
									else if (rawType.contains("Map<")) {

										prop.put("type", "object");

										String valueType = parserUtil.extractMapValue(rawType);

										if (valueType != null && !valueType.contains(",")) {

											String res = extractSimpleName(

													resolveFullType(valueType, clazz));

											// ✅ primitivo
											if (typeUtil.isPrimitive(res)) {

												prop.put(

														"additionalProperties",

														Map.of("type", typeUtil.mapType(res)));
											}

											// ✅ objeto
											else {

												prop.put(

														"additionalProperties",

														buildSafeSchemaReference(res, clazz));
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

										// ✅ flatten SOLO wrappers especiales
										else if (shouldFlatten(resolved)) {

											flattenSchemaProperties(resolved, properties);

											flattened = true;
										}

										// ✅ enum
										else if (enumMap != null && enumMap.containsKey(resolved)) {

											ensureEnumSchema(resolved);

											prop.put("$ref", "#/components/schemas/" + resolved);
										}

										// ✅ objeto normal
										else {

											if (IGNORED_TYPES.contains(resolved)) {

												prop.put("type", "object");
											}

								else {

												prop.putAll(

														buildSafeSchemaReference(resolved, clazz));
											}
										}

										// ✅ wrapper flatten
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

									// ✅ required
									if (isRequired(field)) {

										required.add(name);
									}

									// ✅ agregar propiedad
									properties.put(name, prop);
								});
					});

			// =================================================
			// ✅ REQUIRED
			// =================================================
			if (!required.isEmpty()) {

				schema.put("required", required);
			}

			return schema;

		} finally {

			processing.remove(className);
		}
	}

	// =========================================================
	// ✅ WRAPPER DETECTION
	// =========================================================
	private boolean shouldFlatten(String typeName) {

		if (typeName == null || typeName.isBlank()) {
			return false;
		}

		Optional<ClassOrInterfaceDeclaration> clazzOpt = classIndexer.findClass(typeName);

		if (clazzOpt.isEmpty()) {
			return false;
		}

		ClassOrInterfaceDeclaration clazz = clazzOpt.get();

		// ✅ analizar campos
		return clazz.getFields().stream()

				.filter(f -> !f.isStatic() && !f.isTransient())

				.flatMap(f -> f.getVariables().stream())

				.allMatch(var -> {

					String rawType = var.getType().asString();

					// ❌ si es lista o map → NO flatten
					if (rawType.contains("List<") || rawType.contains("Map<")) {
						return false;
					}

					String cleanType = rawType.startsWith("Optional<") ? parserUtil.extractGeneric(rawType) : rawType;

					String resolved = parserUtil.resolveFinalType(cleanType);

					// ✅ SOLO flatten si TODOS son primitivos
					return typeUtil.isPrimitive(resolved) || "String".equals(resolved) || "Integer".equals(resolved)
							|| "Long".equals(resolved);
				});
	}

	// =========================================================
	// ✅ FLATTEN RECURSIVO
	// =========================================================
	@SuppressWarnings("unchecked")
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

				.map(cu ->

				cu.getImports().stream()

						.filter(i -> !i.isAsterisk())

						.filter(i -> i.getName().getIdentifier().equals(simpleType))

						.map(i -> i.getNameAsString())

						.findFirst()

						.orElse(simpleType))

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

		List<String> values = enumDecl.getEntries().stream()

				.map(e -> e.getNameAsString())

				.collect(Collectors.toList());

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

			// ✅ NotNull
			if (ann.getNameAsString().equals("NotNull")) {

				prop.put("nullable", false);
			}

			// ✅ Size
			if (ann.getNameAsString().equals("Size")) {

				ann.ifNormalAnnotationExpr(a -> {

					a.getPairs().forEach(p -> {

						if (p.getNameAsString().equals("min")) {

							prop.put("minLength",

									Integer.parseInt(p.getValue().toString()));
						}

						if (p.getNameAsString().equals("max")) {

							prop.put("maxLength",

									Integer.parseInt(p.getValue().toString()));
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

	// =========================================================
	// ✅ REQUIRED DETECTION
	// =========================================================
	private boolean isRequired(FieldDeclaration field) {

		return field.getAnnotations().stream().anyMatch(ann -> {

			String name = ann.getNameAsString();

			if (name.equals("NotNull") || name.equals("NotBlank") || name.equals("NotEmpty")
					|| name.equals("NonNull")) {

				return true;
			}

			if (name.equals("JsonProperty")) {

				if (ann.isNormalAnnotationExpr()) {

					return ann.asNormalAnnotationExpr().getPairs().stream().anyMatch(p ->

					p.getNameAsString().equals("required")

							&& p.getValue().toString().equals("true"));
				}
			}

			return false;
		});
	}

	// =========================================================
	// ✅ SAFE SCHEMA REF
	// =========================================================
	private Map<String, Object> buildSafeSchemaReference(String type, ClassOrInterfaceDeclaration context) {

		if (type == null || type.isBlank()) {

			return Map.of("type", "object");
		}

		// ✅ tipos ignorados
		if (IGNORED_TYPES.contains(type)) {

			return Map.of("type", "object");
		}

		// ✅ schemas enterprise
		if (registerKnownExternalSchema(type)) {

			return Map.of("$ref", "#/components/schemas/" + type);
		}

		// ✅ buscar clase real
		Optional<ClassOrInterfaceDeclaration> target =

				classIndexer.findClass(type);

		// ✅ clase encontrada
		if (target.isPresent()) {

			ensureSchemaWithParsing(type, context);

			return Map.of("$ref", "#/components/schemas/" + type);
		}

		// ✅ fallback seguro
		System.out.println("⚠️ Clase externa/no accesible detectada: " + type);

		return Map.of("type", "object");
	}

	// =========================================================
	// ✅ COMMON ENTERPRISE SCHEMAS
	// =========================================================
	@SuppressWarnings("unchecked")
	public boolean registerKnownExternalSchema(String type) {

		// =====================================================
		// ✅ HeaderEntrada
		// =====================================================
		if ("HeaderEntrada".equals(type)) {

			// ✅ validar si existe vacío
			if (schemaMap.containsKey(type)) {

				Object existingProps = schemaMap.get(type).get("properties");

				// ✅ ya válido
				if (existingProps instanceof Map && !((Map<?, ?>) existingProps).isEmpty()) {

					return true;
				}

				// ✅ schema vacío
				schemaMap.remove(type);
			}

			Map<String, Object> props = new LinkedHashMap<>();

			props.put("identificadorUnicoGlobal", Map.of("type", "string"));

			props.put("identificacionCanal", Map.of("type", "string"));

			props.put("identificacionSubCanal", Map.of("type", "string"));

			props.put("siglaAplicacion",

					Map.of("type", "string", "minLength", 1, "maxLength", 4));

			props.put("identificacionUsuario", Map.of("type", "string"));

			props.put("direccionIpConsumidor", Map.of("type", "string"));

			props.put("direccionIpCliente", Map.of("type", "string"));

			props.put("fechaEnvioMensaje", Map.of("type", "string"));

			props.put("horaEnvioMensaje", Map.of("type", "string"));

			props.put("atributoPagineo", Map.of("type", "string"));

			props.put("claveBusqueda", Map.of("type", "string"));

			props.put("cantidadRegistros", Map.of("type", "integer"));

			Map<String, Object> schema = new LinkedHashMap<>();

			schema.put("type", "object");

			schema.put("description", "Header corporativo de entrada Mercantil");

			schema.put("properties", props);

			schema.put("required",

					List.of("identificadorUnicoGlobal", "identificacionCanal", "identificacionSubCanal",
							"siglaAplicacion", "direccionIpConsumidor", "direccionIpCliente", "fechaEnvioMensaje",
							"horaEnvioMensaje"));

			schemaMap.put(type, schema);

			return true;
		}

		// =====================================================
		// ✅ HeaderSalida
		// =====================================================
		if ("HeaderSalida".equals(type)) {

			// ✅ validar si existe vacío
			if (schemaMap.containsKey(type)) {

				Object existingProps = schemaMap.get(type).get("properties");

				// ✅ ya válido
				if (existingProps instanceof Map && !((Map<?, ?>) existingProps).isEmpty()) {

					return true;
				}

				// ✅ schema vacío
				schemaMap.remove(type);
			}

			Map<String, Object> props = new LinkedHashMap<>();

			props.put("tipoMensaje",

					Map.of("type", "string", "example", "F"));

			props.put("mensajeProgramadorSistema",

					Map.of("type", "string", "example", "OPERACION EXITOSA"));

			props.put("codigoMensajeProgramador",

					Map.of("type", "string", "example", "0000"));

			props.put("mensajeUsuario",

					Map.of("type", "string", "example", "TRANSACCION EXITOSA"));

			props.put("codigoMensajeUsuario",

					Map.of("type", "string", "example", "0000"));

			props.put("fechaSalidaMensaje",

					Map.of("type", "string", "example", "20250609"));

			props.put("horaSalidaMensaje",

					Map.of("type", "string", "example", "102530"));

			Map<String, Object> schema = new LinkedHashMap<>();

			schema.put("type", "object");

			schema.put("description", "Header corporativo de salida Mercantil");

			schema.put("properties", props);

			schema.put("required",

					List.of("tipoMensaje", "mensajeProgramadorSistema", "codigoMensajeProgramador", "mensajeUsuario",
							"codigoMensajeUsuario", "fechaSalidaMensaje", "horaSalidaMensaje"));

			schemaMap.put(type, schema);

			return true;
		}

		// =====================================================
		// ✅ no schema encontrado
		// =====================================================
		return false;
	}

	private String normalizeJsonKey(String name) {

		if (name == null || name.isBlank()) {
			return name;
		}

		String[] tokens = name.replaceAll("([A-Z])", " $1").trim().toLowerCase().split("\\s+");

		StringBuilder result = new StringBuilder();

		for (int i = 0; i < tokens.length; i++) {

			String token = tokens[i];

			String normalized = AbbreviationUtil.getAbbreviations().getOrDefault(token, token);

			if (i == 0) {
				result.append(normalized);
			} else {
				result.append(Character.toUpperCase(normalized.charAt(0))).append(normalized.substring(1));
			}
		}

		return result.toString();
	}

	private boolean isWrapperName(String name) {

		if (name == null) {
			return true;
		}

		return name.startsWith("bodyEntrada") || name.startsWith("bodySalida") || name.startsWith("headerEntrada")
				|| name.startsWith("headerSalida");
	}

}