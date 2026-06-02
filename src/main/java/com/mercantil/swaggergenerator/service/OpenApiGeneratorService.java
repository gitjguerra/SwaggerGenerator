package com.mercantil.swaggergenerator.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.component.ClassIndexer;
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

	@Autowired
	private ClassIndexer classIndexer;

	// ✅ EXAMPLES MAPS
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ IGNORANDO CLASES DE API-COMMONS
	private static final List<String> IGNORED_TYPES = List.of("ConstructorRequired", "BeansZOS");

	// ✅ cache de endpoints por nombre
	private Map<String, String> backendServiceMap = new LinkedHashMap<>();

	// Object mapper create
	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	// Buscar CONSTANTS
	@SuppressWarnings("unused")
	private List<String> currentBeansPath;

	// ✅ =========================
	// ✅ GENERADOR PRINCIPAL
	// ✅ =========================
	public OpenApiDoc generate(ServiceItem service) {

		System.out.println("\n==============================");
		System.out.println("🚀 Generando servicio: " + service.getName());
		System.out.println("📁 BeansPath: " + service.getBeansPath());
		System.out.println("📁 ControllersPath: " + service.getControllersPath());
		System.out.println("==============================\n");

		OpenApiDoc doc = new OpenApiDoc();

		schemaMap = new LinkedHashMap<>();
		schemaBuilder.setSchemaMap(schemaMap);

		exampleMap = new LinkedHashMap<>();
		exampleGenerator.setSchemaMap(schemaMap);

		backendServiceMap = new LinkedHashMap<>();

		// ✅ ASIGNAR RUTA ACTUAL
		this.currentBeansPath = service.getBeansPath();

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

		System.out.println("📦 Bean files encontrados: " + beanFiles.size());

		// 🔥 PRIMERO INDEXAR TODO
		indexAllClasses(beanFiles);

		// ✅ LUEGO PROCESAR
		beanFiles.forEach(f -> processBeanFile(f, doc, service.getBasePackage()));

		// ✅ 3. CONTROLLERS
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());

		System.out.println("🎯 Controllers encontrados: " + controllerFiles.size());

		controllerFiles.forEach(f -> processControllerFile(f, doc));

		doc.components.put("schemas", schemaMap);

		System.out.println("✅ Total endpoints generados: " + doc.paths.size());

		return doc;
	}

	// ✅ =========================
	// ✅ PROCESAR CONTROLLERS
	// ✅ =========================
	private void processControllerFile(File file, OpenApiDoc doc) {

		if (!file.getAbsolutePath().toLowerCase().contains("controller")) {
			return;
		}

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

				boolean hasEndpoint = clazz.getMethods().stream().anyMatch(m -> httpMethodUtil.detect(m) != null);

				if (!hasEndpoint)
					return;

				System.out.println("✅ Controller detectado: " + clazz.getNameAsString());

				String controllerName = clazz.getNameAsString();

				String tag = controllerName.replace("Controller", "").replaceAll("([a-z])([A-Z])", "$1 $2");

				if (doc.tags.stream().noneMatch(t -> t.get("name").equals(tag))) {
					doc.tags.add(Map.of("name", tag, "description", "Operaciones " + tag));
				}

				String basePath = extractClassMapping(clazz);

				clazz.getMethods().forEach(method -> processMethod(method, doc, tag, basePath));
			});

		} catch (Exception e) {
			System.out.println("❌ Error procesando controller: " + file.getAbsolutePath());
			e.printStackTrace();
		}
	}

	// ✅ =========================
	// ✅ PROCESAR MÉTODOS
	// ✅ =========================
	private void processMethod(MethodDeclaration method, OpenApiDoc doc, String tag, String basePath) {

		Map<String, String> httpMapping = httpMethodUtil.detect(method);

		String httpMethod;
		String path;

		if (httpMapping == null) {

			String methodName = method.getNameAsString();
			String fallbackPath = methodName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();

			httpMethod = "post";
			path = "/" + fallbackPath;

		} else {
			httpMethod = httpMapping.get("method");
			path = httpMapping.get("path");
		}

		String fullPath = ("/" + (basePath == null ? "" : basePath) + "/" + (path == null ? "" : path))
				.replaceAll("//+", "/");

		System.out.println("🔹 Endpoint: " + httpMethod.toUpperCase() + " " + fullPath);

		Map<String, Object> op = new LinkedHashMap<>();
		op.put("tags", List.of(tag));
		op.put("summary", method.getNameAsString());
		op.put("operationId", method.getNameAsString());

		Object request = requestBuilder.build(method, schemaMap, exampleMap, IGNORED_TYPES);
		if (request != null)
			op.put("requestBody", request);

		Object response = responseBuilder.build(method, schemaMap, exampleMap, IGNORED_TYPES);
		if (response != null)
			op.put("responses", response);

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

				boolean allowed = isInBasePackage(file, clazz, basePackage);

				if (!allowed) {
					System.out.println("⛔ IGNORADO: " + clazz.getNameAsString() + " | archivo: " + file.getName());
					return;
				}

				String className = clazz.getNameAsString();

				if (IGNORED_TYPES.contains(className)) {
					return;
				}

				System.out.println("✅ Bean detectado: " + className);

				Map<String, Object> schema = schemaBuilder.build(clazz);

				schemaMap.putIfAbsent(className, schema);

				exampleMap.put(className, exampleGenerator.buildExampleFromType(className));
			});

		} catch (Exception e) {
			System.out.println("❌ Error parseando bean: " + file.getAbsolutePath());
			e.printStackTrace();
		}
	}

	// ✅ =========================
	// ✅ UTILIDADES
	// ✅ =========================
	
	// ✅ PARA LISTA
	private List<File> findJavaFiles(List<String> roots) {

		Set<String> seen = new HashSet<>();
		List<File> files = new ArrayList<>();

		if (roots != null) {
			for (String root : roots) {
				for (File f : findJavaFilesRecursive(root)) {
					if (seen.add(f.getAbsolutePath())) {
						files.add(f);
					}
				}
			}
		}

		return files;
	}

	// ✅ PARA STRING
	private List<File> findJavaFiles(String root) {
		return findJavaFilesRecursive(root);
	}

	private List<File> findJavaFilesRecursive(String root) {

		List<File> files = new ArrayList<>();

		File dir = new File(root);

		if (!dir.exists()) {
			System.out.println("❌ Ruta NO existe: " + root);
			return files;
		}

		File[] filesArray = dir.listFiles();
		if (filesArray == null)
			return files;

		for (File f : filesArray) {

			if (f.isDirectory()) {
				files.addAll(findJavaFilesRecursive(f.getAbsolutePath()));
			} else if (f.getName().endsWith(".java")) {
				files.add(f);
			}
		}

		return files;
	}

	private String extractClassMapping(ClassOrInterfaceDeclaration clazz) {

		return clazz.getAnnotationByName("RequestMapping").map(annotation -> {

			if (annotation.isSingleMemberAnnotationExpr()) {
				var value = annotation.asSingleMemberAnnotationExpr().getMemberValue();

				if (value.isStringLiteralExpr()) {
					return value.asStringLiteralExpr().asString();
				}
			}

			if (annotation.isNormalAnnotationExpr()) {

				for (var pair : annotation.asNormalAnnotationExpr().getPairs()) {

					if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {

						if (pair.getValue().isStringLiteralExpr()) {
							return pair.getValue().asStringLiteralExpr().asString();
						}
					}
				}
			}

			return "";
		}).orElse("");
	}

	// ✅ =========================
	// ✅ FILTRO ROBUSTO
	// ✅ =========================
	private boolean isInBasePackage(File file, ClassOrInterfaceDeclaration clazz, String basePackage) {

		if (basePackage == null || basePackage.isBlank())
			return true;

		boolean packageMatch = clazz.findCompilationUnit().flatMap(c -> c.getPackageDeclaration())
				.map(p -> p.getNameAsString().startsWith(basePackage)).orElse(false);

		if (packageMatch)
			return true;

		String normalizedPath = file.getAbsolutePath().replace("\\", ".").toLowerCase();

		String normalizedPkg = basePackage.replace(".", "").toLowerCase();

		return normalizedPath.contains(normalizedPkg);
	}

	// ✅ =========================
	// ✅ BASE SCHEMAS
	// ✅ =========================
	private void registerBaseSchemas() {

		// =========================================================
		// ✅ HEADER ENTRADA
		// =========================================================
		System.out.println("📌 Registrando schema base: HeaderEntrada");

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

		// ✅ Example
		Map<String, Object> exampleEntrada = new LinkedHashMap<>();
		exampleEntrada.put("identificadorUnicoGlobal", "string");
		exampleEntrada.put("identificacionCanal", "0006");
		exampleEntrada.put("identificacionSubCanal", "01");
		exampleEntrada.put("siglaAplicacion", "ABC");
		exampleEntrada.put("identificacionUsuario", "user");
		exampleEntrada.put("direccionIpConsumidor", "127.0.0.1");
		exampleEntrada.put("direccionIpCliente", "127.0.0.1");
		exampleEntrada.put("fechaEnvioMensaje", "20260101");
		exampleEntrada.put("horaEnvioMensaje", "120000");
		exampleEntrada.put("atributoPagineo", "N");
		exampleEntrada.put("claveBusqueda", "clave");
		exampleEntrada.put("cantidadRegistros", 0);

		headerEntrada.put("example", exampleEntrada);

		schemaMap.putIfAbsent("HeaderEntrada", headerEntrada);

		// =========================================================
		// ✅ HEADER SALIDA
		// =========================================================
		System.out.println("📌 Registrando schema base: HeaderSalida");

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
		Map<String, Object> exampleSalida = new LinkedHashMap<>();
		exampleSalida.put("tipoMensaje", "F");
		exampleSalida.put("mensajeProgramadorSistema", "Procesado correctamente");
		exampleSalida.put("codigoMensajeProgramador", "0000");
		exampleSalida.put("mensajeUsuario", "Operación exitosa");
		exampleSalida.put("codigoMensajeUsuario", "0000");
		exampleSalida.put("fechaSalidaMensaje", "20260101");
		exampleSalida.put("horaSalidaMensaje", "120000");

		headerSalida.put("example", exampleSalida);

		schemaMap.putIfAbsent("HeaderSalida", headerSalida);

		// =========================================================
		// ✅ CLIENT RESPONSE (HARDCODE ENTERPRISE)
		// =========================================================
		System.out.println("📌 Registrando schema base: ClientResponse");

		Map<String, Object> clientResponse = new LinkedHashMap<>();
		clientResponse.put("type", "object");

		Map<String, Object> crProps = new LinkedHashMap<>();

		crProps.put("code", Map.of("type", "integer"));
		crProps.put("message", Map.of("type", "string"));

		// 🔥 headerSalida ya existe como schema base
		crProps.put("headerSalida", Map.of("$ref", "#/components/schemas/HeaderSalida"));

		// 🔥 body dinámico
		crProps.put("bodySalida", Map.of("type", "object", "description", "Contenido dinámico de la respuesta"));

		clientResponse.put("properties", crProps);

		schemaMap.putIfAbsent("ClientResponse", clientResponse);

		// =========================================================
		// ✅ CLIENT
		// =========================================================
		System.out.println("📌 Registrando schema base: Client");

		Map<String, Object> client = new LinkedHashMap<>();
		client.put("type", "object");

		Map<String, Object> cProps = new LinkedHashMap<>();

		cProps.put("codigoMensajeProgramador", Map.of("type", "integer"));
		cProps.put("mensajeProgramador", Map.of("type", "string"));

		client.put("properties", cProps);

		schemaMap.putIfAbsent("Client", client);

		System.out.println("✅ Base schemas registrados");
	}

	// =========================================================
	// ✅ Generar, guardar y devolver documento
	// =========================================================
	public OpenApiDoc generateAndSaveReturningDoc(ServiceItem service, String outputDir) {

		System.out.println("\n📦 Generando y guardando (con retorno) servicio: " + service.getName());

		OpenApiDoc doc = generate(service);

		saveDoc(doc, service.getName(), outputDir);

		return doc;
	}

	// =========================================================
	// ✅ Guardar archivo JSON
	// =========================================================
	private void saveDoc(OpenApiDoc doc, String serviceName, String outputDir) {

		try {
			File dir = new File(outputDir);

			if (!dir.exists()) {
				dir.mkdirs();
				System.out.println("📁 Directorio creado: " + outputDir);
			}

			File file = new File(dir, serviceName + ".json");

			mapper.writeValue(file, doc);

			System.out.println("✅ Archivo generado: " + file.getAbsolutePath());

		} catch (Exception e) {
			System.out.println("❌ Error guardando archivo de: " + serviceName);
			throw new RuntimeException(e);
		}
	}

	// ✅ =========================
	private void loadBackendServices(String configPath) {

		try {

			File file = new File(configPath + "/restservices.xml");

			if (!file.exists()) {
				System.out.println("⚠️ XML no encontrado en: " + configPath);
				return;
			}

			var db = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			var builder = db.newDocumentBuilder();
			var doc = builder.parse(file);

			var nodes = doc.getElementsByTagName("service");

			for (int i = 0; i < nodes.getLength(); i++) {

				org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);

				backendServiceMap.put(el.getAttribute("name").toLowerCase(), el.getAttribute("endpoint"));
			}

			System.out.println("✅ Backend services cargados: " + backendServiceMap.size());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String resolveConfigPath(ServiceItem service) {
		return "C:/BM_HOME/appl/api-" + service.getName() + "/config";
	}

	private void indexAllClasses(List<File> files) {

		System.out.println("🔍 Indexando clases...");

		AtomicInteger count = new AtomicInteger(0);

		for (File file : files) {

			try {
				CompilationUnit cu = StaticJavaParser.parse(file);

				cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

					classIndexer.register(clazz);
					count.incrementAndGet();
				});

			} catch (Exception e) {
				System.out.println("⚠️ Error indexando: " + file.getName());
			}
		}

		System.out.println("✅ Clases indexadas: " + count.get());
	}

}