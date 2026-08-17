package com.mercantil.swaggergenerator.util;

public final class PathUtil {

	private static final String SLASH = "/";

	private PathUtil() {
	}

	// =========================================================
	// ✅ NORMALIZAR PATH
	// =========================================================
	public static String normalize(String path) {

		if (path == null || path.isBlank()) {
			return "";
		}

		String normalized = path.trim().replace("\\", SLASH).replaceAll("/+", SLASH);

		if (!normalized.startsWith(SLASH)) {
			normalized = SLASH + normalized;
		}

		if (normalized.length() > 1 && normalized.endsWith(SLASH)) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}

		return normalized;
	}
}
