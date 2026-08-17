package com.mercantil.swaggergenerator.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

class HttpMethodUtilTest {

	private final HttpMethodUtil httpMethodUtil = new HttpMethodUtil();

	private MethodDeclaration parseMethod(String methodSource) {

		CompilationUnit cu = StaticJavaParser.parse(

				"class Dummy {\n" + methodSource + "\n}");

		return cu.findFirst(MethodDeclaration.class).orElseThrow();
	}

	@Test
	void detectsPatchViaRequestMappingMethodAttribute() {

		MethodDeclaration method = parseMethod(

				"@RequestMapping(path = \"/orders/{id}\", method = RequestMethod.PATCH) void patchOrder() {}");

		Map<String, String> result = httpMethodUtil.detect(method);

		assertThat(result).containsEntry("method", "patch").containsEntry("path", "/orders/{id}");
	}

	@Test
	void bareRequestMappingIsTreatedAsAnEndpointInsteadOfDiscarded() {

		MethodDeclaration method = parseMethod(

				"@RequestMapping(\"/health\") void health() {}");

		Map<String, String> result = httpMethodUtil.detect(method);

		assertThat(result).containsEntry("method", "get").containsEntry("path", "/health");
	}

	@Test
	void detectsDedicatedPatchMappingAnnotation() {

		MethodDeclaration method = parseMethod(

				"@PatchMapping(\"/orders/{id}\") void patchOrder() {}");

		Map<String, String> result = httpMethodUtil.detect(method);

		assertThat(result).containsEntry("method", "patch").containsEntry("path", "/orders/{id}");
	}

	@Test
	void methodWithoutAnyMappingAnnotationIsNotDetected() {

		MethodDeclaration method = parseMethod("void helper() {}");

		assertThat(httpMethodUtil.detect(method)).isNull();
	}
}
