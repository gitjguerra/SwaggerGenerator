package com.mercantil.swaggergenerator.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class AbbreviationUtil {

	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();
	private static final List<Map.Entry<String, String>> SORTED_ENTRIES = new ArrayList<>();

	static {
		loadAbbreviations();

		// ordenar UNA SOLA VEZ
		SORTED_ENTRIES.addAll(ABBREV_MAP.entrySet());
		SORTED_ENTRIES.sort((a, b) -> b.getKey().length() - a.getKey().length());
	}

	private static void loadAbbreviations() {

		try (var is = AbbreviationUtil.class.getClassLoader().getResourceAsStream("abbreviations.txt")) {

			if (is == null) {
				throw new RuntimeException("❌ No se encontró abbreviations.txt en resources");
			}

			new BufferedReader(new InputStreamReader(is)).lines().map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(line -> {

						String[] parts = line.split("=", 2); // ✅ más robusto

						if (parts.length == 2) {
							ABBREV_MAP.put(parts[0].toLowerCase().trim(), parts[1].toLowerCase().trim());
						}
					});

			System.out.println("✅ Abreviaturas cargadas: " + ABBREV_MAP.size());

		} catch (Exception e) {
			throw new RuntimeException("Error cargando abbreviations.txt", e);
		}
	}

	// ============================================
	// ✅ NORMALIZACIÓN
	// ============================================

	public static String normalizeName(String name) {

		if (name == null)
			return "";

		// ✅ separar camelCase primero
		String withSpaces = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		List<String> tokens = Arrays.stream(withSpaces.split("[^a-zA-Z0-9]+")).filter(t -> !t.isBlank())
				.collect(Collectors.toList());

		StringBuilder result = new StringBuilder();

		for (String token : tokens) {

			String lowerToken = token.toLowerCase();

			// ✅ reemplazo SOLO POR TOKEN (NO global)
			String normalized = ABBREV_MAP.getOrDefault(lowerToken, lowerToken);

			result.append(normalized).append(" ");
		}

		String finalResult = result.toString().trim();

		return finalResult;
	}

	// ============================================
	// ✅ TOKENIZADOR
	// ============================================
	public static List<String> tokenize(String name) {

		if (name == null)
			return List.of();

		String withSpaces = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		String normalized = normalizeName(withSpaces);

		return Arrays.stream(normalized.toLowerCase().split("[^a-z0-9]+")).filter(p -> !p.isBlank())
				.collect(Collectors.toList());
	}

	public static Map<String, String> getAbbreviations() {
		return ABBREV_MAP;
	}
}