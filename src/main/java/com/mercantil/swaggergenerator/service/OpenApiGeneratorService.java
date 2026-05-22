package com.mercantil.swaggergenerator.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;

@Service
public class OpenApiGeneratorService {

	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	public OpenApiDoc generate(ServiceItem service) {

		OpenApiDoc doc = new OpenApiDoc();

		doc.security = List.of(Map.of("bearerAuth", List.of()));

		doc.info.title = "API " + service.getName();

		doc.servers.add(Map.of("url", service.getHost() + service.getBasePath()));

		// ✅ 1. cargar beans primero
		List<File> beanFiles = findJavaFiles(service.getBeansPath());
		beanFiles.forEach(f -> processBeanFile(f, doc));

		// ✅ 2. luego controllers
		List<File> controllerFiles = findJavaFiles(service.getControllersPath());
		controllerFiles.forEach(f -> processControllerFile(f, doc));

		// ✅ 3. inyectar schemas
		doc.components.put("schemas", schemaMap);

		return doc;
	}

	private void processControllerFile(File file, OpenApiDoc doc) {

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

				if (!clazz.getAnnotationByName("RestController").isPresent()) {
					return;
				}

				String controllerName = clazz.getNameAsString();

				String tag = controllerName.replace("Controller", "").replaceAll("([a-z])([A-Z])", "$1 $2").trim();

				// ✅ evitar duplicados + generar descripción correctamente
				if (doc.tags.stream().noneMatch(t -> t.get("name").equals(tag))) {

					String description = "Operaciones " + tag;

					if (clazz.getJavadoc().isPresent()) {

						String docText = cleanDoc(clazz.getJavadoc().get().getDescription().toText());

						if (!docText.isEmpty()) {
							description = docText;
						}
					}

					doc.tags.add(Map.of("name", tag, "description", description));
				}

				// ✅ base path del controller
				String basePath = extractClassMapping(clazz);

				// ✅ procesar métodos
				clazz.getMethods().forEach(method -> {
					processMethod(method, doc, tag, basePath);
				});
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void processBeanFile(File file, OpenApiDoc doc) {

		try {

			CompilationUnit cu = StaticJavaParser.parse(file);

			cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

				String className = clazz.getNameAsString();

				// ✅ ❌ IGNORAR CLASES BASE DEL FRAMEWORK
				if (className.equals("ConstructorRequired") || className.equals("BeansZOS")) {
					return;
				}

				// ✅ ❌ ignorar clases sin fields (normalmente helpers)
				if (clazz.getFields().isEmpty() && !clazz.isEnumDeclaration()) {
					return;
				}

				// ✅ evitar duplicados schema
				if (!schemaMap.containsKey(className)) {

					Map<String, Object> schema = buildSchemaFromClass(clazz);

					schemaMap.put(className, schema);
				}

				// ✅ generar ejemplo
				if (!exampleMap.containsKey(className)) {

					Object example = buildExampleFromClass(clazz);

					exampleMap.put(className, example);
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<String, Object> buildSchemaFromClass(ClassOrInterfaceDeclaration clazz) {

		List<String> required = new ArrayList<>();

		boolean isResponseClass = clazz.getNameAsString().endsWith("Response");

		// ✅ ENUM
		if (clazz.isEnumDeclaration()) {

			List<String> values = clazz.asEnumDeclaration().getEntries().stream().map(e -> e.getNameAsString())
					.collect(Collectors.toList());

			return Map.of("type", "string", "enum", values);
		}

		Map<String, Object> properties = new LinkedHashMap<>();

		clazz.getFields().forEach(field -> {

			boolean isRequired = field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("NotNull"));

			field.getVariables().forEach(var -> {

				String name = var.getNameAsString();
				String type = field.getElementType().asString();

				// ✅ =========================
				// ✅ 🔥 FILTRO INTELIGENTE RESPONSE
				// ✅ =========================
				if (isResponseClass) {

					// ❌ eliminar campos heredados del framework
					if (name.equals("bodyEntrada") || name.equals("bodySalida") || name.equals("headerEntrada")
							|| name.equals("code")) {
						return;
					}

					// ✅ permitir SOLO lo válido
					boolean allowed = name.equals("headerSalida") || name.equals("message")
							|| name.matches("bodySalida[A-Z].*");

					if (!allowed) {
						return;
					}
				}

				// ✅ agregar required
				if (isRequired) {
					required.add(name);
				}

				Map<String, Object> prop = new LinkedHashMap<>();

				// ✅ =========================
				// ✅ TIPOS
				// ✅ =========================

				// ✅ LISTA
				if (type.startsWith("List")) {

					String generic = extractGeneric(type);

					prop.put("type", "array");
					prop.put("items", Map.of("$ref", "#/components/schemas/" + generic));
				}

				// ✅ PRIMITIVOS
				else if (isPrimitive(type)) {

					prop.put("type", mapType(type));
				}

				// ✅ OBJETOS
				else {

					prop.put("$ref", "#/components/schemas/" + type);
				}

				properties.put(name, prop);
			});
		});

		// ✅ RESULTADO FINAL
		Map<String, Object> schema = new LinkedHashMap<>();

		schema.put("type", "object");
		schema.put("properties", properties);

		if (!required.isEmpty()) {
			schema.put("required", required);
		}

		return schema;
	}

	private String extractClassMapping(ClassOrInterfaceDeclaration clazz) {

		return clazz.getAnnotationByName("RequestMapping").map(a -> {
			try {
				return a.asNormalAnnotationExpr().getPairs().stream()
						.filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
						.findFirst().get().getValue().asStringLiteralExpr().getValue();
			} catch (Exception e) {
				try {
					return a.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().getValue();
				} catch (Exception ex) {
					return "";
				}
			}
		}).orElse("");
	}

	private String cleanDoc(String text) {

		if (text == null)
			return "";

		return text.replaceAll("\\*", "").replaceAll("\\n", " ").replaceAll("\\s+", " ") // elimina espacios duplicados
				.trim();
	}

	private void processMethod(MethodDeclaration method, OpenApiDoc doc, String tag, String basePath) {

		String path = "";
		String httpMethod = "";

		if (method.getAnnotationByName("GetMapping").isPresent()) {
			httpMethod = "get";
			path = extractMapping(method, "GetMapping");
		} else if (method.getAnnotationByName("PostMapping").isPresent()) {
			httpMethod = "post";
			path = extractMapping(method, "PostMapping");
		} else if (method.getAnnotationByName("PutMapping").isPresent()) {
			httpMethod = "put";
			path = extractMapping(method, "PutMapping");
		} else if (method.getAnnotationByName("DeleteMapping").isPresent()) {
			httpMethod = "delete";
			path = extractMapping(method, "DeleteMapping");
		} else {
			return;
		}

		String fullPath = ("/" + basePath + "/" + path).replaceAll("//+", "/");

		Map<String, Object> op = new LinkedHashMap<>();

		op.put("tags", List.of(tag));
		op.put("summary", formatMethodName(method.getNameAsString()));

		// ✅ JAVADOC
		method.getJavadoc().ifPresent(javadoc -> {
			String description = javadoc.getDescription().toText();
			if (description != null && !description.isEmpty()) {
				op.put("description", cleanDoc(description));
			}
		});

		// ✅ ===========================
		// ✅ PARAMETERS + REQUEST BODY
		// ✅ ===========================
		List<Object> parameters = new ArrayList<>();

		method.getParameters().forEach(p -> {

			// ✅ PATH PARAM
			if (p.getAnnotationByName("PathVariable").isPresent()) {

				parameters.add(Map.of("name", p.getNameAsString(), "in", "path", "required", true, "schema",
						Map.of("type", "string")));
			}

			// ✅ QUERY PARAM
			else if (p.getAnnotationByName("RequestParam").isPresent()) {

				parameters.add(Map.of("name", p.getNameAsString(), "in", "query", "required", false, "schema",
						Map.of("type", "string")));
			}

			// ✅ REQUEST BODY
			else if (p.getAnnotationByName("RequestBody").isPresent()) {

				String type = cleanType(p.getType().asString());
				Object example = exampleMap.get(type);

				Map<String, Object> jsonContent = new LinkedHashMap<>();

				jsonContent.put("schema", Map.of("$ref", "#/components/schemas/" + type));

				// ✅ SOLO SI EXISTE
				if (example != null) {
					jsonContent.put("example", example);
				}

				op.put("requestBody", Map.of("content", Map.of("application/json", jsonContent)));
			}
		});

		if (!parameters.isEmpty()) {
			op.put("parameters", parameters);
		}

		// ✅ ===========================
		// ✅ RESPONSE
		// ✅ ===========================
		String returnType = cleanType(method.getType().asString());

		if (!returnType.equals("void")) {

			Object example = exampleMap.get(returnType);

			Map<String, Object> jsonContent = new LinkedHashMap<>();

			jsonContent.put("schema", Map.of("$ref", "#/components/schemas/" + returnType));

			// ✅ SOLO SI EXISTE
			if (example != null) {
				jsonContent.put("example", example);
			}

			op.put("responses",
					Map.of("200", Map.of("description", "OK", "content", Map.of("application/json", jsonContent))));

		} else {

			op.put("responses", Map.of("200", Map.of("description", "OK (sin contenido)")));
		}

		// ✅ guardar en paths
		if (doc.paths.containsKey(fullPath)) {

			((Map<String, Object>) doc.paths.get(fullPath)).put(httpMethod, op);

		} else {

			doc.paths.put(fullPath, new LinkedHashMap<>(Map.of(httpMethod, op)));
		}

		// ✅ DEBUG
		System.out.println("✅ " + httpMethod.toUpperCase() + " " + fullPath);
	}

	private String extractMapping(MethodDeclaration method, String annotation) {

		return method.getAnnotationByName(annotation).map(a -> {
			try {
				return a.asNormalAnnotationExpr().getPairs().stream().findFirst().get().getValue().asStringLiteralExpr()
						.getValue();
			} catch (Exception e) {
				try {
					return a.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().getValue();
				} catch (Exception ex) {
					return "";
				}
			}
		}).orElse("");
	}

	private List<File> findJavaFiles(String root) {

		List<File> files = new ArrayList<>();

		File dir = new File(root);

		File[] list = dir.listFiles();

		if (list == null)
			return files;

		for (File f : list) {

			if (f.isDirectory()) {
				files.addAll(findJavaFiles(f.getAbsolutePath()));
			} else if (f.getName().endsWith(".java")) {
				files.add(f);
			}
		}

		return files;
	}

	private String formatMethodName(String name) {

		// separar camelCase
		String result = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		// primera en mayúscula
		result = result.substring(0, 1).toUpperCase() + result.substring(1);

		return result;
	}

	private boolean isPrimitive(String type) {

		return type.equals("String") || type.equals("Integer") || type.equals("int") || type.equals("Double")
				|| type.equals("double") || type.equals("Boolean") || type.equals("boolean") || type.equals("Long")
				|| type.equals("long");
	}

	private String mapType(String type) {

		if (type.equals("String"))
			return "string";
		if (type.equals("Integer") || type.equals("int"))
			return "integer";
		if (type.equals("Double") || type.equals("double"))
			return "number";
		if (type.equals("Boolean") || type.equals("boolean"))
			return "boolean";
		if (type.equals("Long") || type.equals("long"))
			return "integer";

		return "string";
	}

	private String extractGeneric(String type) {

		int start = type.indexOf("<");
		int end = type.indexOf(">");

		if (start != -1 && end != -1) {
			return type.substring(start + 1, end);
		}

		return "Object";
	}

	private Object cleanExampleObject(Object obj) {
	
	    if (!(obj instanceof Map)) {
	        return obj;
	    }
	
	    Map<String, Object> cleaned = new LinkedHashMap<>();
	
	    ((Map<?, ?>) obj).forEach((k, v) -> {
	
	        String key = k.toString();
	
	        // ❌ eliminar basura heredada SIEMPRE
	        if (key.equals("bodyEntrada") ||
	            key.equals("bodySalida") ||
	            key.equals("headerEntrada") ||
	            key.equals("code")) {
	            return;
	        }
	
	        // ✅ limpiar recursivo
	        cleaned.put(key, cleanExampleObject(v));
	    });
	
	    return cleaned;
	}

	private Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {
	
	    Map<String, Object> example = new LinkedHashMap<>();
	
	    boolean isResponseClass =
	        clazz.getNameAsString().endsWith("Response");
	
	    clazz.getFields().forEach(field -> {
	
	        field.getVariables().forEach(var -> {
	
	            String name = var.getNameAsString();
	            String type = field.getElementType().asString();
	
	            // ✅ FILTRO RESPONSE (MISMO QUE SCHEMA)
	            if (isResponseClass) {
	
	                if (name.equals("bodyEntrada") ||
	                    name.equals("bodySalida") ||
	                    name.equals("headerEntrada") ||
	                    name.equals("code")) {
	                    return;
	                }
	
	                boolean allowed =
	                        name.equals("headerSalida") ||
	                        name.equals("message") ||
	                        name.matches("bodySalida[A-Z].*");
	
	                if (!allowed) {
	                    return;
	                }
	            }
	
	            // ✅ STRING
	            if (type.equals("String")) {
	                example.put(name, "string");
	            }
	
	            // ✅ NUMERICOS
	            else if (type.equals("Integer") || type.equals("int")) {
	                example.put(name, 0);
	            }
	            else if (type.equals("Long") || type.equals("long")) {
	                example.put(name, 1);
	            }
	            else if (type.equals("Double") || type.equals("double")) {
	                example.put(name, 0.0);
	            }
	
	            // ✅ BOOLEAN
	            else if (type.equals("Boolean") || type.equals("boolean")) {
	                example.put(name, true);
	            }
	
	            // ✅ LISTA
	            else if (type.startsWith("List")) {
	
	                String generic = extractGeneric(type);
	
	                Object nested = exampleMap.get(generic);
	
	                example.put(name,
	                    List.of(cleanExampleObject(nested))
	                );
	            }
	
	            // ✅ OBJETO
	            else {
	
	                Object nested = exampleMap.get(type);
	
	                example.put(name,
	                    cleanExampleObject(nested)
	                );
	            }
	        });
	    });
	
	    return example;
	}

	private String cleanType(String type) {

		if (type.contains(".")) {
			return type.substring(type.lastIndexOf(".") + 1);
		}
		return type;
	}

}