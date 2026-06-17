package com.mercantil.swaggergenerator.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import com.mercantil.swaggergenerator.config.ServiceLoader;
import com.mercantil.swaggergenerator.exception.ServiceException;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;
import com.mercantil.swaggergenerator.util.HttpMethodUtil;

@Service
public class OpenApiGeneratorService {

	private static final String URL_SEPARATOR = "/";
	private static final String PROPERTIES = "properties";
	private static final String STRING = "string";

	private final HttpMethodUtil httpMethodUtil;
	private final SchemaBuilder schemaBuilder;
	private final ExampleGenerator exampleGenerator;
	private final RequestBuilder requestBuilder;
	private final ResponseBuilder responseBuilder;
	private final ClassIndexer classIndexer;

	public OpenApiGeneratorService(HttpMethodUtil httpMethodUtil, SchemaBuilder schemaBuilder,
			ExampleGenerator exampleGenerator, RequestBuilder requestBuilder, ResponseBuilder responseBuilder,
			ClassIndexer classIndexer) {

		this.httpMethodUtil = httpMethodUtil;
		this.schemaBuilder = schemaBuilder;
		this.exampleGenerator = exampleGenerator;
		this.requestBuilder = requestBuilder;
		this.responseBuilder = responseBuilder;
		this.classIndexer = classIndexer;
	}

	private String currentServiceName;

	private static final Logger log = LogManager.getLogger(OpenApiGeneratorService.class);

	// =========================================================
	// ✅ SCHEMAS / EXAMPLES
	// =========================================================
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// =========================================================
	// ✅ IGNORADOS
	// =========================================================
	private static final List<String> IGNORED_TYPES = List.of(

			"ConstructorRequired", "BeansZOS", "SendRequestRest", "CentraSite", "RestConstants");

	// =========================================================
	// ✅ ENDPOINT CACHE
	// =========================================================
	private Map<String, String> backendServiceMap = new LinkedHashMap<>();

	// =========================================================
	// ✅ JSON MAPPER
	// =========================================================
	private final ObjectMapper mapper =

			new ObjectMapper()

					.enable(SerializationFeature.INDENT_OUTPUT);

	@SuppressWarnings("unused")
	private List<String> currentBeansPath;

	// =========================================================
	// ✅ GENERADOR PRINCIPAL
	// =========================================================
	public OpenApiDoc generate(ServiceItem service) {

		log.info("\n==============================");
		log.info("Generando servicio: {}", service.getName());
		log.info("BeansPath: {}", service.getBeansPath());
		log.info("ControllersPath: {}", service.getControllersPath());
		log.info("==============================\n");

		OpenApiDoc doc = new OpenApiDoc();

		schemaMap = new LinkedHashMap<>();

		schemaBuilder.setSchemaMap(schemaMap);

		exampleMap = new LinkedHashMap<>();

		exampleGenerator.setSchemaMap(schemaMap);

		backendServiceMap = new LinkedHashMap<>();

		this.currentBeansPath = service.getBeansPath();
		this.currentServiceName = service.getName().toLowerCase().trim();

		doc.setSecurity(List.of(Map.of("bearerAuth", List.of())));

		doc.getInfo().setTitle("API " + service.getName());

		doc.getServers()
				.add(Map.of("url", (service.getHost() == null ? "" : service.getHost()) + service.getBasePath()));

		// =====================================================
		// ✅ BACKEND SERVICES
		// =====================================================
		String configPath = resolveConfigPath(service);

		loadBackendServices(configPath);

		// =====================================================
		// ✅ BASE SCHEMAS
		// =====================================================
		registerBaseSchemas();

		// =====================================================
		// ✅ BEANS
		// =====================================================
		List<File> beanFiles = findJavaFiles(service.getBeansPath());

		log.info("Bean files encontrados: {}", beanFiles.size());

		// ✅ INDEXAR PRIMERO
		indexAllClasses(beanFiles);

		// =====================================================
		// ✅ PRIMERA PASADA
		// SOLO SCHEMAS
		// =====================================================
		beanFiles.forEach(f ->

		processBeanSchemas(f, service.getBasePackage()));

		// =====================================================
		// ✅ CONTROLLERS
		// =====================================================
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());

		log.info("Controllers encontrados: {}", controllerFiles.size());

		controllerFiles.forEach(f ->

		processControllerFile(f, doc));

		// =====================================================
		// ✅ LIMPIAR
		// =====================================================
		removeEmptySchemas();

		doc.getComponents().put("schemas", schemaMap);

