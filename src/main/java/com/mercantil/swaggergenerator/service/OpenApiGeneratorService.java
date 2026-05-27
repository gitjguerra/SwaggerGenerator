package com.mercantil.swaggergenerator.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.component.ExampleGenerator;
import com.mercantil.swaggergenerator.component.RequestBuilder;
import com.mercantil.swaggergenerator.component.ResponseBuilder;
import com.mercantil.swaggergenerator.component.SchemaBuilder;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;
import com.mercantil.swaggergenerator.util.HttpMethodUtil;

@Service
public class OpenApiGeneratorService {

	// Get http method
	@Autowired
	private HttpMethodUtil httpMethodUtil;

	// Build schemas
	@Autowired
	private SchemaBuilder schemaBuilder;

	// Build examples
	@Autowired
	private ExampleGenerator exampleGenerator;

	// build request
	@Autowired
	private RequestBuilder requestBuilder;

	// build response
	@Autowired
	private ResponseBuilder responseBuilder;

	// ✅ EXAMPLES MAPS
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ IGNORANDO CLASES DE API-COMMONS
	private static final List<String> IGNORED_TYPES = List.of("ConstructorRequired", "BeansZOS");

	// ✅ cache de endpoints por nombre
	private Map<String, String> backendServiceMap = new LinkedHashMap<>();

	// Object mapper create
	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	// ✅ =========================
	// ✅ GENERADOR PRINCIPAL
	// ✅ =========================
	public OpenApiDoc generate(ServiceItem service) {

		OpenApiDoc doc = new OpenApiDoc();

		schemaMap = new LinkedHashMap<>();
		schemaBuilder.setSchemaMap(schemaMap);
		exampleMap = new LinkedHashMap<>();
		exampleGenerator.setSchemaMap(schemaMap);
		backendServiceMap = new LinkedHashMap<>();

		doc.security = List.of(Map.of("bearerAuth", List.of()));
		doc.info.title = "API " + service.getName();

		doc.servers.add(Map.of("url", (service.getHost() == null ? "" : service.getHost()) + service.getBasePath()));

		// ✅ PRIMERO CARGAR SERVICIOS DEL BACKEND
		String configPath = resolveConfigPath(service);
		loadBackendServices(configPath);

		// ✅ 1. BASE SCHEMAS
		registerBaseSchemas();

		// ✅ 2. BEANS
		List<File> beanFiles = findJavaFiles(service.getBeansPath());
		beanFiles.forEach(f -> processBeanFile(f, doc, service.getBasePackage()));

		// ✅ 3. CONTROLLERS
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());
		controllerFiles.forEach(f -> processControllerFile(f, doc));

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

		Map<String, String> httpMapping = httpMethodUtil.detect(method);
		if (httpMapping == null)
			return;

		String httpMethod = httpMapping.get("method");
		String path = httpMapping.get("path");

		String fullPath = ("/" + (basePath == null ? "" : basePath) + "/" + (path == null ? "" : path))
				.replaceAll("//+", "/");

		Map<String, Object> op = new LinkedHashMap<>();
		op.put("tags", List.of(tag));
		op.put("summary", method.getNameAsString());
		op.put("operationId", method.getNameAsString());

		String serviceName = extractServiceName(method);
		serviceName = serviceName != null ? serviceName.toLowerCase().trim() : null;

		String backendUrl = findBackendUrl(serviceName);

		StringBuilder desc = new StringBuilder();

		if (backendUrl != null) {
			String coreProgram = extractCoreProgram(backendUrl);
			desc.append("✅ Backend: ").append(backendUrl);

			if (coreProgram != null) {
				desc.append("\n\n🔗 Core: ").append(coreProgram);
			}
		} else {
			desc.append("❌ Backend no encontrado");
		}

		desc.append("\n\n🔗 Service: ").append(serviceName);

		op.put("description", desc.toString());

