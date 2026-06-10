package com.mercantil.swaggergenerator.util;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;

@Component
public class ParserUtil {

	// =========================================================
	// ✅ JSON PROPERTY
	// =========================================================
	public String resolveJsonName(FieldDeclaration field, String defaultName) {

		// ✅ 1. FIELD DIRECTO
		Optional<AnnotationExpr> annOpt = field.getAnnotationByName("JsonProperty");

		if (annOpt.isPresent()) {

			String val = extractJsonValue(annOpt.get());

			if (val != null) {

				return val;
			}
		}

		// ✅ 2. GETTER
		Optional<String> getterValue =

				field.findCompilationUnit()

						.flatMap(cu ->

						cu.findAll(MethodDeclaration.class)

								.stream()

								.filter(m ->

								isGetterForField(m, defaultName))

								.map(m ->

								m.getAnnotationByName("JsonProperty"))

								.filter(Optional::isPresent)

								.map(Optional::get)

								.map(this::extractJsonValue)

								.filter(Objects::nonNull)

								.findFirst());

		if (getterValue.isPresent()) {

			return getterValue.get();
		}

		// ✅ 3. CONSTRUCTOR (@JsonCreator)
		Optional<String> constructorValue =

				field.findCompilationUnit()

						.flatMap(cu ->

						cu.findAll(ConstructorDeclaration.class)

								.stream()

								.filter(c ->

								c.getAnnotationByName("JsonCreator")

										.isPresent())

								.flatMap(c ->

								c.getParameters()

										.stream())

								.filter(p ->

								p.getNameAsString().equals(defaultName))

								.map(p ->

								p.getAnnotationByName("JsonProperty"))

								.filter(Optional::isPresent)

								.map(Optional::get)

								.map(this::extractJsonValue)

								.filter(Objects::nonNull)

								.findFirst());

		return constructorValue.orElse(defaultName);
	}

	// =========================================================
	// ✅ EXTRAER JSON VALUE
	// =========================================================
	private String extractJsonValue(AnnotationExpr ann) {

		// ✅ @JsonProperty("campo")
		if (ann.isSingleMemberAnnotationExpr()) {

			return ann.asSingleMemberAnnotationExpr()

					.getMemberValue()

					.toString()

					.replace("\"", "");
		}

		// ✅ @JsonProperty(value = "campo")
		if (ann.isNormalAnnotationExpr()) {

			return ann.asNormalAnnotationExpr()

					.getPairs()

					.stream()

					.filter(p ->

					p.getNameAsString().equals("value"))

					.findFirst()

					.map(p ->

					p.getValue()

							.toString()

							.replace("\"", ""))

					.orElse(null);
		}

		// ✅ @JsonProperty sin value
		return null;
	}

	// =========================================================
	// ✅ EXTRACT GENERIC
	// =========================================================
	public String extractGeneric(String input) {

		if (input == null || input.isBlank()) {

			return null;
		}

		int start = input.indexOf("<");

		int end = input.lastIndexOf(">");

		if (start == -1 || end == -1 || end <= start) {

			return input.trim();
		}

		String inner = input.substring(start + 1, end);

		// ✅ SOPORTA GENERICS ANIDADOS
		if (inner.contains("<")) {

			return extractGeneric(inner);
		}

		return inner.trim();
	}

	// =========================================================
	// ✅ EXTRAER VALUE MAP<K,V>
	// =========================================================
	public String extractMapValue(String input) {

		if (input == null || !input.contains(",")) {

			return "Object";
		}

		int start = input.indexOf("<");

		int end = input.lastIndexOf(">");

		if (start == -1 || end == -1) {

			return "Object";
		}

		String inside = input.substring(start + 1, end);

		String[] parts = inside.split(",");

		if (parts.length < 2) {

			return "Object";
		}

		return resolveFinalType(parts[1].trim());
	}

	// =========================================================
	// ✅ LIMPIAR TIPO FINAL
	// =========================================================
	public String resolveFinalType(String type) {

		if (type == null || type.isBlank()) {

			return null;
		}

		String clean = type.trim();

		// =====================================================
		// ✅ OPTIONAL
		// =====================================================
		if (clean.startsWith("Optional<")) {

			clean = extractGeneric(clean);
		}

		// =====================================================
		// ✅ QUITAR PACKAGES
		// =====================================================
		if (clean.contains(".")) {

			clean = clean.substring(clean.lastIndexOf(".") + 1);
		}

		// =====================================================
		// ✅ LIST / SET
		// =====================================================
		if (clean.startsWith("List<") || clean.startsWith("Set<")) {

			return extractGeneric(clean);
		}

		// =====================================================
		// ✅ MAP<K,V>
		// =====================================================
		if (clean.startsWith("Map<")) {

			return extractMapValue(clean);
		}

		// =====================================================
		// ✅ GENERICS RESTANTES
		// =====================================================
		if (clean.contains("<")) {

			clean = extractGeneric(clean);
		}

		clean = clean.trim();

		// =====================================================
		// ✅ FALLBACK
		// =====================================================
		if (clean.isBlank()) {

			return type;
		}

		// ✅ NUNCA degradar beans reales
		return clean;
	}

	// =========================================================
	// ✅ HELPERS
	// =========================================================
	public boolean isList(String type) {

		return type != null

				&& (type.startsWith("List<") || type.startsWith("Set<"));
	}

	public boolean isMap(String type) {

		return type != null && type.startsWith("Map<");
	}

	public boolean isOptional(String type) {

		return type != null && type.startsWith("Optional<");
	}

	public String unwrapOptional(String type) {

		if (isOptional(type)) {

			return extractGeneric(type);
		}

		return type;
	}

	// =========================================================
	// ✅ GENERIC COMPLEJO
	// =========================================================
	public String extractDeepestType(String type) {

		if (type == null || type.isBlank()) {

			return null;
		}

		// ✅ EJEMPLOS:
		// List<Map<String, User>> -> User
		// Optional<List<Account>> -> Account

		while (type.contains("<")) {

			type = extractGeneric(type);
		}

		return resolveFinalType(type);
	}

	// =========================================================
	// ✅ VALIDAR GETTER
	// =========================================================
	private boolean isGetterForField(MethodDeclaration m, String fieldName) {

		String methodName = m.getNameAsString();

		String expected =

				"get"

						+ Character.toUpperCase(fieldName.charAt(0))

						+ fieldName.substring(1);

		return methodName.equals(expected);
	}

	public String resolveJsonNameFromConstructor(ClassOrInterfaceDeclaration clazz, String fieldName) {

		for (ConstructorDeclaration ctor : clazz.getConstructors()) {

			for (Parameter param : ctor.getParameters()) {

				String paramName = param.getNameAsString();

				// ✅ Match field semántico
				if (fieldName.equalsIgnoreCase(paramName) || fieldName.contains(paramName)
						|| paramName.contains(fieldName)) {

					for (AnnotationExpr ann : param.getAnnotations()) {

						if ("JsonProperty".equals(ann.getNameAsString())) {

							if (ann.isSingleMemberAnnotationExpr()) {

								return ann.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
							}
						}
					}
				}
			}
		}

		return fieldName;
	}

}