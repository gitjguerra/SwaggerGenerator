package com.mercantil.swaggergenerator.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mercantil.swaggergenerator.model.OpenApiDoc;
import com.mercantil.swaggergenerator.model.ServiceItem;

@Service
public class OpenApiGeneratorService {

    public OpenApiDoc generate(ServiceItem service) {

        OpenApiDoc doc = new OpenApiDoc();

        doc.info.title = "API " + service.getName();

        doc.servers.add(
            Map.of("url", service.getHost() + service.getBasePath())
        );

        Reflections reflections = new Reflections(service.getBasePackage());

        Set<Class<?>> controllers =
            reflections.getTypesAnnotatedWith(RestController.class);

        for (Class<?> controller : controllers) {
            processController(controller, doc);
        }

        return doc;
    }
    

	private void processController(Class<?> controller, OpenApiDoc doc) {
	
	    String basePath = "";
	
	    if (controller.isAnnotationPresent(RequestMapping.class)) {
	        basePath = controller.getAnnotation(RequestMapping.class).value()[0];
	    }
	
	    for (Method method : controller.getDeclaredMethods()) {
	
			if (method.isAnnotationPresent(GetMapping.class)) {
			    addPath(doc, method, "get");
			
			} else if (method.isAnnotationPresent(PostMapping.class)) {
			    addPath(doc, method, "post");
			
			} else if (method.isAnnotationPresent(PutMapping.class)) {
			    addPath(doc, method, "put");
			
			} else if (method.isAnnotationPresent(DeleteMapping.class)) {
			    addPath(doc, method, "delete");
			}

	    }
	}

	private Map<String, Object> buildOperation(Method method, OpenApiDoc doc) {
	
	    Map<String, Object> operation = new LinkedHashMap<>();
	
	    operation.put("summary", method.getName());
	
	    // ✅ Request
	    if (method.getParameterCount() > 0) {
	
	        Class<?> req = method.getParameterTypes()[0];
	
	        addSchema(req, doc);
	
	        operation.put("requestBody", Map.of(
	            "content", Map.of(
	                "application/json", Map.of(
	                    "schema", Map.of(
	                        "$ref", "#/components/schemas/" + req.getSimpleName()
	                    )
	                )
	            )
	        ));
	    }
	
	    // ✅ Response
	    Class<?> res = method.getReturnType();
	
	    addSchema(res, doc);
	
	    operation.put("responses", Map.of(
	        "200", Map.of(
	            "description", "OK",
	            "content", Map.of(
	                "application/json", Map.of(
	                    "schema", Map.of(
	                        "$ref", "#/components/schemas/" + res.getSimpleName()
	                    )
	                )
	            )
	        )
	    ));
	
	    return operation;
	}

	private void addSchema(Class<?> clazz, OpenApiDoc doc) {
	
	    Map<String, Object> schemas =
	        (Map<String, Object>) doc.components.get("schemas");
	
	    if (schemas.containsKey(clazz.getSimpleName())) {
	        return;
	    }
	
	    schemas.put(clazz.getSimpleName(), Map.of()); // placeholder
	
	    schemas.put(clazz.getSimpleName(),
	        buildSchema(clazz, doc));
	}

	private Map<String, Object> buildSchema(Class<?> clazz, OpenApiDoc doc) {
	
	    Map<String, Object> properties = new LinkedHashMap<>();
	
	    for (Field f : clazz.getDeclaredFields()) {
	
	        Class<?> type = f.getType();
	
	        // ✅ LISTAS
	        if (List.class.isAssignableFrom(type)) {
	
	            Class<?> generic = getGenericType(f);
	
	            addSchema(generic, doc);
	
	            properties.put(f.getName(), Map.of(
	                "type", "array",
	                "items", Map.of(
	                    "$ref", "#/components/schemas/" + generic.getSimpleName()
	                )
	            ));
	        }
	
	        // ✅ TIPOS SIMPLES
	        else if (isPrimitive(type)) {
	
	            properties.put(f.getName(), Map.of(
	                "type", mapType(type)
	            ));
	        }

			else if (type.isEnum()) {
			
			    Object[] values = type.getEnumConstants();
			
			    properties.put(f.getName(), Map.of(
			        "type", "string",
			        "enum", Arrays.stream(values)
			                      .map(Object::toString)
			                      .toArray()
			    ));
			}
	        
	        // ✅ OBJETO COMPLEJO
	        else {
	
	            addSchema(type, doc);
	
	            properties.put(f.getName(), Map.of(
	                "$ref", "#/components/schemas/" + type.getSimpleName()
	            ));
	        }
	    }
	
	    return Map.of(
	        "type", "object",
	        "properties", properties
	    );
	}

	private Class<?> getGenericType(Field field) {
	
	    ParameterizedType type =
	        (ParameterizedType) field.getGenericType();
	
	    return (Class<?>) type.getActualTypeArguments()[0];
	}

