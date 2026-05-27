package com.mercantil.swaggergenerator.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.util.HeaderExampleProvider;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

@Component
public class ExampleGenerator {

	@Autowired
	private TypeUtil typeUtil;

	@Autowired
	private ParserUtil parserUtil;

	@Autowired
	private HeaderExampleProvider headerProvider;

	// ✅ CONTEXTO DE VALORES CONSISTENTES
	private Map<String, Object> dataContext = new LinkedHashMap<>();

	// ✅ mapa de ejemplos
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ mapa de schemas
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
		this.schemaMap = schemaMap;
		this.exampleMap = new LinkedHashMap<>(); // ✅ reset global
	}

	// ✅ ABBREVIATIONS (parseado del PDF)
	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();
	static {
		loadAbbreviations();
	}

	public Object generateSmartExample(String name) {

		String normalized = normalizeName(name);
		String lower = normalized.toLowerCase();

		// ✅ tokenizar SIEMPRE desde el nombre original
		List<String> tokens = tokenize(name);

		// =========================================================
		// ✅ 1. CONTEXTO (REUTILIZACIÓN)
		// =========================================================
		for (Map.Entry<String, Object> entry : dataContext.entrySet()) {

			String key = entry.getKey();

			if (lower.equals(key) || lower.endsWith(key)) {
				return entry.getValue();
			}
		}

		// =========================================================
		// ✅ 2. REGLAS FUERTES (NORMALIZED - 🔥 PRIORIDAD ALTA)
		// =========================================================

		if (normalized.contains("codigoproducto"))
			return "02";
		if (normalized.contains("codigopais"))
			return "VE";
		if (normalized.contains("codigoempresa"))
			return "0108";
		if (normalized.contains("codigorazon"))
			return "01";

		// =========================================================
		// ✅ 3. SEMÁNTICA
		// =========================================================

		// ✅ TIPO IDENTIFICACION
		if ((tokens.contains("tipo") && tokens.contains("identificacion")) || lower.contains("tipoidentificacion")) {
			String v = "V";
			dataContext.put("tipoidentificacion", v);
			return v;
		}

		// ✅ IDENTIFICACIÓN / CÉDULA
		if ((tokens.contains("cedula") || tokens.contains("rif") || tokens.contains("identificacion")
				|| lower.contains("cedula") || lower.contains("rif")) && !tokens.contains("tipo")) {
			Integer v = 12345678;
			dataContext.put("cedula", v);
			return v;
		}

		// ✅ PERSONA
		if (tokens.contains("persona") || lower.contains("numeropersona")) {
			Integer v = 12345678;
			dataContext.put("persona", v);
			return v;
		}

		// ✅ SUBCANAL
		if (tokens.contains("subcanal") || lower.contains("subcanal")) {
			return "09";
		}

		// ✅ MONEDA
		if (tokens.contains("moneda") || lower.contains("moneda")) {
			return "VES";
		}

		// ✅ TARJETA
		if (tokens.contains("tarjeta") || lower.contains("tarjeta")) {

			String v = "1234567890123456";

			if (name.toLowerCase().endsWith("s")) {
				return List.of(v);
			}

			return v;
		}

		// =========================================================
		// ✅ CÓDIGOS (TOKEN + ABREVIATURAS)
		// =========================================================

		if ((tokens.contains("codigo") && tokens.contains("empresa")) || lower.contains("codemp")) {
			return "0108";
		}

		if ((tokens.contains("codigo") && tokens.contains("producto")) || lower.contains("codprod")) {
			return "02";
		}

		if ((tokens.contains("codigo") && tokens.contains("pais")) || lower.contains("codpais")) {
			return "VE";
		}

		if (tokens.contains("razon") || lower.contains("razon")) {
			return "01";
		}

		// ✅ CÓDIGO GENÉRICO
		if (tokens.contains("codigo") || lower.startsWith("codigo")) {
			return "0001";
		}

		// =========================================================
		// ✅ FINANCIERO
		// =========================================================

		if (tokens.contains("monto") || tokens.contains("mto") || lower.contains("mto")) {
			return 1500.50;
		}

		if (tokens.contains("tasa") || lower.contains("tasa")) {
			return 36.75;
		}

		return null;
	}

	public Object buildExampleFromType(String type) {

		if ("HeaderEntrada".equals(type)) {
			return headerProvider.buildHeaderEntrada();
		}

		if ("HeaderSalida".equals(type)) {
			return headerProvider.buildHeaderSalida();
		}

		// ✅ si ya existe en cache → usarlo
		if (exampleMap.containsKey(type)) {
			return exampleMap.get(type);
		}

		// ✅ BYTE / BINARIO (IMÁGENES)
		if (type.equals("byte") || type.equals("byte[]")) {
			return "base64-string";
		}

		// ✅ buscar en schemas
		Map<String, Object> schema = schemaMap.get(type);

		// ✅ ENUM → tomar primer valor como ejemplo
		if (schema != null && schema.containsKey("enum")) {

			List<?> values = (List<?>) schema.get("enum");

			if (!values.isEmpty()) {
				return values.get(0);
			}
		}

		if (schema == null) {
			return new LinkedHashMap<>();
		}

		// ✅ construir dinámicamente (recursivo)
		Map<String, Object> example = new LinkedHashMap<>();

		Object propsObj = schema.get("properties");

		if (!(propsObj instanceof Map)) {
			return example;
		}

		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((key, val) -> {

			if (!(val instanceof Map))
				return;

			Map<String, Object> prop = (Map<String, Object>) val;

			// ✅ $ref
			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				// ✅ SOLO OBJETO (NO LISTA)
				example.put(key, buildExampleFromType(refType));
			}

			// ✅ array

			else if ("array".equals(prop.get("type"))) {

				Object items = prop.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();
						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(key, List.of(buildExampleFromType(refType)));

					} else {

						example.put(key, List.of("string")); // fallback
					}
				}
			}

			// ✅ primitivo
			else {

				Object value = generateSmartExample(key);

				// ✅ SI HAY SEMÁNTICA
				if (value != null) {
					example.put(key, value);
				} else {
					example.put(key, "string"); // fallback simple
				}

			}
		});

		// ✅ fallback si no hubo propiedades
		if (example.isEmpty()) {

			// ✅ si no tiene propiedades → devolver objeto simple
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("id", "string");

			return fallback;
		}

		// ✅ cachear resultado
		exampleMap.put(type, example);

		return example;
	}

	// ✅ =========================
	// ✅ EXAMPLE
	// ✅ =========================
	public Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

		exampleMap = new LinkedHashMap<>();
		Map<String, Object> example = new LinkedHashMap<>();

		// ✅ resetear contexto por objeto
		dataContext = new LinkedHashMap<>();

		// ✅ recorrer campos
		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = parserUtil.resolveJsonName(field, var.getNameAsString());

				// =========================================================
				// ✅ tipo del campo
				// =========================================================
				String rawType = field.getElementType().asString();

				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;

				String type = parserUtil.resolveFinalType(cleanType);

				// =========================================================
				// ✅ 1. LISTAS
				// =========================================================
				if (cleanType.contains("List<") || rawType.contains("List<")) {

					String generic = parserUtil.extractGeneric(cleanType);
					Object nested = buildExampleFromType(generic);

					example.put(name, List.of(nested));
					return;
				}

				// =========================================================
				// ✅ 2. OBJETOS
				// =========================================================
				if (!typeUtil.isPrimitive(type)) {

					Object nested = buildExampleFromType(type);
					example.put(name, nested);
					return;
				}

				// =========================================================
				// ✅ 3. SEMÁNTICA
				// =========================================================
				Object value = generateSmartExample(name);

				if (value != null) {

					// ✅ adaptar tipo
					if (typeUtil.isNumericType(type) && value instanceof String) {
						try {
							String numeric = value.toString().replaceAll("\\D", "");
							if (!numeric.isEmpty()) {
								value = Integer.parseInt(numeric);
							}
						} catch (Exception ignored) {
						}
					}

					example.put(name, value);
					return;
				}

				// =========================================================
				// ✅ 4. FALLBACK
				// =========================================================
				example.put(name, resolveValueByType(type));
			});
		});

		return example;
	}

	private Object resolveValueByType(String type) {

		if (type.equals("Boolean") || type.equals("boolean"))
			return true;

		if (type.equals("Integer") || type.equals("int"))
			return 1;

		if (type.equals("Long") || type.equals("long"))
			return 1L;

		if (type.equals("Double") || type.equals("double"))
			return 100.5;

		return "string";
	}

	private String normalizeName(String name) {

		if (name == null)
			return "";

		String lower = name.toLowerCase();

		List<Map.Entry<String, String>> entries = new ArrayList<>(ABBREV_MAP.entrySet());

		// ✅ ordenar por tamaño (evita choques tipo id vs identificacion)
		entries.sort((a, b) -> b.getKey().length() - a.getKey().length());

		for (Map.Entry<String, String> entry : entries) {

			String abbr = entry.getKey();
			String full = entry.getValue();

			// ✅ REEMPLAZO GLOBAL (FIX IMPORTANTE)
			lower = lower.replace(abbr, full);
		}

		return lower;
	}

	private List<String> tokenize(String name) {

		if (name == null)
			return List.of();

		// ✅ 1. NORMALIZAR (usa ABBREV_MAP)
		String normalized = normalizeName(name);

		// ✅ 2. separar camelCase
		String withSpaces = normalized.replaceAll("([a-z])([A-Z])", "$1 $2");

		// ✅ 3. split base
		List<String> baseTokens = Arrays.stream(withSpaces.toLowerCase().split("[^a-z0-9]+")).filter(p -> !p.isBlank())
				.collect(Collectors.toList());

		// ✅ 4. EXPANSIÓN SEMÁNTICA (🔥 CLAVE)
		List<String> expanded = new ArrayList<>(baseTokens);

		for (String token : baseTokens) {

			// ✅ expansión por abreviaturas
			if (token.contains("cod"))
				expanded.add("codigo");
			if (token.contains("prod"))
				expanded.add("producto");
			if (token.contains("emp"))
				expanded.add("empresa");
			if (token.contains("pais"))
				expanded.add("pais");
			if (token.contains("mon"))
				expanded.add("moneda");
			if (token.contains("ident"))
				expanded.add("identificacion");
			if (token.contains("pers"))
				expanded.add("persona");
			if (token.contains("raz"))
				expanded.add("razon");

			// ✅ expansión directa
			if (token.contains("codigo"))
				expanded.add("codigo");
			if (token.contains("producto"))
				expanded.add("producto");
			if (token.contains("empresa"))
				expanded.add("empresa");
			if (token.contains("moneda"))
				expanded.add("moneda");
			if (token.contains("identificacion"))
				expanded.add("identificacion");
		}

		return expanded;
	}

	private static void loadAbbreviations() {

		try (java.io.InputStream is = OpenApiGeneratorService.class.getClassLoader()
				.getResourceAsStream("abbreviations.txt")) {

			if (is == null) {
				throw new RuntimeException("❌ No se encontró abbreviations.txt en resources");
			}

			new java.io.BufferedReader(new java.io.InputStreamReader(is)).lines().map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#")).forEach(line -> {

						String[] parts = line.split("=");

						if (parts.length == 2) {
							ABBREV_MAP.put(parts[0].toLowerCase(), parts[1].toLowerCase());
						}
					});

			System.out.println("✅ Abreviaturas cargadas: " + ABBREV_MAP.size());

		} catch (Exception e) {
			throw new RuntimeException("Error cargando abbreviations.txt", e);
		}
	}

}
