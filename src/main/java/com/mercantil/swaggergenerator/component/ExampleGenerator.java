package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
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

		// ✅ ORIGINAL (🔥 CLAVE)
		String rawLower = name.toLowerCase();

		// ✅ NORMALIZADO (para semántica expandida)
		String normalized = normalizeName(name);
		String lower = normalized.toLowerCase();

		// ✅ tokenización
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
		// ✅ 2. REGLAS FUERTES (NORMALIZED)
		// =========================================================
		if (lower.contains("codigoproducto"))
			return "02";

		if (lower.contains("codigopais"))
			return "VE";

		if (lower.contains("codigoempresa"))
			return "0108";

		if (lower.contains("codigorazon"))
			return "01";

		// =========================================================
		// ✅ 3. SEMÁNTICA (🔥 PRIORIDAD ALTA)
		// =========================================================

		// ✅ FECHAS (usar raw + semantic)
		if (rawLower.contains("fch") || lower.contains("fecha")) {
			return "20251226";
		}

		// ✅ NÚMERO DE CUENTA
		if (rawLower.contains("nrocuenta") || lower.contains("numerocuenta") || lower.contains("numcuenta")
				|| (tokens.contains("cuenta") && tokens.contains("nro"))) {

			return 1242034900;
		}

		// ✅ EMAIL
		if (rawLower.contains("email") || lower.contains("correo")) {
			return "jperez@email.com";
		}

		// ✅ NOMBRES PERSONA
		if (rawLower.contains("primernom") || lower.contains("primernombre")) {
			return "Juan";
		}

		if (rawLower.contains("segundonom") || lower.contains("segundonombre")) {
			return "Carlos";
		}

		// ✅ APELLIDOS
		if (rawLower.contains("primerapell") || lower.contains("primerapellido")) {
			return "Pérez";
		}

		if (rawLower.contains("segundoapell") || lower.contains("segundoapellido")) {
			return "López";
		}

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
		// ✅ PERFIL / KYC
		// =========================================================

		// ✅ SEXO
		if (rawLower.contains("sexo")) {
			return "masculino";
		}

		// ✅ ESTADO CIVIL
		if (rawLower.contains("estadocivil")) {
			return "casado";
		}

		// ✅ CARGA FAMILIAR
		if (lower.contains("cargafamiliar")) {
			return "5";
		}

		// ✅ PAÍS RESIDENCIA
		if (lower.contains("paisresidencia")) {
			return "VE";
		}

		// ✅ AÑOS / ANTIGÜEDAD / TIEMPO
		if (lower.contains("anio") || lower.contains("anios") || lower.contains("años")) {
			return "10";
		}

		// ✅ NIVEL EDUCATIVO
		if (lower.contains("niveleducativo")) {
			return "universitario";
		}

		// ✅ FECHAS LABORALES
		if (lower.contains("fechaingresolaboral")) {
			return "20251226";
		}

		// ✅ PORCENTAJE
		if (lower.contains("porcentaje")) {
			return "10";
		}

		// ✅ SUELDO / INGRESOS
		if (lower.contains("sueldo") || lower.contains("ingresos")) {
			return "700000";
		}

		// ✅ EMPRESA / NOMBRE COMERCIAL
		if (lower.contains("nombrecomercial")) {
			return "MiEmpresa C.A.";
		}

		// ✅ MONTO VENTAS / OTROS INGRESOS
		if (lower.contains("montoventas") || lower.contains("montootros")) {
			return "1500000";
		}

		// ✅ EMPRESAS LABORALES
		if (lower.contains("empresas")) {
			return "01";
		}

		// ✅ CANTIDAD EMPLEADOS
		if (lower.contains("cantidadempleados") || lower.contains("rangos")) {
			return 77;
		}

		// ✅ CARGOS / ACTIVIDAD
		if (lower.contains("cargo")) {
			return "Analista";
		}

		if (lower.contains("actividad")) {
			return "Servicios";
		}

		// ✅ CATEGORÍA / PROFESIÓN
		if (lower.contains("categoria") || lower.contains("profesion")) {
			return "0001";
		}

		// ✅ FUNCIONARIO / DIVISION / UNIDAD
		if (lower.contains("codigofuncionario") || lower.contains("division") || lower.contains("unidadorganizativa")) {
			return "0001";
		}

		// ✅ ACTIVO / CONDICIÓN
		if (lower.contains("codigoactivo") || lower.contains("codigocondicionactivo")) {
			return "01";
		}

		// ✅ ACREEDOR
		if (lower.contains("acreedor")) {
			return "Banco Mercantil";
		}

		// ✅ TELÉFONO ACREEDOR
		if (lower.contains("telefonoacreedor")) {
			return "04141234567";
		}

		// ✅ MEDIO COMUNICACIÓN
		if (lower.contains("mediocomunicacion")) {
			return "EMAIL";
		}

		// ✅ REGISTRO MERCANTIL (datos legales)
		if (lower.contains("folio")) {
			return "17";
		}

		if (lower.contains("tomo")) {
			return "17";
		}

		if (lower.contains("protocolo") || lower.contains("numerotribunal")) {
			return "01";
		}

		if (lower.contains("circunscripcion")) {
			return "Caracas";
		}

		// ✅ CAPITAL
		if (lower.contains("capitalsuscrito")) {
			return "200000";
		}

		if (lower.contains("capitalpagado")) {
			return "100000";
		}

		// ✅ TIPO EMPRESA
		if (lower.contains("tipoempresa")) {
			return "CA";
		}

		// =========================================================
		// ✅ PRODUCTO
		// =========================================================
		if (rawLower.contains("nroproducto") || rawLower.equals("prod") || rawLower.contains("tpprod")
				|| rawLower.contains("typprod") || lower.contains("producto")) {

			return "02";
		}

		// =========================================================
		// ✅ CÓDIGOS
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

				String lowerKey = key.toLowerCase();

				// ✅ FORZAR FECHAS (🔥 FIX FINAL)
				if (lowerKey.contains("fch") || lowerKey.contains("fecha")) {
					example.put(key, "20251226");
					return;
				}

				// ✅ SI HAY SEMÁNTICA NORMAL
				if (value != null) {
					example.put(key, value);
				} else {
					example.put(key, "string");
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
				// ✅ 3. buildExampleFromClass SEMÁNTICA
				// =========================================================
				Object value = generateSmartExample(name);

				if (value != null) {

					// ✅ adaptar tipo

					String fieldLower = name.toLowerCase();

					// 🔥 detectar campos de fecha
					boolean isDateField = fieldLower.contains("fch") || fieldLower.contains("fecha");

					// ✅ SOLO convertir si NO es fecha
					if (!isDateField && typeUtil.isNumericType(type) && value instanceof String) {
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
