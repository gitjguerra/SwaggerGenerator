package com.mercantil.swaggergenerator.component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.service.OpenApiGeneratorService;
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

	// ✅ mapa de ejemplos (CACHE AISLADO POR EJECUCIÓN)
	private Map<String, Object> exampleMap = new LinkedHashMap<>();

	// ✅ mapa de schemas
	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
		this.schemaMap = schemaMap;

		// 🔥 FIX CRÍTICO: limpiar cache COMPLETO
		this.exampleMap = new LinkedHashMap<>();
	}

	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();
	static {
		loadAbbreviations();
	}

	// =========================================================
	// ✅ GENERADOR PRINCIPAL (FIX TOTAL)
	// =========================================================
	public Object buildExampleFromType(String type) {

		if ("HeaderEntrada".equals(type))
			return headerProvider.buildHeaderEntrada();

		if ("HeaderSalida".equals(type))
			return headerProvider.buildHeaderSalida();

		if (type == null)
			return null;

		if (type.equals("byte") || type.equals("byte[]"))
			return "base64-string";

		// 🔥 FIX: CLAVE DE CACHE AISLADA
		String cacheKey = type + "_CTX_" + System.identityHashCode(dataContext);

		if (exampleMap.containsKey(cacheKey)) {
			return exampleMap.get(cacheKey);
		}

		Map<String, Object> schema = schemaMap.get(type);

		if (schema == null) {
			System.out.println("⚠️ No schema encontrado para: " + type);
			return new LinkedHashMap<>();
		}

		// ✅ ENUM
		if (schema.containsKey("enum")) {
			List<?> values = (List<?>) schema.get("enum");
			if (!values.isEmpty())
				return values.get(0);
		}

		// ✅ RESOLVER REF ROOT
		if (schema.containsKey("$ref")) {

			String ref = schema.get("$ref").toString();
			String refType = ref.substring(ref.lastIndexOf("/") + 1);

			return buildExampleFromType(refType);
		}

		Map<String, Object> example = new LinkedHashMap<>();

		Object propsObj = schema.get("properties");

		// =====================================================
		// 🔥 FIX CRÍTICO: SOPORTE HERENCIA (allOf)
		// =====================================================
		if (!(propsObj instanceof Map)) {

			Map<String, Object> fallback = new LinkedHashMap<>();

			if (schema.containsKey("allOf")) {

				List<?> allOf = (List<?>) schema.get("allOf");

				for (Object obj : allOf) {

					if (obj instanceof Map) {

						Map<String, Object> sub = (Map<String, Object>) obj;

						if (sub.containsKey("$ref")) {

							String ref = sub.get("$ref").toString();
							String refType = ref.substring(ref.lastIndexOf("/") + 1);

							Object nested = buildExampleFromType(refType);

							if (nested instanceof Map) {
								fallback.putAll((Map<String, Object>) nested);
							}
						}

						// ✅ INLINE PROPERTIES
						if (sub.containsKey("properties")) {

							Map<String, Object> inlineProps = (Map<String, Object>) sub.get("properties");

							inlineProps.forEach((k, v) -> {
								fallback.put(k, generateSmartExample(k) != null ? generateSmartExample(k) : "string");
							});
						}
					}
				}
			}

			return fallback;
		}

		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((key, val) -> {

			if (!(val instanceof Map))
				return;

			Map<String, Object> prop = (Map<String, Object>) val;

			// ✅ REF
			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				example.put(key, buildExampleFromType(refType));
				return;
			}

			// ✅ ARRAY
			if ("array".equals(prop.get("type"))) {

				Object items = prop.get("items");

				if (items instanceof Map) {

					Map<?, ?> itemMap = (Map<?, ?>) items;

					if (itemMap.containsKey("$ref")) {

						String ref = itemMap.get("$ref").toString();
						String refType = ref.substring(ref.lastIndexOf("/") + 1);

						example.put(key, List.of(buildExampleFromType(refType)));

					} else {

						example.put(key, List.of("string"));
					}
				}
				return;
			}

			// ✅ PRIMITIVO
			Object value = generateSmartExample(key);

			if (value != null)
				example.put(key, value);
			else
				example.put(key, "string");
		});

		// =====================================================
		// 🔥 FIX FINAL: EVITA OBJETO VACÍO
		// =====================================================
		if (example.isEmpty()) {

			Map<String, Object> fallback = new LinkedHashMap<>();

			props.keySet().forEach(k -> fallback.put(k, "string"));

			return fallback;
		}

		exampleMap.put(cacheKey, example);

		return example;
	}

	// =========================================================
	// ✅ SMART EXAMPLE
	// =========================================================
	public Object generateSmartExample(String name) {

		if (name == null)
			return null;

		String rawLower = name.toLowerCase();
		String lower = normalizeName(name);
		List<String> tokens = tokenize(name);

		// =========================================================
		// ✅ CONTEXTO BASE
		// =========================================================
		String nombre = (String) dataContext.computeIfAbsent("nombre", k -> "Juan");
		String apellido = (String) dataContext.computeIfAbsent("apellido", k -> "Perez");

		// =========================================================
		// ✅ FECHAS
		// =========================================================
		if (rawLower.contains("fch") || lower.contains("fecha")) {
			return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
		}

		// =========================================================
		// ✅ HORA (hh)
		// =========================================================
		if (rawLower.contains("hh") || lower.contains("hora")) {
			return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
		}

		// =========================================================
		// ✅ MONTO (mto)
		// =========================================================
		if (rawLower.contains("mto") || tokens.contains("monto")) {
			return 1500.75;
		}

		// =========================================================
		// ✅ CANTIDAD (cant)
		// =========================================================
		if (rawLower.contains("cant") || tokens.contains("cantidad")) {
			return 7;
		}

		// =========================================================
		// ✅ TIPO
		// =========================================================
		if (tokens.contains("tipo")) {
			return "1";
		}

		// =========================================================
		// ✅ ESTATUS
		// =========================================================
		if (lower.contains("estatus") || lower.contains("estado")) {
			return "ACTIVO";
		}

		// =========================================================
		// ✅ IDENTIFICACIÓN
		// =========================================================
		if (lower.contains("tipoid")) {
			return "V";
		}

		if (lower.contains("nroid") || (tokens.contains("nro") && tokens.contains("id"))) {
			return 12345678;
		}

		if (tokens.contains("id")) {
			return 123456;
		}

		// =========================================================
		// ✅ NOMBRES
		// =========================================================
		if (lower.contains("primernom"))
			return nombre;
		if (lower.contains("segundonom"))
			return "Carlos";

		// =========================================================
		// ✅ APELLIDOS
		// =========================================================
		if (lower.contains("primerapell"))
			return apellido;
		if (lower.contains("segundoapell"))
			return "Gomez";

		if (lower.contains("apellcasada"))
			return "de " + apellido;

		// =========================================================
		// ✅ EMAIL COHERENTE
		// =========================================================
		if (lower.contains("email") || lower.contains("correo")) {
			return nombre.toLowerCase() + "." + apellido.toLowerCase() + "@mercantil.com";
		}

		// =========================================================
		// ✅ TELÉFONOS
		// =========================================================
		if (lower.contains("celular"))
			return "04141234567";
		if (lower.contains("telf"))
			return "02121234567";

		// =========================================================
		// ✅ CUENTA
		// =========================================================
		if (lower.contains("cuenta") || lower.contains("cta")) {
			return "01020123456789012345";
		}

		// =========================================================
		// ✅ TARJETA
		// =========================================================
		if (lower.contains("tarj")) {
			return generateCardNumber();
		}

		// =========================================================
		// ✅ PRODUCTO INTELIGENTE
		// =========================================================
		if (lower.contains("producto") && !lower.startsWith("cod")) {

		    String producto = (String) dataContext.computeIfAbsent("producto", k -> "CUENTA");

		    switch (producto) {
		        case "CUENTA":
		            return "CUENTA CORRIENTE";
		        case "TARJETA":
		            return "TARJETA DE CRÉDITO";
		        default:
		            return "CUENTA CORRIENTE";
		    }
		}

		// =========================================================
		// ✅ CODIGO PRODUCTO CONSISTENTE
		// =========================================================
		if (lower.startsWith("codprod") || lower.contains("codproduc")) {

		    String producto = (String) dataContext.computeIfAbsent("producto", k -> "CUENTA");

		    switch (producto) {
		        case "CUENTA": return "01";
		        case "TARJETA": return "02";
		        default: return "01";
		    }
		}

		// =========================================================
		// ✅ DIRECCIÓN
		// =========================================================
		if (lower.contains("direcc"))
			return "Av. Francisco de Miranda";
		if (lower.contains("ciudad"))
			return "Caracas";

		// =========================================================
		// ✅ CÓDIGOS (🔥 MUY IMPORTANTE)
		// =========================================================
		if (lower.startsWith("cod") || tokens.contains("codigo")) {

			if (lower.contains("pais"))
				return "VE";
			if (lower.contains("empresa"))
				return "0108";
			if (lower.contains("canal"))
				return "0006";
			if (lower.contains("producto"))
				return "02";

			return "01";
		}

		// =========================================================
		// ✅ UBICACIÓN
		// =========================================================
		if (lower.contains("zona"))
			return "1010";
		if (lower.contains("edo"))
			return "DC";
		if (lower.contains("munic"))
			return "Libertador";
		if (lower.contains("parroq"))
			return "Chacao";

		// =========================================================
		// ✅ GENERO
		// =========================================================
		if (lower.contains("genero"))
			return "M";

		// =========================================================
		// ✅ NUMEROS GENERALES (nroX)
		// =========================================================
		if (lower.contains("nro") || lower.contains("numero")) {
			return 12345;
		}

		// =========================================================
		// ✅ DESCRIPCIONES
		// =========================================================
		if (lower.contains("descrip")) {
			return "Descripción generada";
		}

		// =========================================================
		return null;
	}

	// =========================================================
	// ✅ BUILD FROM CLASS
	// =========================================================
	public Object buildExampleFromClass(ClassOrInterfaceDeclaration clazz) {

		// 🔥 FIX CRÍTICO
		exampleMap = new LinkedHashMap<>();
		dataContext = new LinkedHashMap<>();

		Map<String, Object> example = new LinkedHashMap<>();

		clazz.getFields().forEach(field -> {

			field.getVariables().forEach(var -> {

				String name = parserUtil.resolveJsonName(field, var.getNameAsString());

				String rawType = field.getElementType().asString();
				boolean isOptional = rawType.startsWith("Optional<");

				String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;
				String type = parserUtil.resolveFinalType(cleanType);

				if (cleanType.contains("List<")) {

					String generic = parserUtil.extractGeneric(cleanType);
					example.put(name, List.of(buildExampleFromType(generic)));
					return;
				}

				if (!typeUtil.isPrimitive(type)) {

					example.put(name, buildExampleFromType(type));
					return;
				}

				Object value = generateSmartExample(name);

				if (value != null)
					example.put(name, value);
				else
					example.put(name, "string");
			});
		});

		return example;
	}

	// =========================================================

	private static void loadAbbreviations() {

		try (var is = OpenApiGeneratorService.class.getClassLoader().getResourceAsStream("abbreviations.txt")) {

			if (is == null)
				return;

			new java.io.BufferedReader(new java.io.InputStreamReader(is)).lines().map(String::trim).forEach(line -> {

				if (!line.isEmpty() && line.contains("=")) {

					String[] parts = line.split("=");
					ABBREV_MAP.put(parts[0], parts[1]);
				}
			});

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String normalizeName(String name) {

		if (name == null)
			return "";

		String lower = name.toLowerCase();

		for (Map.Entry<String, String> e : ABBREV_MAP.entrySet()) {
			lower = lower.replace(e.getKey(), e.getValue());
		}

		return lower;
	}

	private List<String> tokenize(String name) {

		if (name == null)
			return List.of();

		String normalized = normalizeName(name);

		return Arrays.stream(normalized.split("[^a-z0-9]+")).filter(p -> !p.isBlank()).collect(Collectors.toList());
	}

	private String generateCardNumber() {

		// 🔹 Prefijo Visa (puedes cambiar por Mastercard = 5, Amex = 34/37)
		String prefix = "4";

		// 🔹 Generar 15 dígitos base (sin check digit)
		StringBuilder number = new StringBuilder(prefix);

		while (number.length() < 15) {
			number.append((int) (Math.random() * 10));
		}

		// 🔹 Calcular dígito verificador (Luhn)
		int sum = 0;
		boolean alternate = true;

		for (int i = number.length() - 1; i >= 0; i--) {
			int n = Character.getNumericValue(number.charAt(i));

			if (alternate) {
				n *= 2;
				if (n > 9)
					n -= 9;
			}

			sum += n;
			alternate = !alternate;
		}

		int checkDigit = (10 - (sum % 10)) % 10;

		number.append(checkDigit);

		return number.toString();
	}

}