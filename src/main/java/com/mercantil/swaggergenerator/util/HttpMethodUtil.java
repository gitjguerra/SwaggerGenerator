package com.mercantil.swaggergenerator.util;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class HttpMethodUtil {

	private static final String METHOD = "method";
	private static final String PATH = "path";

	private static final String POST_MAPPING = "PostMapping";
	private static final String GET_MAPPING = "GetMapping";
	private static final String PUT_MAPPING = "PutMapping";
	private static final String DELETE_MAPPING = "DeleteMapping";
	private static final String PATCH_MAPPING = "PatchMapping";
	private static final String REQUEST_MAPPING = "RequestMapping";

	public Map<String, String> detect(MethodDeclaration method) {

		Map<String, String> result;

		result = detectMapping(method, POST_MAPPING, "post");
		if (!result.isEmpty()) {
			return result;
		}

		result = detectMapping(method, GET_MAPPING, "get");
		if (!result.isEmpty()) {
			return result;
		}

		result = detectMapping(method, PUT_MAPPING, "put");
		if (!result.isEmpty()) {
			return result;
		}

		result = detectMapping(method, DELETE_MAPPING, "delete");
		if (!result.isEmpty()) {
			return result;
		}

		result = detectMapping(method, PATCH_MAPPING, "patch");
		if (!result.isEmpty()) {
			return result;
		}

		result = detectRequestMapping(method);

		// ✅ ningún mapping HTTP encontrado: se expone como null (no como Map vacío)
		// para que los callers puedan distinguir "no es un endpoint" de forma explícita.
		return result.isEmpty() ? null : result;
	}

	private Map<String, String> detectMapping(MethodDeclaration method, String annotation, String httpMethod) {

		if (method.getAnnotationByName(annotation).isPresent()) {

			return Map.of(METHOD, httpMethod, PATH, extract(method, annotation));
		}

		return Map.of();
	}

	private Map<String, String> detectRequestMapping(MethodDeclaration method) {

		Optional<?> requestMapping = method.getAnnotationByName(REQUEST_MAPPING);

		if (requestMapping.isEmpty()) {
			return Map.of();
		}

		String annotation = requestMapping.get().toString();

		String httpMethod = resolveHttpMethod(annotation);

		if (httpMethod == null) {
			return Map.of();
		}

		return Map.of(METHOD, httpMethod, PATH, extract(method, REQUEST_MAPPING));
	}

	private String resolveHttpMethod(String annotation) {

		if (annotation.contains("RequestMethod.POST")) {
			return "post";
		}

		if (annotation.contains("RequestMethod.GET")) {
			return "get";
		}

		if (annotation.contains("RequestMethod.PUT")) {
			return "put";
		}

		if (annotation.contains("RequestMethod.DELETE")) {
			return "delete";
		}

		if (annotation.contains("RequestMethod.PATCH")) {
			return "patch";
		}

		if (annotation.contains("RequestMethod.HEAD")) {
			return "head";
		}

		if (annotation.contains("RequestMethod.OPTIONS")) {
			return "options";
		}

		if (annotation.contains("RequestMethod.TRACE")) {
			return "trace";
		}

		// ✅ @RequestMapping sin atributo "method" matchea todos los verbos en Spring;
		// se documenta como GET (el uso real más común para mappings sin verbo explícito)
		// en vez de tratarlo como "no es un endpoint".
		return "get";
	}

	private String extract(MethodDeclaration method, String name) {

		return method.getAnnotationByName(name).map(annotation -> {

			if (annotation.isSingleMemberAnnotationExpr()) {

				var value = annotation.asSingleMemberAnnotationExpr().getMemberValue();

				if (value.isStringLiteralExpr()) {

					return value.asStringLiteralExpr().asString();
				}
			}

			if (annotation.isNormalAnnotationExpr()) {

				for (var pair : annotation.asNormalAnnotationExpr().getPairs()) {

					String pairName = pair.getNameAsString();

					if ("value".equals(pairName) || "path".equals(pairName)) {

						if (pair.getValue().isStringLiteralExpr()) {

							return pair.getValue().asStringLiteralExpr().asString();
						}
					}
				}
			}

			return "";
		}).orElse("");
	}
}