		// ✅ REQUEST
		op.put("requestBody", requestBuilder.build(method, schemaMap, exampleMap, IGNORED_TYPES));

		// ✅ RESPONSE
		op.put("responses", responseBuilder.build(method, schemaMap, exampleMap, IGNORED_TYPES));

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

				Map<String, Object> schema = schemaBuilder.build(clazz);

				schemaMap.putIfAbsent(className, schema);

				exampleMap.put(className, exampleGenerator.buildExampleFromType(className));
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
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

	private String extractClassMapping(ClassOrInterfaceDeclaration clazz) {

		return clazz.getAnnotationByName("RequestMapping")
				.flatMap(a -> a.toString().contains("\"") ? java.util.Optional.of(a.toString().split("\"")[1])
						: java.util.Optional.empty())
				.orElse("");
	}

	private boolean isInBasePackage(ClassOrInterfaceDeclaration clazz, String basePackage) {

		return clazz.findCompilationUnit().flatMap(c -> c.getPackageDeclaration())
				.map(p -> p.getNameAsString().startsWith(basePackage)).orElse(false);
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

	    // ✅ Example (CLAVE para Swagger UI)
	    Map<String, Object> headerEntradaExample = new LinkedHashMap<>();
	    headerEntradaExample.put("identificadorUnicoGlobal", "string");
	    headerEntradaExample.put("identificacionCanal", "0006");
	    headerEntradaExample.put("identificacionSubCanal", "01");
	    headerEntradaExample.put("siglaAplicacion", "ABC");
	    headerEntradaExample.put("identificacionUsuario", "user");
	    headerEntradaExample.put("direccionIpConsumidor", "127.0.0.1");
	    headerEntradaExample.put("direccionIpCliente", "127.0.0.1");
	    headerEntradaExample.put("fechaEnvioMensaje", "20260101");
	    headerEntradaExample.put("horaEnvioMensaje", "120000");
	    headerEntradaExample.put("atributoPagineo", "N");
	    headerEntradaExample.put("claveBusqueda", "clave");
	    headerEntradaExample.put("cantidadRegistros", 0);

	    headerEntrada.put("example", headerEntradaExample);

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
	    propsSalida.put("mensajeUsuario", Map.of("type", "string"));
	    propsSalida.put("codigoMensajeUsuario", Map.of("type", "string"));
	    propsSalida.put("fechaSalidaMensaje", Map.of("type", "string"));
	    propsSalida.put("horaSalidaMensaje", Map.of("type", "string"));

	    headerSalida.put("properties", propsSalida);

	    // ✅ Example
	    Map<String, Object> headerSalidaExample = new LinkedHashMap<>();
	    headerSalidaExample.put("tipoMensaje", "F");
	    headerSalidaExample.put("mensajeProgramadorSistema", "Procesado correctamente");
	    headerSalidaExample.put("codigoMensajeProgramador", "0000");
	    headerSalidaExample.put("mensajeUsuario", "Operación exitosa");
	    headerSalidaExample.put("codigoMensajeUsuario", "0000");
	    headerSalidaExample.put("fechaSalidaMensaje", "20260101");
	    headerSalidaExample.put("horaSalidaMensaje", "120000");

	    headerSalida.put("example", headerSalidaExample);

	    schemaMap.putIfAbsent("HeaderSalida", headerSalida);
	}