		log.info("Total endpoints generados: {}", doc.getPaths().size());

		return doc;
	}

	// =========================================================
	// ✅ PROCESS CONTROLLER
	// =========================================================
	private void processControllerFile(File file, OpenApiDoc doc) {

		if (!file.getAbsolutePath().toLowerCase()

				.contains("controller")) {

			return;
		}

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class)

					.forEach(clazz -> {

						boolean hasEndpoint =

								clazz.getMethods()

										.stream()

										.anyMatch(m ->

										httpMethodUtil.detect(m) != null);

						if (!hasEndpoint) {

							return;
						}

						log.info("Controller detectado: {}", clazz.getNameAsString());

						String controllerName = clazz.getNameAsString();

						String tag =

								controllerName

										.replace("Controller", "")

										.replaceAll("([a-z])([A-Z])", "$1 $2");

						if (doc.getTags().stream()

								.noneMatch(t ->

								t.get("name").equals(tag))) {

							doc.getTags().add(

									Map.of("name", tag, "description", "Operaciones " + tag));
						}

						String basePath = extractClassMapping(clazz);

						clazz.getMethods().forEach(method ->

					processMethod(method, doc, tag, basePath));
					});

		} catch (Exception e) {

			log.info("Error procesando controller: {}", file.getAbsolutePath());

			e.printStackTrace();
		}
	}

	// =========================================================
	// ✅ PROCESS METHOD
	// =========================================================
	private void processMethod(MethodDeclaration method, OpenApiDoc doc, String tag, String basePath) {

		Map<String, String> httpMapping = httpMethodUtil.detect(method);

		String httpMethod;
		String path;

		if (httpMapping == null) {

			String methodName = method.getNameAsString();

			String fallbackPath =

					methodName.replaceAll("([a-z])([A-Z])", "$1-$2")

							.toLowerCase();

			httpMethod = "post";

			path = URL_SEPARATOR + fallbackPath;

		} else {

			httpMethod = httpMapping.get("method");

			path = httpMapping.get("path");
		}

		String rawPath = URL_SEPARATOR + (basePath == null ? "" : basePath) + URL_SEPARATOR
				+ (path == null ? "" : path);

		// ✅ normalizar path base
		rawPath = normalizePath(rawPath);

		// ✅ obtener prefijo del microservicio
		String servicePrefix = resolveServicePrefix();

		// ✅ construir path final correcto
		String fullPath = normalizePath(servicePrefix + rawPath);

		if (fullPath.length() > 1 && fullPath.endsWith(URL_SEPARATOR)) {

			fullPath = fullPath.substring(0, fullPath.length() - 1);
		}

		Map<String, Object> op = new LinkedHashMap<>();

		op.put("tags", List.of(tag));

		op.put("summary", method.getNameAsString());

		String operationId = generateOperationId(tag, method.getNameAsString());

		op.put("operationId", operationId);

		exampleGenerator.setCurrentApiPath(fullPath);
		Object request = requestBuilder.build(fullPath, method, schemaMap, exampleMap, IGNORED_TYPES);

		if (request != null) {

			op.put("requestBody", request);
		}

		Object response = responseBuilder.build(fullPath, method, schemaMap, exampleMap, IGNORED_TYPES);

		if (response != null) {

			op.put("responses", response);
		}

		Map<String, Object> pathItem =

				(Map<String, Object>) doc.getPaths()

						.getOrDefault(fullPath, new LinkedHashMap<>());

		pathItem.put(httpMethod, op);

		doc.getPaths().put(fullPath, pathItem);
	}

	// =========================================================
	// ✅ PROCESS SCHEMAS
	// =========================================================
	private void processBeanSchemas(File file, String basePackage) {

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class)

					.forEach(clazz -> {

						boolean allowed = isInBasePackage(file, clazz, basePackage);

						if (!allowed) {

							return;
						}

						String className = clazz.getNameAsString();

						if (IGNORED_TYPES.contains(className)) {

							return;
						}

						Map<String, Object> schema = schemaBuilder.build(clazz);

						Object propsObj = schema.get(PROPERTIES);

						boolean hasProperties =

								propsObj instanceof Map

										&& !((Map<?, ?>) propsObj).isEmpty();

						if (hasProperties) {

							schemaMap.putIfAbsent(className, schema);
						}
					});

		} catch (Exception e) {

			log.error("Error parseando schema: {}", file.getAbsolutePath());

			e.printStackTrace();
		}
	}

	// =========================================================
	// ✅ FIND JAVA FILES
	// =========================================================
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

	private List<File> findJavaFiles(String root) {

		return findJavaFilesRecursive(root);
	}

	private List<File> findJavaFilesRecursive(String root) {

		List<File> files = new ArrayList<>();

		File dir = new File(root);

		if (!dir.exists()) {

			log.info("Ruta NO existe: {}", root);

			return files;
		}

		File[] filesArray = dir.listFiles();

		if (filesArray == null) {

			return files;
		}

		for (File f : filesArray) {

			if (f.isDirectory()) {

				files.addAll(findJavaFilesRecursive(f.getAbsolutePath()));
			}

			else if (f.getName().endsWith(".java")) {

				files.add(f);
			}
		}

		return files;
	}

	// =========================================================
	// ✅ EXTRACT CLASS MAPPING
	// =========================================================
	private String extractClassMapping(ClassOrInterfaceDeclaration clazz) {

		return clazz.getAnnotationByName("RequestMapping")

				.map(annotation -> {

					if (annotation.isSingleMemberAnnotationExpr()) {

						var value =

								annotation.asSingleMemberAnnotationExpr()

										.getMemberValue();

						if (value.isStringLiteralExpr()) {

							return value.asStringLiteralExpr().asString();
						}
					}

					if (annotation.isNormalAnnotationExpr()) {

						for (var pair :

				annotation.asNormalAnnotationExpr()

						.getPairs()) {

							if (pair.getNameAsString().equals("value")

									|| pair.getNameAsString().equals("path")) {

								if (pair.getValue().isStringLiteralExpr()) {

									return pair.getValue()

											.asStringLiteralExpr()

											.asString();
								}
							}
						}
					}

					return "";
				})

				.orElse("");
	}

	// =========================================================
	// ✅ FILTER PACKAGE
	// =========================================================
	private boolean isInBasePackage(File file, ClassOrInterfaceDeclaration clazz, String basePackage) {

		if (basePackage == null || basePackage.isBlank()) {

			return true;
		}

		boolean packageMatch =

				clazz.findCompilationUnit()

						.flatMap(CompilationUnit::getPackageDeclaration)

						.map(p ->

						p.getNameAsString()

								.startsWith(basePackage))

						.orElse(false);

		if (packageMatch) {

			return true;
		}

		String normalizedPath =

				file.getAbsolutePath()

						.replace("\\", ".")

						.toLowerCase();

		String normalizedPkg =

				basePackage.replace(".", "")

						.toLowerCase();

		return normalizedPath.contains(normalizedPkg);
	}

	// =========================================================
	// ✅ BASE SCHEMAS
	// =========================================================
	private void registerBaseSchemas() {

		Map<String, Object> headerEntrada = new LinkedHashMap<>();

		headerEntrada.put("type", "object");

		Map<String, Object> propsEntrada = new LinkedHashMap<>();

		propsEntrada.put("identificadorUnicoGlobal", Map.of("type", STRING, "format", "uuid"));

		propsEntrada.put("identificacionCanal", Map.of("type", STRING));

		propsEntrada.put("identificacionSubCanal", Map.of("type", STRING));

		propsEntrada.put("siglaAplicacion", Map.of("type", STRING));

		propsEntrada.put("cantidadRegistros", Map.of("type", "integer"));

		headerEntrada.put(PROPERTIES, propsEntrada);

		schemaMap.putIfAbsent("HeaderEntrada", headerEntrada);

		Map<String, Object> headerSalida = new LinkedHashMap<>();

		headerSalida.put("type", "object");

		Map<String, Object> propsSalida = new LinkedHashMap<>();

		propsSalida.put("tipoMensaje", Map.of("type", STRING));

		headerSalida.put(PROPERTIES, propsSalida);

		schemaMap.putIfAbsent("HeaderSalida", headerSalida);
	}

	// =========================================================
	// ✅ SAVE JSON
	// =========================================================
	private void saveDoc(OpenApiDoc doc, String serviceName, String outputDir) {

		try {

			File dir = new File(outputDir);

			if (!dir.exists()) {

				dir.mkdirs();

				log.info("Directorio creado: {}", outputDir);
			}

			File file = new File(dir, serviceName + ".json");

			mapper.writeValue(file, doc);

			log.info("Archivo generado: {}", file.getAbsolutePath());

		} catch (Exception e) {
			throw new ServiceException("Error guardando archivo: {}", e);
		}
	}

	// =========================================================
	// ✅ BACKEND SERVICES
	// =========================================================
	private void loadBackendServices(String configPath) {

		try {

			File file = new File(configPath + "/restservices.xml");

			if (!file.exists()) {

				log.info("XML no encontrado: {}", configPath);

				return;
			}

			javax.xml.parsers.DocumentBuilderFactory db = javax.xml.parsers.DocumentBuilderFactory.newInstance();

			// ✅ Protección XXE
			db.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			db.setFeature("http://xml.org/sax/features/external-general-entities", false);
			db.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			db.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

			db.setXIncludeAware(false);
			db.setExpandEntityReferences(false);

			var builder = db.newDocumentBuilder();

			var doc = builder.parse(file);

			var nodes = doc.getElementsByTagName("service");

			for (int i = 0; i < nodes.getLength(); i++) {

				org.w3c.dom.Element el =

						(org.w3c.dom.Element) nodes.item(i);

				backendServiceMap.put(

						el.getAttribute("name").toLowerCase(),

						el.getAttribute("endpoint"));
			}

			log.info("Backend services cargados: {}", backendServiceMap.size());

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private String resolveConfigPath(ServiceItem service) {

		String bmHome = System.getenv("BM_HOME");

		if (bmHome == null || bmHome.isBlank()) {
			throw new ServiceException("BM_HOME no está configurado en el entorno");
		}

		String serviceName = service.getName();

		if (serviceName == null || serviceName.isBlank()) {
			throw new ServiceException("Nombre del servicio inválido");
		}

		return bmHome + File.separator + "appl" + File.separator + serviceName + File.separator + "config";
	}

	// =========================================================
	// ✅ CLASS INDEX
	// =========================================================
	private void indexAllClasses(List<File> files) {

		log.info("Indexando clases...");

		AtomicInteger count = new AtomicInteger(0);

		for (File file : files) {

			try {

				CompilationUnit cu = StaticJavaParser.parse(file);

				cu.findAll(ClassOrInterfaceDeclaration.class)

						.forEach(clazz -> {

							classIndexer.register(clazz);

							count.incrementAndGet();
						});

			} catch (Exception e) {
				log.info("Error indexando: {}", file.getName());
			}
		}

		log.info("Clases indexadas: {}", count.get());
	}

	// =========================================================
	// ✅ REMOVE EMPTY
	// =========================================================
	private void removeEmptySchemas() {

		schemaMap.entrySet()

				.removeIf(entry -> {

					Map<String, Object> schema = entry.getValue();

					Object props = schema.get(PROPERTIES);

					boolean emptyProps = props instanceof Map && ((Map<?, ?>) props).isEmpty();

					return emptyProps;
				});
	}

	// =========================================================
	// ✅ UNIQUE OPERATION ID
	// =========================================================
	private String generateOperationId(String tag, String methodName) {

		if (methodName == null || methodName.isBlank()) {

			return "operation";
		}

		String cleanTag =

				tag == null ? "" : tag.replaceAll("\\s+", "");

		if (cleanTag.isBlank()) {

			return methodName;
		}

		return Character.toLowerCase(cleanTag.charAt(0))

				+ cleanTag.substring(1)

				+ Character.toUpperCase(methodName.charAt(0))

				+ methodName.substring(1);
	}

	private String resolveServicePrefix() {

		if (currentServiceName == null || currentServiceName.isBlank()) {
			return "";
		}

		if (!currentServiceName.startsWith(URL_SEPARATOR)) {
			return URL_SEPARATOR + currentServiceName;
		}

		return currentServiceName;
	}

	private String normalizePath(String path) {

		if (path == null || path.isBlank()) {
			return URL_SEPARATOR;
		}

		path = path.trim();

		path = path.replaceAll("//+", URL_SEPARATOR);

		if (!path.startsWith(URL_SEPARATOR)) {
			path = URL_SEPARATOR + path;
		}

		if (path.length() > 1 && path.endsWith(URL_SEPARATOR)) {
			path = path.substring(0, path.length() - 1);
		}

		return path;
	}

	public void generateAll() {

		List<ServiceItem> services = ServiceLoader.load();

		String outputDir = System.getProperty("pathOutput");

		if (outputDir == null || outputDir.isBlank()) {
			throw new ServiceException("pathOutput no configurado");
		}

		log.info("Generando {} servicios...", services.size());

		for (ServiceItem service : services) {

			try {

				OpenApiDoc doc = generate(service);

				saveDoc(doc, service.getName(), outputDir);

			} catch (Exception e) {

				log.info("Error generando servicio: {}", service.getName());
				e.printStackTrace();
			}
		}
	}

}