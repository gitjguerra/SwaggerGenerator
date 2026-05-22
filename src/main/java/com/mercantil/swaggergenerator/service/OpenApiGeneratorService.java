package com.mercantil.swaggergenerator.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;

@Service
public class OpenApiGeneratorService {

	// ✅ mapa de schemas
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	// ✅ mapa de ejemplos
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ =========================
	// ✅ GENERADOR PRINCIPAL
	// ✅ =========================
	public OpenApiDoc generate(ServiceItem service) {

		OpenApiDoc doc = new OpenApiDoc();

		// ✅ limpiar estado
		schemaMap = new LinkedHashMap<>();
		exampleMap = new LinkedHashMap<>();

		doc.security = List.of(Map.of("bearerAuth", List.of()));

		doc.info.title = "API " + service.getName();

		doc.servers.add(Map.of("url", (service.getHost() == null ? "" : service.getHost()) + service.getBasePath()));

		// ✅ 1. cargar beans
		List<File> beanFiles = findJavaFiles(service.getBeansPath());

		// ✅ registro previo (evita dependencias rotas)
		preloadSchemas(beanFiles);

		// ✅ procesar beans
		beanFiles.forEach(f -> processBeanFile(f, doc, service.getBasePackage()));

		// ✅ 2. controllers
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());
		controllerFiles.forEach(f -> processControllerFile(f, doc));

		// ✅ Set headers genericos
		registerBaseSchemas();

		doc.components.put("schemas", schemaMap);