	private boolean isPrimitive(Class<?> type) {
	
	    return type == String.class ||
	           type == Integer.class || type == int.class ||
	           type == Double.class  || type == double.class ||
	           type == Boolean.class || type == boolean.class ||
	           type == Long.class    || type == long.class;
	}

	private String mapType(Class<?> type) {
	
	    if (type == String.class) return "string";
	    if (type == Integer.class || type == int.class) return "integer";
	    if (type == Double.class || type == double.class) return "number";
	    if (type == Boolean.class || type == boolean.class) return "boolean";
	
	    return "string";
	}
	
	private void addPath(OpenApiDoc doc, Method method, String httpMethod) {
	
	    String path = extractPath(method);
	
	    Map<String, Object> operation = buildOperationWithParams(method, doc);
	
	    doc.paths.put(path, Map.of(httpMethod, operation));
	}

	private String extractPath(Method method) {
	
	    if (method.isAnnotationPresent(GetMapping.class)) {
	        return method.getAnnotation(GetMapping.class).value()[0];
	    }
	    if (method.isAnnotationPresent(PostMapping.class)) {
	        return method.getAnnotation(PostMapping.class).value()[0];
	    }
	
	    return "";
	}

	private List<Object> extractParameters(Method method, OpenApiDoc doc) {
	
	    List<Object> params = new ArrayList<>();
	
	    Parameter[] parameters = method.getParameters();
	
	    for (Parameter p : parameters) {
	
	        // 🔥 PATH VARIABLE
	        if (p.isAnnotationPresent(PathVariable.class)) {
	
	            String name = p.getAnnotation(PathVariable.class).value();
	
	            params.add(Map.of(
	                "name", name,
	                "in", "path",
	                "required", true,
	                "schema", Map.of(
	                    "type", mapType(p.getType())
	                )
	            ));
	        }
	
	        // 🔥 REQUEST PARAM (query)
	        else if (p.isAnnotationPresent(RequestParam.class)) {
	
	            RequestParam rp = p.getAnnotation(RequestParam.class);
	
	            String name = rp.value();
	
	            params.add(Map.of(
	                "name", name,
	                "in", "query",
	                "required", rp.required(),
	                "schema", Map.of(
	                    "type", mapType(p.getType())
	                )
	            ));
	        }
	
	        // 🔥 BODY (POST/PUT)
	        else {
	
	            Class<?> req = p.getType();
	
	            addSchema(req, doc);
	
	            params.add(Map.of(
	                "in", "body",   // ⚠️ OpenAPI real es requestBody (lo hacemos abajo)
	                "name", "body",
	                "schema", Map.of(
	                    "$ref", "#/components/schemas/" + req.getSimpleName()
	                )
	            ));
	        }
	    }
	
	    return params;
	}

	private Map<String, Object> buildOperationWithParams(Method method, OpenApiDoc doc) {
	
	    Map<String, Object> op = new LinkedHashMap<>();
	
	    op.put("summary", method.getName());
	
	    List<Object> parameters = new ArrayList<>();
	
	    Parameter[] params = method.getParameters();
	
	    for (Parameter p : params) {
	
	        if (p.isAnnotationPresent(PathVariable.class)) {
	
	            String name = p.getAnnotation(PathVariable.class).value();
	
	            parameters.add(Map.of(
	                "name", name,
	                "in", "path",
	                "required", true,
	                "schema", Map.of(
	                    "type", mapType(p.getType())
	                )
	            ));
	        }
	        else if (p.isAnnotationPresent(RequestParam.class)) {
	
	            RequestParam rp = p.getAnnotation(RequestParam.class);
	
	            parameters.add(Map.of(
	                "name", rp.value(),
	                "in", "query",
	                "required", rp.required(),
	                "schema", Map.of(
	                    "type", mapType(p.getType())
	                )
	            ));
	        }
	        else {
	            // ✅ BODY
	            Class<?> req = p.getType();
	
	            addSchema(req, doc);
	
	            op.put("requestBody", Map.of(
	                "content", Map.of(
	                    "application/json", Map.of(
	                        "schema", Map.of(
	                            "$ref", "#/components/schemas/" + req.getSimpleName()
	                        )
	                    )
	                )
	            ));
	        }
	    }
	
	    if (!parameters.isEmpty()) {
	        op.put("parameters", parameters);
	    }
	
	    // ✅ RESPONSE
	    Class<?> res = method.getReturnType();
	
	    addSchema(res, doc);
	
	    op.put("responses", Map.of(
	        "200", Map.of(
	            "description", "OK",
	            "content", Map.of(
	                "application/json", Map.of(
	                    "schema", Map.of(
	                        "$ref", "#/components/schemas/" + res.getSimpleName()
	                    )
	                )
	            )
	        )
	    ));
	
	    return op;
	}

}