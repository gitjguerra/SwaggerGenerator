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

	@Autowired
	private HttpMethodUtil httpMethodUtil;

	@Autowired
	private SchemaBuilder schemaBuilder;

	@Autowired
	private ExampleGenerator exampleGenerator;

	@Autowired
	private RequestBuilder requestBuilder;

	@Autowired
	private ResponseBuilder responseBuilder;

	@Autowired
	private ClassIndexer classIndexer;

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

		this.currentBeansPath = service.getBeansPath();

		doc.security = List.of(Map.of("bearerAuth", List.of()));

		doc.info.title = "API " + service.getName();

		doc.servers.add(

				Map.of("url",

						(service.getHost() == null ? "" : service.getHost())

								+ service.getBasePath()));

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

		System.out.println("📦 Bean files encontrados: " + beanFiles.size());

		// ✅ INDEXAR PRIMERO
		indexAllClasses(beanFiles);

		// =====================================================
		// ✅ PRIMERA PASADA
		// SOLO SCHEMAS
		// =====================================================
		beanFiles.forEach(f ->

		processBeanSchemas(f, service.getBasePackage()));

		// =====================================================
		// ✅ SEGUNDA PASADA
		// SOLO EXAMPLES
		// =====================================================
		beanFiles.forEach(f ->

		processBeanExamples(f, service.getBasePackage()));

		// =====================================================
		// ✅ CONTROLLERS
		// =====================================================
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());

		System.out.println("🎯 Controllers encontrados: " + controllerFiles.size());

		controllerFiles.forEach(f ->

		processControllerFile(f, doc));

		// =====================================================
		// ✅ LIMPIAR
		// =====================================================
		removeEmptySchemas();

		doc.components.put("schemas", schemaMap);

		System.out.println("✅ Total endpoints generados: " + doc.paths.size());

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

						System.out.println("✅ Controller detectado: " + clazz.getNameAsString());

						String controllerName = clazz.getNameAsString();

						String tag =

								controllerName

										.replace("Controller", "")

										.replaceAll("([a-z])([A-Z])", "$1 $2");

						if (doc.tags.stream()

								.noneMatch(t ->

								t.get("name").equals(tag))) {

							doc.tags.add(

									Map.of("name", tag, "description", "Operaciones " + tag));
						}

						String basePath = extractClassMapping(clazz);

						clazz.getMethods().forEach(method ->

					processMethod(method, doc, tag, basePath));
					});

		} catch (Exception e) {

			System.out.println("❌ Error procesando controller: " + file.getAbsolutePath());

			e.printStackTrace();
		}
	}

	// =========================================================
	// ✅ PROCESS METHOD
	// =========================================================
	@SuppressWarnings("unchecked")
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

			path = "/" + fallbackPath;

		} else {

			httpMethod = httpMapping.get("method");

			path = httpMapping.get("path");
		}

		String fullPath =

				("/"

						+ (basePath == null ? "" : basePath)

						+ "/"

						+ (path == null ? "" : path))

						.replaceAll("//+", "/")

						.trim();

		if (fullPath.length() > 1 && fullPath.endsWith("/")) {

			fullPath = fullPath.substring(0, fullPath.length() - 1);
		}

		Map<String, Object> op = new LinkedHashMap<>();

		op.put("tags", List.of(tag));

		op.put("summary", method.getNameAsString());

		String operationId = generateOperationId(tag, method.getNameAsString());

		op.put("operationId", operationId);

		Object request = requestBuilder.build(fullPath, method, schemaMap, exampleMap, IGNORED_TYPES);

		if (request != null) {

			op.put("requestBody", request);
		}

		Object response = responseBuilder.build(fullPath, method, schemaMap, exampleMap, IGNORED_TYPES);

		if (response != null) {

			op.put("responses", response);
		}

		Map<String, Object> pathItem =

				(Map<String, Object>) doc.paths

						.getOrDefault(fullPath, new LinkedHashMap<>());

		pathItem.put(httpMethod, op);

		doc.paths.put(fullPath, pathItem);
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

						Object propsObj = schema.get("properties");

						boolean hasProperties =

								propsObj instanceof Map

										&& !((Map<?, ?>) propsObj).isEmpty();

						if (hasProperties) {

							schemaMap.putIfAbsent(className, schema);
						}
					});

		} catch (Exception e) {

			System.out.println("❌ Error parseando schema: " + file.getAbsolutePath());

			e.printStackTrace();
		}
	}

	// =========================================================
	// ✅ PROCESS EXAMPLES
	// =========================================================
	private void processBeanExamples(File file, String basePackage) {

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

						exampleMap.put(className,

								exampleGenerator.buildExampleFromType(className));
					});

		} catch (Exception e) {

			System.out.println("❌ Error generando example: " + file.getAbsolutePath());

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

			System.out.println("❌ Ruta NO existe: " + root);

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

						.flatMap(c ->

						c.getPackageDeclaration())

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

		propsEntrada.put("identificadorUnicoGlobal", Map.of("type", "string", "format", "uuid"));

		propsEntrada.put("identificacionCanal", Map.of("type", "string"));

		propsEntrada.put("identificacionSubCanal", Map.of("type", "string"));

		propsEntrada.put("siglaAplicacion", Map.of("type", "string"));

		propsEntrada.put("cantidadRegistros", Map.of("type", "integer"));

		headerEntrada.put("properties", propsEntrada);

		schemaMap.putIfAbsent("HeaderEntrada", headerEntrada);

		Map<String, Object> headerSalida = new LinkedHashMap<>();

		headerSalida.put("type", "object");

		Map<String, Object> propsSalida = new LinkedHashMap<>();

		propsSalida.put("tipoMensaje", Map.of("type", "string"));

		headerSalida.put("properties", propsSalida);

		schemaMap.putIfAbsent("HeaderSalida", headerSalida);
	}

	// =========================================================
	// ✅ GENERATE + SAVE
	// =========================================================
	public OpenApiDoc generateAndSaveReturningDoc(ServiceItem service, String outputDir) {

		System.out.println("\n📦 Generando y guardando servicio: " + service.getName());

		OpenApiDoc doc = generate(service);

		saveDoc(doc, service.getName(), outputDir);

		return doc;
	}

	// =========================================================
	// ✅ SAVE JSON
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

			System.out.println("❌ Error guardando archivo");

			throw new RuntimeException(e);
		}
	}

	// =========================================================
	// ✅ BACKEND SERVICES
	// =========================================================
	private void loadBackendServices(String configPath) {

		try {

			File file = new File(configPath + "/restservices.xml");

			if (!file.exists()) {

				System.out.println("⚠️ XML no encontrado: " + configPath);

				return;
			}

			var db = javax.xml.parsers.DocumentBuilderFactory.newInstance();

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

			System.out.println("✅ Backend services cargados: " + backendServiceMap.size());

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	// =========================================================
	private String resolveConfigPath(ServiceItem service) {

		return "C:/BM_HOME/appl/api-" + service.getName() + "/config";
	}

	// =========================================================
	// ✅ CLASS INDEX
	// =========================================================
	private void indexAllClasses(List<File> files) {

		System.out.println("🔍 Indexando clases...");

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

				System.out.println("⚠️ Error indexando: " + file.getName());
			}
		}

		System.out.println("✅ Clases indexadas: " + count.get());
	}

	// =========================================================
	// ✅ REMOVE EMPTY
	// =========================================================
	@SuppressWarnings("unchecked")
	private void removeEmptySchemas() {

		schemaMap.entrySet()

				.removeIf(entry -> {

					Map<String, Object> schema = entry.getValue();

					Object props = schema.get("properties");

					boolean emptyProps =

							props instanceof Map

									&& ((Map<?, ?>) props).isEmpty();

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
}