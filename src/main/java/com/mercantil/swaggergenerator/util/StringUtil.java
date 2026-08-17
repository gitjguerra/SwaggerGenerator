package com.mercantil.swaggergenerator.util;

public final class StringUtil {

	private StringUtil() {
	}

	public static String capitalize(String str) {

		if (str == null || str.isBlank()) {
			return "";
		}

		return Character.toUpperCase(str.charAt(0)) + str.substring(1);
	}

	public static String decapitalize(String str) {

		if (str == null || str.isBlank()) {
			return "";
		}

		return Character.toLowerCase(str.charAt(0)) + str.substring(1);
	}
}