	// =========================================================
	// ✅ Generar y guardar
	// =========================================================
	public String generateAndSave(ServiceItem service, String outputDir) {

		OpenApiDoc doc = generate(service);

		saveDoc(doc, service.getName(), outputDir);

		return new File(outputDir, service.getName() + ".json").getAbsolutePath();
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

	private void loadBackendServices(String configPath) {

		try {

			// ✅ posibles nombres de archivo
			List<String> candidates = List.of("restservices.xml", "service.xml", "services.xml");

			File fileFound = null;

			for (String name : candidates) {

				File f = new File(configPath + "/" + name);

				if (f.exists()) {
					fileFound = f;
					break;
				}
			}

			if (fileFound == null) {
				System.out.println("⚠️ No se encontró ningún XML en: " + configPath);
				return;
			}

			var db = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			var builder = db.newDocumentBuilder();
			var doc = builder.parse(fileFound);

			var nodes = doc.getElementsByTagName("service");

			for (int i = 0; i < nodes.getLength(); i++) {

				org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);

				String name = el.getAttribute("name");
				String endpoint = el.getAttribute("endpoint");

				backendServiceMap.put(name.toLowerCase(), endpoint);
			}

			System.out.println("✅ XML cargado: " + fileFound.getName() + " | Servicios: " + backendServiceMap.size());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String extractServiceName(MethodDeclaration method) {

		return method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()

				.filter(call -> call.getNameAsString().equals("setServiceName"))

				.map(call -> {

					if (call.getArguments().isEmpty())
						return null;

					com.github.javaparser.ast.expr.Expression arg = call.getArgument(0);

					try {

						// ✅ CASO 1: STRING DIRECTO
						// setServiceName("jwt-create")
						if (arg.isStringLiteralExpr()) {
							return arg.asStringLiteralExpr().asString();
						}

						// ✅ CASO 2: CONSTANTE
						// SecurityConstanstService.JUMIO_AUTENTICAR.getValue()
						if (arg.isMethodCallExpr()) {

							com.github.javaparser.ast.expr.MethodCallExpr methodCall = arg.asMethodCallExpr();

							// getValue()
							if (methodCall.getNameAsString().equals("getValue")) {

								java.util.Optional<com.github.javaparser.ast.expr.Expression> scope = methodCall
										.getScope();

								if (scope.isPresent() && scope.get().isFieldAccessExpr()) {

									com.github.javaparser.ast.expr.FieldAccessExpr fieldAccess = scope.get()
											.asFieldAccessExpr();

									String constant = fieldAccess.getNameAsString();

									return resolveEnumToValue(constant);
								}
							}
						}

					} catch (Exception ignored) {
					}

					return null;
				})

				.filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
	}

	private String resolveEnumToValue(String enumName) {

		if (enumName == null)
			return null;

		return enumName.toLowerCase().replace("_", "-");
	}

	private String resolveConfigPath(ServiceItem service) {

		// ✅ base fija del banco
		String base = "C:/BM_HOME/appl";

		// ✅ nombre del servicio (simf, tarjeta, etc.)
		String name = service.getName();

		// ✅ regla real de carpeta
		return base + "/api-" + name + "/config";
	}

	private String extractCoreProgram(String url) {

		if (url == null || url.isBlank())
			return null;

		try {
			String lastSegment = url.substring(url.lastIndexOf("/") + 1);

			// registrarBancoCompraDivisa-bocs013z
			if (lastSegment.contains("-")) {
				return lastSegment.substring(lastSegment.lastIndexOf("-") + 1).toUpperCase();
			}

		} catch (Exception ignored) {
		}

		return null;
	}

	private String findBackendUrl(String serviceName) {

		if (serviceName == null)
			return null;

		return backendServiceMap.get(serviceName);
	}

	public OpenApiDoc generateAndSaveReturningDoc(ServiceItem service, String outputDir) {

		OpenApiDoc doc = generate(service);

		saveDoc(doc, service.getName(), outputDir);

		return doc;
	}

	private void saveDoc(OpenApiDoc doc, String serviceName, String outputDir) {

		try {
			File dir = new File(outputDir);
			if (!dir.exists()) {
				dir.mkdirs();
			}

			File file = new File(dir, serviceName + ".json");

			mapper.writeValue(file, doc);

			System.out.println("✅ Archivo generado: " + file.getAbsolutePath());

		} catch (Exception e) {
			throw new RuntimeException("Error guardando archivo para: " + serviceName, e);
		}
	}

}