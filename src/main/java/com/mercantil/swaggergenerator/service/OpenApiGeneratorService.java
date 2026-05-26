package com.mercantil.swaggergenerator.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

	private static final List<String> IGNORED_TYPES = List.of("ConstructorRequired", "BeansZOS");

	// ✅ CONTEXTO DE VALORES CONSISTENTES
	private Map<String, Object> dataContext = new LinkedHashMap<>();

	// ✅ ABBREVIATIONS (parseado del PDF)
	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();

	static {
		loadAbbreviations();
	}

	// ✅ mapa de schemas
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	// ✅ mapa de ejemplos
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ =========================
	// ✅ GENERADOR PRINCIPAL
	// ✅ =========================
	public OpenApiDoc generate(ServiceItem service) {

		OpenApiDoc doc = new OpenApiDoc();

		schemaMap = new LinkedHashMap<>();
		exampleMap = new LinkedHashMap<>();

		doc.security = List.of(Map.of("bearerAuth", List.of()));
		doc.info.title = "API " + service.getName();

		doc.servers.add(Map.of("url", (service.getHost() == null ? "" : service.getHost()) + service.getBasePath()));

		// ✅ 1. REGISTRAR HARDCORE BASE
		registerBaseSchemas();

		// ✅ 2. PROCESAR BEANS (SIN preload)
		List<File> beanFiles = findJavaFiles(service.getBeansPath());
		beanFiles.forEach(f -> processBeanFile(f, doc, service.getBasePackage()));

		// ✅ 3. PROCESAR CONTROLLERS
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());
		controllerFiles.forEach(f -> processControllerFile(f, doc));

		// ✅ 4. FINAL
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

		// =========================================================
		// ✅ 1. DETECTAR MÉTODO HTTP
		// =========================================================
		if (method.getAnnotationByName("PostMapping").isPresent()) {
			httpMethod = "post";
			path = extractMapping(method, "PostMapping");
		} else if (method.getAnnotationByName("GetMapping").isPresent()) {
			httpMethod = "get";
			path = extractMapping(method, "GetMapping");
		} else {
			return;
		}

		String fullPath = ("/" + basePath + "/" + path).replaceAll("//+", "/");

		Map<String, Object> op = new LinkedHashMap<>();
		op.put("tags", List.of(tag));
		op.put("summary", method.getNameAsString());
		op.put("operationId", method.getNameAsString());

		// =========================================================
		// ✅ HELPERS 🔥
		// =========================================================
		java.util.function.Function<String, Map<String, Object>> safeRef = type -> {
			if (type == null || IGNORED_TYPES.contains(type)) {
				return Map.of("type", "object");
			}
			return Map.of("$ref", "#/components/schemas/" + type);
		};

		// =========================================================
		// ✅ 2. REQUEST
		// =========================================================
		Map<String, Object> requestSchema = new LinkedHashMap<>();
		Map<String, Object> requestProps = new LinkedHashMap<>();

		requestProps.put("headerEntrada", safeRef.apply("HeaderEntrada"));

		String requestBodyType = null;
		String bodyName = null;

		for (var p : method.getParameters()) {

			String rawType = p.getType().asString();

			if (rawType.contains("Optional<")) {
				rawType = extractGeneric(rawType);
			}

			if (rawType.startsWith("Request")) {

				String requestClass = resolveFinalType(rawType);

				Map<String, Object> reqSchema = schemaMap.get(requestClass);

				if (reqSchema != null && reqSchema.get("properties") instanceof Map) {

					Map<String, Object> props = (Map<String, Object>) reqSchema.get("properties");

					for (var entry : props.entrySet()) {

						if (entry.getKey().startsWith("bodyEntrada")) {

							Map<String, Object> refObj = (Map<String, Object>) entry.getValue();

							if (refObj.containsKey("$ref")) {

								String ref = refObj.get("$ref").toString();
								requestBodyType = ref.substring(ref.lastIndexOf("/") + 1);

								// ✅ BLOQUEO ABSOLUTO
								if (IGNORED_TYPES.contains(requestBodyType)) {
									requestBodyType = null;
									break;
								}

								bodyName = capitalize(entry.getKey().replace("bodyEntrada", ""));

								ensureSchemaExists(requestBodyType);
								break;
							}
						}
					}
				}
			}
		}

		if (requestBodyType != null) {
			requestProps.put("bodyEntrada" + bodyName, safeRef.apply(requestBodyType));
		}

		requestSchema.put("type", "object");
		requestSchema.put("properties", requestProps);

		Map<String, Object> requestJson = new LinkedHashMap<>();
		requestJson.put("schema", requestSchema);

		Map<String, Object> requestExample = new LinkedHashMap<>();
		requestExample.put("headerEntrada", buildHeaderEntradaExample());

		if (requestBodyType != null) {

			Object bodyExample = exampleMap.get(requestBodyType);
			if (!(bodyExample instanceof Map)) {
				bodyExample = new LinkedHashMap<>();
			}

			requestExample.put("bodyEntrada" + bodyName, bodyExample);
		}

		requestJson.put("example", requestExample);
		requestJson.put("examples", Map.of("default", Map.of("summary", "Ejemplo generado", "value", requestExample)));

		op.put("requestBody", Map.of("required", true, "content", Map.of("application/json", requestJson)));

		// =========================================================
		// ✅ 3. RESPONSE
		// =========================================================
		String rawReturn = method.getType().asString();

		String responseType = unwrapResponseType(rawReturn);

		// 🔴 BLOQUEO DEFINITIVO
		if (IGNORED_TYPES.contains(responseType) || responseType.equals("ClientResponse")) {
			responseType = null;
		}

		ensureSchemaExists(responseType);

		String responseBodyName = responseType != null ? capitalize(responseType.replace("Response", "")) : "";

		String expectedBodyClass = "BodySalida" + responseBodyName;

		// ✅ BLOQUEO TAMBIÉN AQUÍ
		if (IGNORED_TYPES.contains(expectedBodyClass)) {
			expectedBodyClass = null;
		}

		boolean canHaveBody = expectedBodyClass != null && schemaMap.containsKey(expectedBodyClass);

		Map<String, Object> responseSchema = new LinkedHashMap<>();
		Map<String, Object> responseProps = new LinkedHashMap<>();

		responseProps.put("headerSalida", safeRef.apply("HeaderSalida"));

		if (canHaveBody) {
			responseProps.put("bodySalida" + responseBodyName, safeRef.apply(expectedBodyClass));
		}

		responseSchema.put("type", "object");
		responseSchema.put("properties", responseProps);

		Map<String, Object> responseJson = new LinkedHashMap<>();
		responseJson.put("schema", responseSchema);

		Map<String, Object> responseExample = new LinkedHashMap<>();

		// ✅ SIEMPRE headerSalida (FORZADO)
		responseExample.put("headerSalida", buildHeaderSalidaExample());

		// ✅ BODY (SI EXISTE)
		if (canHaveBody) {

			Object exampleBody = exampleMap.get(expectedBodyClass);

			// ✅ si no hay ejemplo → poner objeto vacío (NO omitir)
			if (!(exampleBody instanceof Map)) {
				exampleBody = new LinkedHashMap<>();
			}

			responseExample.put("bodySalida" + responseBodyName, exampleBody);
		}

		responseJson.put("example", responseExample);
		responseJson.put("examples",
				Map.of("default", Map.of("summary", "Ejemplo generado", "value", responseExample)));

		Map<String, Object> responses = new LinkedHashMap<>();

		responses.put("200",
				Map.of("description", "Operación exitosa", "content", Map.of("application/json", responseJson)));

		op.put("responses", responses);

		Map<String, Object> pathItem = (Map<String, Object>) doc.paths.getOrDefault(fullPath, new LinkedHashMap<>());

		pathItem.put(httpMethod, op);
		doc.paths.put(fullPath, pathItem);

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

				// ✅ IGNORAR TOTALMENTE
				if (IGNORED_TYPES.contains(className)) {
					return;
				}

				Map<String, Object> schema = buildSchemaFromClass(clazz);

				schemaMap.putIfAbsent(className, schema);

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

	    Map<String, Object> properties = new LinkedHashMap<>();
	    List<String> required = new ArrayList<>();

	    String className = clazz.getNameAsString();

	    // ✅ IGNORAR CLASES EXTERNAS
	    if (IGNORED_TYPES.contains(className)) {
	        return Map.of("type", "object", "additionalProperties", true);
	    }

	    // =========================================================
	    // ✅ ENUM
	    // =========================================================
	    if (clazz.isEnumDeclaration()) {

	        List<String> values = clazz.asEnumDeclaration().getEntries()
	                .stream()
	                .map(e -> e.getNameAsString())
	                .collect(Collectors.toList());

	        return Map.of("type", "string", "enum", values);
	    }

	    // =========================================================
	    // ✅ CAMPOS
	    // =========================================================
	    clazz.getFields().forEach(field -> {

	        field.getVariables().forEach(var -> {

	            String name = var.getNameAsString();

	            // ✅ JsonProperty override
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

	            // ✅ IGNORAR tipo externo
	            if (!schemaMap.containsKey(type) && !isPrimitive(type)) {
	                properties.put(name, Map.of("type", "object"));
	                return;
	            }

	            if (field.getAnnotationByName("NotNull").isPresent()) {
	                required.add(name);
	            }

	            Map<String, Object> prop = new LinkedHashMap<>();

	            // =========================================================
	            // ✅ LISTA
	            // =========================================================
	            if (rawType.startsWith("List<")) {

	                String generic = resolveFinalType(extractGeneric(rawType));

	                prop.put("type", "array");

	                if (!schemaMap.containsKey(generic) && !isPrimitive(generic)) {
	                    prop.put("items", Map.of("type", "object"));
	                } else if (isPrimitive(generic)) {
	                    prop.put("items", Map.of("type", mapType(generic)));
	                } else {
	                    prop.put("items", Map.of("$ref", "#/components/schemas/" + generic));
	                }
	            }

	            // =========================================================
	            // ✅ PRIMITIVO
	            // =========================================================
	            else if (isPrimitive(type)) {

	                prop.put("type", mapType(type));

	                if (type.equals("UUID"))
	                    prop.put("format", "uuid");
	                if (type.equals("LocalDate"))
	                    prop.put("format", "date");
	                if (type.equals("LocalDateTime") || type.equals("Date"))
	                    prop.put("format", "date-time");
	                if (type.equals("BigDecimal"))
	                    prop.put("format", "double");
	            }

	            // =========================================================
	            // ✅ OBJETO
	            // =========================================================
	            else {

	                if (schemaMap.containsKey(type)) {
	                    prop.put("$ref", "#/components/schemas/" + type);
	                } else {
	                    prop.put("type", "object");
	                }
	            }

	            // =========================================================
	            // ✅ NULLABLE
	            // =========================================================
	            if (isOptional) {
	                prop.put("nullable", true);
	            }

	            // =========================================================
	            // ✅ EJEMPLO (ÚNICO LUGAR CORRECTO)
	            // =========================================================
	            Object exampleValue = generateSmartExample(name);
	            if (exampleValue != null) {
	                prop.put("example", exampleValue);
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

	    if (properties.isEmpty()) {
	        schema.put("additionalProperties", true);
	    }

	    return schema;
	}

	// ✅ =========================
	// ✅ EXAMPLE
	// ✅ =========================
	private Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

	    Map<String, Object> example = new LinkedHashMap<>();

	    // ✅ resetear contexto por objeto
	    dataContext = new LinkedHashMap<>();

	    // ✅ recorrer campos
	    clazz.getFields().forEach(field -> {

	        field.getVariables().forEach(var -> {

	            String name = var.getNameAsString();

	            // =========================================================
	            // ✅ JsonProperty override
	            // =========================================================
	            if (field.getAnnotationByName("JsonProperty").isPresent()) {

	                String annotation = field.getAnnotationByName("JsonProperty").get().toString();

	                if (annotation.contains("\"")) {
	                    name = annotation.split("\"")[1];
	                }
	            }

	            // =========================================================
	            // ✅ tipo del campo
	            // =========================================================
	            String rawType = field.getElementType().asString();

	            boolean isOptional = rawType.startsWith("Optional<");

	            String cleanType = isOptional ? extractGeneric(rawType) : rawType;

	            String type = resolveFinalType(cleanType);

	            // =========================================================
	            // ✅ 1. LISTAS (🔥 PRIORIDAD MÁXIMA)
	            // =========================================================
	            if (rawType.startsWith("List<")) {

	                String generic = extractGeneric(rawType);
	                Object nested = buildExampleFromType(generic);

	                example.put(name, List.of(nested));
	                return;
	            }

	            // =========================================================
	            // ✅ 2. OBJETOS
	            // =========================================================
	            if (!isPrimitive(type)) {

	                Object nested = buildExampleFromType(type);
	                example.put(name, nested);
	                return;
	            }

	            // =========================================================
	            // ✅ 3. SEMÁNTICA (DESPUÉS)
	            // =========================================================
	            Object value = generateSmartExample(name);

	            if (value != null) {

	                // ✅ adaptar tipo
	                if (isNumericType(type) && value instanceof String) {
	                    try {
	                        value = Integer.parseInt(value.toString().replaceAll("\\D", ""));
	                    } catch (Exception ignored) {}
	                }

	                example.put(name, value);
	                return;
	            }

	            // =========================================================
	            // ✅ 4. FALLBACK
	            // =========================================================
	            example.put(name, resolveValueByType(type));
	        });
	    });

	    return example;
	}

	private boolean isNumericType(String type) {
		return type.equals("Integer") || type.equals("int") || type.equals("Long") || type.equals("long")
				|| type.equals("Double") || type.equals("double");
	}

	// ✅ =========================
	// ✅ UTILIDADES
	// ✅ =========================
	private List<File> findJavaFiles(String root) {

		List<File> files = new ArrayList<>();

		File dir = new File(root);

		if (!dir.exists())
			return files;

		File[] filesArray = dir.listFiles();
		if (filesArray == null)
			return files;

		for (File f : filesArray) {

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

	private String extractGeneric(String input) {

		int start = input.indexOf("<");
		int end = input.lastIndexOf(">");

		if (start == -1 || end == -1 || end <= start) {
			return input;
		}

		String inner = input.substring(start + 1, end).trim();

		// 🔥 SI TODAVÍA HAY < > → sigue bajando
		if (inner.contains("<")) {
			return extractGeneric(inner);
		}

		return inner;
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
			return new LinkedHashMap<>();
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

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();
						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(key, List.of(buildExampleFromType(refType)));

					} else {

						example.put(key, List.of("string")); // fallback
					}
				}
			}

			// ✅ primitivo
			else {

				Object value = generateSmartExample(key);

				// ✅ SI HAY SEMÁNTICA
				if (value != null) {
					example.put(key, value);
				} else {
					example.put(key, "string"); // fallback simple
				}

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

				// ✅ NUEVOS
				type.equals("BigDecimal") || type.equals("BigInteger") || type.equals("UUID")
				|| type.equals("LocalDate") || type.equals("LocalDateTime") || type.equals("Date");

	}

	private void registerBaseSchemas() {

		// =========================================================
		// ✅ HEADER ENTRADA (SCHEMA)
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

		schemaMap.putIfAbsent("HeaderEntrada", headerEntrada);

		// =========================================================
		// ✅ HEADER SALIDA (SCHEMA)
		// =========================================================
		Map<String, Object> headerSalida = new LinkedHashMap<>();
		headerSalida.put("type", "object");

		Map<String, Object> propsSalida = new LinkedHashMap<>();

		propsSalida.put("tipoMensaje", Map.of("type", "string", "example", "F"));
		propsSalida.put("mensajeProgramadorSistema", Map.of("type", "string"));
		propsSalida.put("codigoMensajeProgramador", Map.of("type", "string"));
		propsSalida.put("mensajeUsuario", Map.of("type", "string"));
		propsSalida.put("codigoMensajeUsuario", Map.of("type", "string"));
		propsSalida.put("fechaSalidaMensaje", Map.of("type", "string"));
		propsSalida.put("horaSalidaMensaje", Map.of("type", "string"));

		headerSalida.put("properties", propsSalida);

		schemaMap.putIfAbsent("HeaderSalida", headerSalida);

		// =========================================================
		// ✅ EJEMPLOS HARDCODE ✅
		// =========================================================

		Map<String, Object> headerEntradaExample = new LinkedHashMap<>();
		headerEntradaExample.put("identificadorUnicoGlobal", "string");
		headerEntradaExample.put("identificacionCanal", "string");
		headerEntradaExample.put("identificacionSubCanal", "string");
		headerEntradaExample.put("siglaAplicacion", "ABC");
		headerEntradaExample.put("identificacionUsuario", "string");
		headerEntradaExample.put("direccionIpConsumidor", "127.0.0.1");
		headerEntradaExample.put("direccionIpCliente", "127.0.0.1");
		headerEntradaExample.put("fechaEnvioMensaje", "20260101");
		headerEntradaExample.put("horaEnvioMensaje", "120000");
		headerEntradaExample.put("atributoPagineo", "N");
		headerEntradaExample.put("claveBusqueda", "string");
		headerEntradaExample.put("cantidadRegistros", 0);

		Map<String, Object> headerSalidaExample = new LinkedHashMap<>();
		headerSalidaExample.put("tipoMensaje", "F");
		headerSalidaExample.put("mensajeProgramadorSistema", "string");
		headerSalidaExample.put("codigoMensajeProgramador", "string");
		headerSalidaExample.put("mensajeUsuario", "string");
		headerSalidaExample.put("codigoMensajeUsuario", "string");
		headerSalidaExample.put("fechaSalidaMensaje", "20260101");
		headerSalidaExample.put("horaSalidaMensaje", "120000");

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

	private void ensureSchemaExists(String typeName) {

		if (typeName == null || typeName.isBlank())
			return;

		// 🔴 BLOQUEO CRÍTICO
		if (IGNORED_TYPES.contains(typeName) || typeName.equals("ClientResponse")
				|| typeName.equals("ConstructorRequired")) {
			return;
		}

		if (isPrimitive(typeName))
			return;

		if (schemaMap.containsKey(typeName))
			return;

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("additionalProperties", true);

		schemaMap.put(typeName, schema);
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty())
			return str;
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	private Map<String, Object> buildHeaderEntradaExample() {

		Map<String, Object> header = new LinkedHashMap<>();

		header.put("identificadorUnicoGlobal", "f215a700-4fdb-45ec-8540-7b030663afb3");
		header.put("identificacionCanal", "0006");
		header.put("identificacionSubCanal", "01");
		header.put("siglaAplicacion", "OLB");
		header.put("identificacionUsuario", "");
		header.put("direccionIpConsumidor", "10.0.12.48");
		header.put("direccionIpCliente", "10.0.12.48");
		header.put("fechaEnvioMensaje", "20251226");
		header.put("horaEnvioMensaje", "135046");
		header.put("atributoPagineo", "");
		header.put("claveBusqueda", "");
		header.put("cantidadRegistros", 0);

		return header;
	}

	private Map<String, Object> buildHeaderSalidaExample() {

		Map<String, Object> header = new LinkedHashMap<>();

		header.put("tipoMensaje", "I");
		header.put("mensajeProgramadorSistema", "Procesado correctamente");
		header.put("codigoMensajeProgramador", "0000");
		header.put("mensajeUsuario", "Operación exitosa");
		header.put("codigoMensajeUsuario", "0000");
		header.put("fechaSalidaMensaje", "20251226");
		header.put("horaSalidaMensaje", "135046");

		return header;
	}

	private String unwrapResponseType(String type) {

		if (type == null)
			return null;

		List<String> wrappers = List.of("ResponseEntity", "ClientResponse", "ConstructorRequired", "Optional");

		String current = type;

		while (current.contains("<") && current.contains(">")) {

			String outer = current.substring(0, current.indexOf("<")).trim();

			if (!wrappers.contains(outer)) {
				break;
			}

			current = extractGeneric(current);
		}

		return resolveFinalType(current);
	}

	private String normalizeName(String name) {

		if (name == null)
			return "";

		String lower = name.toLowerCase();

		List<Map.Entry<String, String>> entries = new ArrayList<>(ABBREV_MAP.entrySet());

		// ✅ ordenar por tamaño (evita choques tipo id vs identificacion)
		entries.sort((a, b) -> b.getKey().length() - a.getKey().length());

		for (Map.Entry<String, String> entry : entries) {

			String abbr = entry.getKey();
			String full = entry.getValue();

			// ✅ REEMPLAZO GLOBAL (FIX IMPORTANTE)
			lower = lower.replace(abbr, full);
		}

		return lower;
	}

	private Object generateSmartExample(String name) {

		String normalized = normalizeName(name);
		String lower = normalized.toLowerCase();

		// ✅ tokenizar SIEMPRE desde el nombre original
		List<String> tokens = tokenize(name);

		// =========================================================
		// ✅ 1. CONTEXTO (REUTILIZACIÓN)
		// =========================================================
		for (Map.Entry<String, Object> entry : dataContext.entrySet()) {

			String key = entry.getKey();

			if (lower.equals(key) || lower.endsWith(key)) {
				return entry.getValue();
			}
		}

		// =========================================================
		// ✅ 2. REGLAS FUERTES (NORMALIZED - 🔥 PRIORIDAD ALTA)
		// =========================================================

		if (normalized.contains("codigoproducto"))
			return "02";
		if (normalized.contains("codigopais"))
			return "VE";
		if (normalized.contains("codigoempresa"))
			return "0108";
		if (normalized.contains("codigorazon"))
			return "01";

		// =========================================================
		// ✅ 3. SEMÁNTICA
		// =========================================================

		// ✅ TIPO IDENTIFICACION
		if ((tokens.contains("tipo") && tokens.contains("identificacion")) || lower.contains("tipoidentificacion")) {
			String v = "V";
			dataContext.put("tipoidentificacion", v);
			return v;
		}

		// ✅ IDENTIFICACIÓN / CÉDULA
		if ((tokens.contains("cedula") || tokens.contains("rif") || tokens.contains("identificacion")
				|| lower.contains("cedula") || lower.contains("rif")) && !tokens.contains("tipo")) {
			Integer v = 12345678;
			dataContext.put("cedula", v);
			return v;
		}

		// ✅ PERSONA
		if (tokens.contains("persona") || lower.contains("numeropersona")) {
			Integer v = 12345678;
			dataContext.put("persona", v);
			return v;
		}

		// ✅ SUBCANAL
		if (tokens.contains("subcanal") || lower.contains("subcanal")) {
			return "09";
		}

		// ✅ MONEDA
		if (tokens.contains("moneda") || lower.contains("moneda")) {
			return "VES";
		}

		// ✅ TARJETA
		if (tokens.contains("tarjeta") || lower.contains("tarjeta")) {

			String v = "1234567890123456";

			if (name.toLowerCase().endsWith("s")) {
				return List.of(v);
			}

			return v;
		}

		// =========================================================
		// ✅ CÓDIGOS (TOKEN + ABREVIATURAS)
		// =========================================================

		if ((tokens.contains("codigo") && tokens.contains("empresa")) || lower.contains("codemp")) {
			return "0108";
		}

		if ((tokens.contains("codigo") && tokens.contains("producto")) || lower.contains("codprod")) {
			return "02";
		}

		if ((tokens.contains("codigo") && tokens.contains("pais")) || lower.contains("codpais")) {
			return "VE";
		}

		if (tokens.contains("razon") || lower.contains("razon")) {
			return "01";
		}

		// ✅ CÓDIGO GENÉRICO
		if (tokens.contains("codigo") || lower.startsWith("codigo")) {
			return "0001";
		}

		// =========================================================
		// ✅ FINANCIERO
		// =========================================================

		if (tokens.contains("monto") || tokens.contains("mto") || lower.contains("mto")) {
			return 1500.50;
		}

		if (tokens.contains("tasa") || lower.contains("tasa")) {
			return 36.75;
		}

		return null;
	}

	private static void loadAbbreviations() {

		try (var is = OpenApiGeneratorService.class.getClassLoader().getResourceAsStream("abbreviations.txt")) {

			if (is == null) {
				throw new RuntimeException("❌ No se encontró abbreviations.txt en resources");
			}

			new java.io.BufferedReader(new java.io.InputStreamReader(is)).lines().map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(line -> {

						String[] parts = line.split("=");

						if (parts.length == 2) {
							ABBREV_MAP.put(parts[0].toLowerCase(), parts[1].toLowerCase());
						}
					});

			System.out.println("✅ Abreviaturas cargadas: " + ABBREV_MAP.size());

		} catch (Exception e) {
			throw new RuntimeException("Error cargando abbreviations.txt", e);
		}
	}

	private Object resolveValueByType(String type) {

		if (type.equals("Boolean") || type.equals("boolean"))
			return true;

		if (type.equals("Integer") || type.equals("int"))
			return 1;

		if (type.equals("Long") || type.equals("long"))
			return 1L;

		if (type.equals("Double") || type.equals("double"))
			return 100.5;

		return "string";
	}

	private List<String> tokenize(String name) {

		if (name == null)
			return List.of();

		// ✅ 1. NORMALIZAR (usa ABBREV_MAP)
		String normalized = normalizeName(name);

		// ✅ 2. separar camelCase
		String withSpaces = normalized.replaceAll("([a-z])([A-Z])", "$1 $2");

		// ✅ 3. split base
		List<String> baseTokens = Arrays.stream(withSpaces.toLowerCase().split("[^a-z0-9]+")).filter(p -> !p.isBlank())
				.collect(Collectors.toList());

		// ✅ 4. EXPANSIÓN SEMÁNTICA (🔥 CLAVE)
		List<String> expanded = new ArrayList<>(baseTokens);

		for (String token : baseTokens) {

			// ✅ expansión por abreviaturas
			if (token.contains("cod"))
				expanded.add("codigo");
			if (token.contains("prod"))
				expanded.add("producto");
			if (token.contains("emp"))
				expanded.add("empresa");
			if (token.contains("pais"))
				expanded.add("pais");
			if (token.contains("mon"))
				expanded.add("moneda");
			if (token.contains("ident"))
				expanded.add("identificacion");
			if (token.contains("pers"))
				expanded.add("persona");
			if (token.contains("raz"))
				expanded.add("razon");

			// ✅ expansión directa
			if (token.contains("codigo"))
				expanded.add("codigo");
			if (token.contains("producto"))
				expanded.add("producto");
			if (token.contains("empresa"))
				expanded.add("empresa");
			if (token.contains("moneda"))
				expanded.add("moneda");
			if (token.contains("identificacion"))
				expanded.add("identificacion");
		}

		return expanded;
	}

}