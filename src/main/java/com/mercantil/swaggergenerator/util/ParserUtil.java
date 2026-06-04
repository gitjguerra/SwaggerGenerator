package com.mercantil.swaggergenerator.util;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

@Component
public class ParserUtil {

	// =========================================================
	// ✅ JSON PROPERTY
	// =========================================================
	public String resolveJsonName(FieldDeclaration field, String defaultName) {

		// ✅ 1. Field directo
		Optional<AnnotationExpr> annOpt = field.getAnnotationByName("JsonProperty");

		if (annOpt.isPresent()) {
			String val = extractJsonValue(annOpt.get());
			if (val != null)
				return val;
		}

		// ✅ 2. Getter
		Optional<String> getterValue = field.findCompilationUnit().flatMap(
				cu -> cu.findAll(MethodDeclaration.class).stream().filter(m -> isGetterForField(m, defaultName))
						.map(m -> m.getAnnotationByName("JsonProperty")).filter(Optional::isPresent).map(Optional::get)
						.map(this::extractJsonValue).filter(Objects::nonNull).findFirst());

		if (getterValue.isPresent())
			return getterValue.get();

		// ✅ 3. Constructor (@JsonCreator)
		Optional<String> constructorValue = field.findCompilationUnit()
				.flatMap(cu -> cu.findAll(ConstructorDeclaration.class).stream()
						.filter(c -> c.getAnnotationByName("JsonCreator").isPresent())
						.flatMap(c -> c.getParameters().stream()).filter(p -> p.getNameAsString().equals(defaultName))
						.map(p -> p.getAnnotationByName("JsonProperty")).filter(Optional::isPresent).map(Optional::get)
						.map(this::extractJsonValue).filter(Objects::nonNull).findFirst());

		return constructorValue.orElse(defaultName);
	}

	private String extractJsonValue(AnnotationExpr ann) {

		// ✅ CASO 1: @JsonProperty("nroPer")
		if (ann.isSingleMemberAnnotationExpr()) {

			return ann.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
		}

		// ✅ CASO 2: @JsonProperty(value = "nroPer")
		if (ann.isNormalAnnotationExpr()) {

			return ann.asNormalAnnotationExpr().getPairs().stream().filter(p -> p.getNameAsString().equals("value"))
					.findFirst().map(p -> p.getValue().toString().replace("\"", "")).orElse(null);
		}

		// ✅ CASO 3: @JsonProperty (sin valor explícito)
		// (raro, pero evitamos NPE)
		return null;
	}

	// =========================================================
	// ✅ GENERIC SIMPLE
	// =========================================================
	public String extractGeneric(String input) {

		if (input == null)
			return null;

		int start = input.indexOf("<");
		int end = input.lastIndexOf(">");

		if (start == -1 || end == -1 || end <= start) {
			return input;
		}

		String inner = input.substring(start + 1, end);

		// 🔥 soporta generics anidados
		if (inner.contains("<")) {
			return extractGeneric(inner);
		}

		return inner.trim();
	}

	// =========================================================
	// ✅ EXTRAER VALOR DE MAP<K,V>
	// =========================================================
	public String extractMapValue(String input) {

		if (input == null || !input.contains(","))
			return "Object";

		int start = input.indexOf("<");
		int end = input.lastIndexOf(">");

		if (start == -1 || end == -1)
			return "Object";

		String inside = input.substring(start + 1, end);

		String[] parts = inside.split(",");

		if (parts.length < 2)
			return "Object";

		return resolveFinalType(parts[1].trim());
	}

	// =========================================================
	// ✅ LIMPIAR TIPO FINAL
	// =========================================================
	public String resolveFinalType(String type) {

		if (type == null)
			return null;

		// 🔥 quitar Optional
		if (type.startsWith("Optional<")) {
			type = extractGeneric(type);
		}

		// 🔥 quitar paquetes
		if (type.contains(".")) {
			type = type.substring(type.lastIndexOf(".") + 1);
		}

		// 🔥 quitar generics restantes
		if (type.contains("<")) {
			type = extractGeneric(type);
		}

		return type.trim();
	}

	// =========================================================
	// ✅ HELPERS PRO
	// =========================================================
	public boolean isList(String type) {
		return type != null && (type.contains("List<") || type.contains("Set<"));
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
	// 🔥 GENERIC COMPLEJO (NIVEL ENTERPRISE)
	// =========================================================
	public String extractDeepestType(String type) {

		if (type == null)
			return null;

		// ejemplos:
		// List<Map<String, User>> -> User
		// Optional<List<Account>> -> Account

		while (type.contains("<")) {
			type = extractGeneric(type);
		}

		return resolveFinalType(type);
	}

	private boolean isGetterForField(MethodDeclaration m, String fieldName) {

		String methodName = m.getNameAsString();

		String expected = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

		return methodName.equals(expected);
	}

}