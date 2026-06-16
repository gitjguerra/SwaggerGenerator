package com.mercantil.swaggergenerator.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class AbbreviationUtil {

	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();
	private static final List<Map.Entry<String, String>> SORTED_ENTRIES = new ArrayList<>();

	static {
		loadAbbreviations();

		// ✅ ordenar UNA SOLA VEZ por longitud (mejor matching)
		SORTED_ENTRIES.addAll(ABBREV_MAP.entrySet());
		SORTED_ENTRIES.sort((a, b) -> b.getKey().length() - a.getKey().length());
	}

	// =========================================================
	// ✅ CARGA ESTILO OSBA
	// =========================================================
	private static void loadAbbreviations() {

		try {

			String pathFile = System.getProperty("pathAbbreviations");

			// ✅ TRY WITH RESOURCES — evita leaks
			try (InputStream is = loadInputStream(pathFile);
					BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

				reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
						.forEach(line -> {

							String[] parts = line.split("=", 2);

							if (parts.length == 2) {

								String key = parts[0].toLowerCase().trim();
								String value = parts[1].toLowerCase().trim();

								ABBREV_MAP.put(key, value);
							}
						});
			}

			System.out.println("✅ Abreviaturas cargadas: " + ABBREV_MAP.size());

		} catch (Exception e) {
			throw new RuntimeException("Error cargando abbreviations.txt", e);
		}
	}

	// =========================================================
	// ✅ NORMALIZACIÓN AVANZADA 🔥
	// =========================================================
	public static String normalizeName(String name) {

		if (name == null)
			return "";

		String withSpaces = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		List<String> tokens = Arrays.stream(withSpaces.split("[^a-zA-Z0-9]+")).filter(t -> !t.isBlank())
				.collect(Collectors.toList());

		StringBuilder result = new StringBuilder();

		for (String token : tokens) {

			String lowerToken = token.toLowerCase();

			String normalized = ABBREV_MAP.get(lowerToken);

			if (normalized == null) {
				normalized = matchByContains(lowerToken);
			}

			if (normalized == null) {
				normalized = lowerToken;
			}

			result.append(normalized).append(" ");
		}

		return result.toString().trim();
	}

	// =========================================================
	// ✅ MATCH INTELIGENTE 🔥
	// =========================================================
	private static String matchByContains(String token) {

		for (Map.Entry<String, String> entry : SORTED_ENTRIES) {

			if (token.equals(entry.getKey()) || token.contains(entry.getKey())) {
				return entry.getValue();
			}

			// ✅ match inverso
			if (token.equals(entry.getValue())) {
				return entry.getKey();
			}
		}

		return null;
	}

	// =========================================================
	// ✅ TOKENIZADOR
	// =========================================================
	public static List<String> tokenize(String name) {

		if (name == null)
			return List.of();

		String withSpaces = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		String normalized = normalizeName(withSpaces);

		return Arrays.stream(normalized.toLowerCase().split("[^a-z0-9]+")).filter(p -> !p.isBlank())
				.collect(Collectors.toList());
	}

	// =========================================================
	// ✅ MATCH ENTRE NOMBRES
	// =========================================================
	public static boolean matches(String a, String b) {

		List<String> tokensA = tokenize(a);
		List<String> tokensB = tokenize(b);

		return tokensA.equals(tokensB);
	}

	public static Map<String, String> getAbbreviations() {
		return ABBREV_MAP;
	}

	private static InputStream loadInputStream(String pathFile) {

		try {

			InputStream is = new java.io.FileInputStream(pathFile);

			System.out.println("✅ Cargando abbreviations.txt: " + pathFile);

			return is;

		} catch (Exception e) {

			System.out.println("⚠️ No se encontró abbreviations externo, usando interno...");

			InputStream is = AbbreviationUtil.class.getClassLoader().getResourceAsStream("abbreviations.txt");

			if (is == null) {
				throw new RuntimeException("❌ No se encontró abbreviations.txt");
			}

			return is;
		}
	}

}