		return doc;
	}

	// ✅ =========================
	// ✅ PROCESAR CONTROLLERS
	// ✅ =========================
	private void processControllerFile(File file, OpenApiDoc doc) {

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

				if (!clazz.getAnnotationByName("RestController").isPresent())
					return;

				String controllerName = clazz.getNameAsString();

				String tag = controllerName.replace("Controller", "").replaceAll("([a-z])([A-Z])", "$1 $2");

				if (doc.tags.stream().noneMatch(t -> t.get("name").equals(tag))) {

					doc.tags.add(Map.of("name", tag, "description", "Operaciones " + tag));
				}

				String basePath = extractClassMapping(clazz);

				clazz.getMethods().forEach(method -> processMethod(method, doc, tag, basePath));
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ✅ =========================
	// ✅ PROCESAR MÉTODOS
	// ✅ =========================
	private void processMethod(MethodDeclaration method, OpenApiDoc doc, String tag, String basePath) {

		String httpMethod = "";
		String path = "";

		// ✅ detectar método HTTP
		if (method.getAnnotationByName("PostMapping").isPresent()) {
			httpMethod = "post";
			path = extractMapping(method, "PostMapping");
		} else if (method.getAnnotationByName("GetMapping").isPresent()) {
			httpMethod = "get";
			path = extractMapping(method, "GetMapping");
		} else {
			return;
		}

		// ✅ construir URL final
		String fullPath = ("/" + basePath + "/" + path).replaceAll("//+", "/");

		Map<String, Object> op = new LinkedHashMap<>();

		op.put("tags", List.of(tag));
		op.put("summary", method.getNameAsString());

		// =========================================================
		// ✅ REQUEST BODY
		// =========================================================
		method.getParameters().forEach(p -> {

			if (p.getAnnotationByName("RequestBody").isPresent()) {

				String type = resolveFinalType(p.getType().asString());

				Map<String, Object> json = new LinkedHashMap<>();

				json.put("schema", Map.of("$ref", "#/components/schemas/" + type));

				Object example = exampleMap.get(type);

				if (example != null) {
					json.put("example", example);
				}

				op.put("requestBody", Map.of("content", Map.of("application/json", json)));
			}
		});

		// =========================================================
		// ✅ RESPONSE
		// =========================================================

		// ✅ 1. obtener tipo completo
		String rawReturn;

		// ✅ si es tipo parametrizado (ej: ResponseEntity<T>)
		if (method.getType().isClassOrInterfaceType()) {

			var type = method.getType().asClassOrInterfaceType();

			// ✅ tiene genéricos
			if (type.getTypeArguments().isPresent() && !type.getTypeArguments().get().isEmpty()) {

				// ✅ tomar el primer argumento <T>
				rawReturn = type.getTypeArguments().get().get(0).asString();

			} else {

				// ✅ sin genérico
				rawReturn = type.getNameAsString();
			}

		} else {
			rawReturn = method.getType().asString();
		}

		// ✅ limpiar espacios por seguridad
		rawReturn = rawReturn.replace(" ", "");

		// ✅ DEBUG OPCIONAL (puedes activarlo si quieres ver casos reales)
		System.out.println("RETURN TYPE → " + rawReturn);

		// =========================================================
		// ✅ 2. DESENVOLVER WRAPPERS IMPORTANTES
		// =========================================================

		// ✅ ResponseEntity<T> (incluye casos con paquete completo)
		if (rawReturn.contains("ResponseEntity<")) {
			rawReturn = extractGeneric(rawReturn);
		}

		// ✅ Optional<T>
		if (rawReturn.contains("Optional<")) {
			rawReturn = extractGeneric(rawReturn);
		}

		// =========================================================
		// ✅ 3. DETECTAR SI ES LISTA
		// =========================================================
		boolean isList = rawReturn.startsWith("List<");

		// =========================================================
		// ✅ 4. OBTENER TIPO FINAL LIMPIO
		// =========================================================
		String finalType = resolveFinalType(rawReturn);

		Map<String, Object> jsonContent = new LinkedHashMap<>();
		Map<String, Object> schema = new LinkedHashMap<>();

		// =========================================================
		// ✅ 5. MANEJO DE LISTAS
		// =========================================================
		if (isList) {

			String generic = extractGeneric(rawReturn);
			String cleanGeneric = resolveFinalType(generic);

			schema.put("type", "array");

			// ✅ si es primitivo
			if (isPrimitive(cleanGeneric)) {

				schema.put("items", Map.of("type", mapType(cleanGeneric)));

			} else {

				schema.put("items", Map.of("$ref", "#/components/schemas/" + cleanGeneric));
			}
		}

		// =========================================================
		// ✅ 6. PRIMITIVOS
		// =========================================================
		else if (isPrimitive(finalType)) {

			schema.put("type", mapType(finalType));

		}

		// =========================================================
		// ✅ 7. OBJETOS COMPLEJOS
		// =========================================================
		else {

			schema.put("$ref", "#/components/schemas/" + finalType);
		}

		// ✅ asignar schema
		jsonContent.put("schema", schema);

		// ✅ example
		Object example = exampleMap.get(finalType);

		if (example != null) {
			jsonContent.put("example", example);
		}

		// ✅ response final
		op.put("responses",
				Map.of("200", Map.of("description", "OK", "content", Map.of("application/json", jsonContent))));

		// ✅ registrar endpoint
		doc.paths.put(fullPath, new LinkedHashMap<>(Map.of(httpMethod, op)));
	}

	// ✅ =========================
	// ✅ PROCESAR BEANS
	// ✅ =========================
	private void processBeanFile(File file, OpenApiDoc doc, String basePackage) {

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

				if (!isInBasePackage(clazz, basePackage))
					return;

				String className = clazz.getNameAsString();

				// ✅ ignorar base
				if (className.equals("ConstructorRequired") || className.equals("BeansZOS"))
					return;

				// ✅ filtrar DTO
				if (!className.startsWith("Request") && !className.startsWith("Response")
						&& !className.startsWith("Body") && !className.startsWith("Header"))
					return;

				schemaMap.put(className, buildSchemaFromClass(clazz));

				exampleMap.put(className, buildExampleFromClass(clazz));
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ✅ =========================
	// ✅ SCHEMA
	// ✅ =========================
	private Map<String, Object> buildSchemaFromClass(ClassOrInterfaceDeclaration clazz) {

		// ✅ propiedades del objeto
		Map<String, Object> properties = new LinkedHashMap<>();

		// ✅ campos requeridos (@NotNull)
		List<String> required = new ArrayList<>();

		// ✅ ========================================
		// ✅ ENUM → convertir a lista de valores
		// ✅ ========================================
		if (clazz.isEnumDeclaration()) {

			List<String> values = clazz.asEnumDeclaration().getEntries().stream().map(e -> e.getNameAsString())
					.collect(Collectors.toList());

			return Map.of("type", "string", "enum", values);
		}

		// ✅ ========================================
		// ✅ PROCESAR CAMPOS
		// ✅ ========================================
		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = var.getNameAsString();

				// ✅ JsonProperty → usar nombre real JSON
				if (field.getAnnotationByName("JsonProperty").isPresent()) {

					String annotation = field.getAnnotationByName("JsonProperty").get().toString();

					if (annotation.contains("\"")) {
						name = annotation.split("\"")[1];
					}
				}

				// ✅ tipo original (IMPORTANTE NO PERDER GENÉRICOS)
				String rawType = field.getElementType().asString();

				// ✅ detectar Optional<T>
				boolean isOptional = rawType.startsWith("Optional<");

				// ✅ limpiar tipo si es Optional
				String cleanType = isOptional ? extractGeneric(rawType) : rawType;

				// ✅ tipo final limpio
				String type = resolveFinalType(cleanType);

				// ✅ NotNull → required
				if (field.getAnnotationByName("NotNull").isPresent()) {
					required.add(name);
				}

				Map<String, Object> prop = new LinkedHashMap<>();

				// ✅ ========================================
				// ✅ MAP (dinámico)
				// ✅ ========================================
				if (rawType.startsWith("Map<")) {

					String generic = extractGeneric(rawType); // "String,String"

					String[] parts = generic.split(",");

					String valueType = resolveFinalType(parts[1].trim());

					Map<String, Object> additional = new LinkedHashMap<>();

					if (isPrimitive(valueType)) {
						additional.put("type", mapType(valueType));
					} else {
						additional.put("$ref", "#/components/schemas/" + valueType);
					}

					prop.put("type", "object");
					prop.put("additionalProperties", additional);

				}

				// ✅ ========================================
				// ✅ LISTAS (usar rawType 🔥)
				// ✅ ========================================
				else if (rawType.startsWith("List<")) {

					String generic = extractGeneric(rawType);

					prop.put("type", "array");

					String cleanGeneric = resolveFinalType(generic);

					if (isPrimitive(cleanGeneric)) {

						prop.put("items", Map.of("type", mapType(cleanGeneric)));

					} else {

						prop.put("items", Map.of("$ref", "#/components/schemas/" + cleanGeneric));
					}

				}

				// ✅ ========================================
				// ✅ PRIMITIVOS + FORMAT
				// ✅ ========================================
				else if (isPrimitive(type)) {

					prop.put("type", mapType(type));

					if (type.equals("UUID")) {
						prop.put("format", "uuid");
					}

					if (type.equals("LocalDate")) {
						prop.put("format", "date");
					}

					if (type.equals("LocalDateTime") || type.equals("Date")) {
						prop.put("format", "date-time");
					}

					if (type.equals("BigDecimal")) {
						prop.put("format", "double");
					}
				}

				// ✅ ========================================
				// ✅ BYTE / BINARIO
				// ✅ ========================================
				else if (type.equals("byte") || type.equals("byte[]")) {

					prop.put("type", "string");
					prop.put("format", "byte");
				}

				// ✅ ========================================
				// ✅ OBJETOS COMPLEJOS ($ref)
				// ✅ ========================================
				else {

					prop.put("$ref", "#/components/schemas/" + type);
				}

				// ✅ ========================================
				// ✅ @Schema (description + example)
				// ✅ ========================================
				if (field.getAnnotationByName("Schema").isPresent()) {

					String annotation = field.getAnnotationByName("Schema").get().toString();

					if (annotation.contains("description")) {
						String desc = annotation.split("description\\s*=\\s*\"")[1].split("\"")[0];
						prop.put("description", desc);
					}

					if (annotation.contains("example")) {
						String ex = annotation.split("example\\s*=\\s*\"")[1].split("\"")[0];
						prop.put("example", ex);
					}
				}

				// ✅ ========================================
				// ✅ VALIDACIONES
				// ✅ ========================================

				// ✅ @Size
				if (field.getAnnotationByName("Size").isPresent()) {

					String annotation = field.getAnnotationByName("Size").get().toString();

					if (annotation.contains("min")) {
						String min = annotation.split("min\\s*=\\s*")[1].split("[,)]")[0];
						prop.put("minLength", Integer.parseInt(min));
					}

					if (annotation.contains("max")) {
						String max = annotation.split("max\\s*=\\s*")[1].split("[,)]")[0];
						prop.put("maxLength", Integer.parseInt(max));
					}
				}

				// ✅ @Min
				if (field.getAnnotationByName("Min").isPresent()) {

					String annotation = field.getAnnotationByName("Min").get().toString();

					// ✅ extraer número correctamente
					String min = annotation.replaceAll(".*value\\s*=\\s*", "").replaceAll(",.*", "")
							.replaceAll("[^0-9]", "");

					if (!min.isEmpty()) {
						prop.put("minimum", Long.parseLong(min));
					}
				}

				// ✅ @Max
				if (field.getAnnotationByName("Max").isPresent()) {

					String annotation = field.getAnnotationByName("Max").get().toString();

					String max = annotation.replaceAll(".*value\\s*=\\s*", "").replaceAll(",.*", "")
							.replaceAll("[^0-9]", "");

					if (!max.isEmpty()) {
						prop.put("maximum", Long.parseLong(max));
					}
				}

				// ✅ @Pattern
				if (field.getAnnotationByName("Pattern").isPresent()) {

					String annotation = field.getAnnotationByName("Pattern").get().toString();

					if (annotation.contains("regexp")) {
						String pattern = annotation.split("regexp\\s*=\\s*\"")[1].split("\"")[0];
						prop.put("pattern", pattern);
					}
				}

				// ✅ ========================================
				// ✅ OPTIONAL → nullable
				// ✅ ========================================
				if (isOptional) {
					prop.put("nullable", true);
				}

				// ✅ agregar propiedad final
				properties.put(name, prop);
			});
		});

		Map<String, Object> schema = new LinkedHashMap<>();

		// ✅ ========================================
		// ✅ HERENCIA → allOf
		// ✅ ========================================
		if (!clazz.getExtendedTypes().isEmpty()) {

			List<Object> allOf = new ArrayList<>();

			clazz.getExtendedTypes().forEach(parent -> {

				String parentName = parent.getNameAsString();

				allOf.add(Map.of("$ref", "#/components/schemas/" + parentName));
			});

			Map<String, Object> child = new LinkedHashMap<>();
			child.put("type", "object");
			child.put("properties", properties);

			if (!required.isEmpty()) {
				child.put("required", required);
			}

			allOf.add(child);

			schema.put("allOf", allOf);
		}

		// ✅ ========================================
		// ✅ OBJETO NORMAL
		// ✅ ========================================
		else {

			schema.put("type", "object");
			schema.put("properties", properties);

			if (!required.isEmpty()) {
				schema.put("required", required);
			}
		}

		if (properties.isEmpty() && clazz.getExtendedTypes().isEmpty()) {
			schema.put("type", "object");
			schema.put("additionalProperties", true);
		}

		return schema;
	}

	// ✅ =========================
	// ✅ EXAMPLE
	// ✅ =========================
	private Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

		Map<String, Object> example = new LinkedHashMap<>();

		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = var.getNameAsString();

				// ✅ JsonProperty override (CLAVE 🔥)
				if (field.getAnnotationByName("JsonProperty").isPresent()) {

					String annotation = field.getAnnotationByName("JsonProperty").get().toString();

					if (annotation.contains("\"")) {
						name = annotation.split("\"")[1];
					}
				}

				String rawType = field.getElementType().asString();

				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? extractGeneric(rawType) : rawType;

				String type = resolveFinalType(cleanType);

				// ✅ STRING
				if (type.equals("String")) {
					example.put(name, "string");
				}

				// ✅ NUMERICOS
				else if (type.equals("Integer") || type.equals("int")) {
					example.put(name, 0);
				} else if (type.equals("Long") || type.equals("long")) {
					example.put(name, 1);
				} else if (type.equals("Double") || type.equals("double")) {
					example.put(name, 0.0);
				} else if (type.equals("Boolean") || type.equals("boolean")) {
					example.put(name, true);
				}

				// ✅ ✅ NUEVOS TIPOS PRO 🔥

				else if (type.equals("UUID")) {
					example.put(name, "550e8400-e29b-41d4-a716-446655440000");
				}

				else if (type.equals("LocalDate")) {
					example.put(name, "2026-01-01");
				}

				else if (type.equals("LocalDateTime")) {
					example.put(name, "2026-01-01T10:00:00");
				}

				else if (type.equals("Date")) {
					example.put(name, "2026-01-01T10:00:00");
				}

				else if (type.equals("BigDecimal")) {
					example.put(name, 100.50);
				}

				// ✅ LISTA
				else if (rawType.startsWith("List<")) {

					String generic = extractGeneric(type);

					Object nested = buildExampleFromType(generic);

					example.put(name, List.of(nested));
				}

				// ✅ OBJETO
				else {

					Object nested = buildExampleFromType(type);

					example.put(name, nested);
				}
			});
		});

		return example;
	}

	// ✅ =========================
	// ✅ UTILIDADES
	// ✅ =========================
	private List<File> findJavaFiles(String root) {

		List<File> files = new ArrayList<>();

		File dir = new File(root);

		if (!dir.exists())
			return files;

		for (File f : dir.listFiles()) {

			if (f.isDirectory()) {
				files.addAll(findJavaFiles(f.getAbsolutePath()));
			} else if (f.getName().endsWith(".java")) {
				files.add(f);
			}
		}

		return files;
	}

	private String extractMapping(MethodDeclaration method, String name) {

		return method.getAnnotationByName(name)
				.flatMap(a -> a.toString().contains("\"") ? java.util.Optional.of(a.toString().split("\"")[1])
						: java.util.Optional.empty())
				.orElse("");
	}

	private String extractClassMapping(ClassOrInterfaceDeclaration clazz) {

		return clazz.getAnnotationByName("RequestMapping")
				.flatMap(a -> a.toString().contains("\"") ? java.util.Optional.of(a.toString().split("\"")[1])
						: java.util.Optional.empty())
				.orElse("");
	}

	private String extractGeneric(String type) {

		int start = type.indexOf("<");
		int end = type.lastIndexOf(">");

		if (start != -1 && end != -1) {
			return type.substring(start + 1, end);
		}

		return type;
	}

	private String resolveFinalType(String type) {

		if (type.contains(".")) {
			type = type.substring(type.lastIndexOf(".") + 1);
		}

		if (type.contains("<")) {

			int start = type.indexOf("<");
			int end = type.lastIndexOf(">");

			return resolveFinalType(type.substring(start + 1, end));
		}

		return type;
	}

	private void preloadSchemas(List<File> files) {

		files.forEach(f -> {
			try {
				CompilationUnit cu = StaticJavaParser.parse(f);

				cu.findAll(ClassOrInterfaceDeclaration.class)
						.forEach(c -> schemaMap.putIfAbsent(c.getNameAsString(), new LinkedHashMap<>()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private boolean isInBasePackage(ClassOrInterfaceDeclaration clazz, String basePackage) {

		return clazz.findCompilationUnit().flatMap(c -> c.getPackageDeclaration())
				.map(p -> p.getNameAsString().startsWith(basePackage)).orElse(false);
	}

	private Object buildExampleFromType(String type) {

		// ✅ si ya existe en cache → usarlo
		if (exampleMap.containsKey(type)) {
			return exampleMap.get(type);
		}

		// ✅ BYTE / BINARIO (IMÁGENES)
		if (type.equals("byte") || type.equals("byte[]")) {
			return "base64-string";
		}

		// ✅ buscar en schemas
		Map<String, Object> schema = schemaMap.get(type);

		// ✅ ENUM → tomar primer valor como ejemplo
		if (schema != null && schema.containsKey("enum")) {

			List<?> values = (List<?>) schema.get("enum");

			if (!values.isEmpty()) {
				return values.get(0);
			}
		}

		if (schema == null) {
			return Map.of("info", "type not found");
		}

		// ✅ construir dinámicamente (recursivo)
		Map<String, Object> example = new LinkedHashMap<>();

		Object propsObj = schema.get("properties");

		if (!(propsObj instanceof Map)) {
			return example;
		}

		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((key, val) -> {

			if (!(val instanceof Map))
				return;

			Map<String, Object> prop = (Map<String, Object>) val;

			// ✅ $ref
			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				// ✅ SOLO OBJETO (NO LISTA)
				example.put(key, buildExampleFromType(refType));
			}

			// ✅ array
			else if ("array".equals(prop.get("type"))) {

				Object items = prop.get("items");

				if (items instanceof Map && ((Map<?, ?>) items).containsKey("$ref")) {

					String ref = ((Map<?, ?>) items).get("$ref").toString();
					String refType = ref.substring(ref.lastIndexOf("/") + 1);

					example.put(key, List.of(buildExampleFromType(refType)));
				}
			}

			// ✅ primitivo
			else {
				example.put(key, "string");
			}
		});

		// ✅ fallback si no hubo propiedades
		if (example.isEmpty()) {

			// ✅ si no tiene propiedades → devolver objeto simple
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("id", "string");

			return fallback;
		}

		// ✅ cachear resultado
		exampleMap.put(type, example);

		return example;
	}

	private String mapType(String type) {
		// ✅ strings
		if (type.equals("String"))
			return "string";
		if (type.equals("UUID"))
			return "string";

		// ✅ enteros
		if (type.equals("Integer") || type.equals("int"))
			return "integer";
		if (type.equals("Long") || type.equals("long"))
			return "integer";

		// ✅ decimales
		if (type.equals("Double") || type.equals("double"))
			return "number";
		if (type.equals("BigDecimal"))
			return "number";

		// ✅ boolean
		if (type.equals("Boolean") || type.equals("boolean"))
			return "boolean";

		// ✅ fechas
		if (type.equals("LocalDate") || type.equals("LocalDateTime") || type.equals("Date")) {
			return "string";
		}

		return "string";
	}

	// ✅ verifica si es tipo primitivo
	private boolean isPrimitive(String type) {

		return type.equals("String") ||

				type.equals("Integer") || type.equals("int") || type.equals("Long") || type.equals("long")
				|| type.equals("Double") || type.equals("double") || type.equals("Boolean") || type.equals("boolean") ||

				// ✅ nuevos tipos
				type.equals("BigDecimal") || type.equals("UUID") || type.equals("LocalDate")
				|| type.equals("LocalDateTime") || type.equals("Date");
	}

	private void registerBaseSchemas() {

		// =========================================================
		// ✅ HEADER ENTRADA
		// =========================================================
		Map<String, Object> headerEntrada = new LinkedHashMap<>();

		headerEntrada.put("type", "object");

		Map<String, Object> propsEntrada = new LinkedHashMap<>();

		propsEntrada.put("identificadorUnicoGlobal", Map.of("type", "string"));
		propsEntrada.put("identificacionCanal", Map.of("type", "string"));
		propsEntrada.put("identificacionSubCanal", Map.of("type", "string"));
		propsEntrada.put("siglaAplicacion", Map.of("type", "string", "minLength", 1, "maxLength", 4));
		propsEntrada.put("identificacionUsuario", Map.of("type", "string"));
		propsEntrada.put("direccionIpConsumidor", Map.of("type", "string"));
		propsEntrada.put("direccionIpCliente", Map.of("type", "string"));
		propsEntrada.put("fechaEnvioMensaje", Map.of("type", "string"));
		propsEntrada.put("horaEnvioMensaje", Map.of("type", "string"));
		propsEntrada.put("atributoPagineo", Map.of("type", "string"));
		propsEntrada.put("claveBusqueda", Map.of("type", "string"));
		propsEntrada.put("cantidadRegistros", Map.of("type", "integer"));

		headerEntrada.put("properties", propsEntrada);

		headerEntrada.put("required",
				List.of("identificadorUnicoGlobal", "identificacionCanal", "identificacionSubCanal", "siglaAplicacion",
						"direccionIpConsumidor", "direccionIpCliente", "fechaEnvioMensaje", "horaEnvioMensaje"));

		schemaMap.putIfAbsent("HeaderEntrada", headerEntrada);

		// =========================================================
		// ✅ HEADER SALIDA
		// =========================================================
		Map<String, Object> headerSalida = new LinkedHashMap<>();

		headerSalida.put("type", "object");

		Map<String, Object> propsSalida = new LinkedHashMap<>();

		propsSalida.put("tipoMensaje", Map.of("type", "string", "example", "F"));
		propsSalida.put("mensajeProgramadorSistema", Map.of("type", "string"));
		propsSalida.put("codigoMensajeProgramador", Map.of("type", "string"));
		propsSalida.put("mensajeUsuario",
				Map.of("type", "string", "example", "EN ESTE MOMENTO NO PODEMOS PROCESAR SU OPERACION"));
		propsSalida.put("codigoMensajeUsuario", Map.of("type", "string"));
		propsSalida.put("fechaSalidaMensaje", Map.of("type", "string"));
		propsSalida.put("horaSalidaMensaje", Map.of("type", "string"));

		headerSalida.put("properties", propsSalida);

		schemaMap.putIfAbsent("HeaderSalida", headerSalida);
	}

	// =========================================================
	// ✅ Generar y guardar
	// =========================================================
	public String generateAndSave(ServiceItem service, String outputDir) {

		try {
			// ✅ generar swagger
			OpenApiDoc doc = generate(service);

			// ✅ crear carpeta si no existe
			File dir = new File(outputDir);
			if (!dir.exists()) {
				dir.mkdirs();
			}

			// ✅ archivo destino
			File file = new File(dir, service.getName() + ".json");

			// ✅ escribir JSON formateado
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);

			mapper.writeValue(file, doc);

			return file.getAbsolutePath();

		} catch (Exception e) {
			throw new RuntimeException("Error generando archivo para: " + service.getName(), e);
		}
	}

	// =========================================================
	// ✅ Generar y guardar todos
	// =========================================================
	public List<String> generateAllAndSave(List<ServiceItem> services, String outputDir) {

		List<String> rutas = new ArrayList<>();

		services.forEach(s -> {
			String path = generateAndSave(s, outputDir);
			rutas.add(path);
		});

		return rutas;
	}

}