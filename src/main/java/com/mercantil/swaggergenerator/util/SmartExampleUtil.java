package com.mercantil.swaggergenerator.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ========================================================= ✅ SMART EXAMPLE
 * UTIL =========================================================
 *
 * Motor de generación de ejemplos basado en semántica del nombre.
 *
 * ✔ Soporta tokens (camelCase → palabras) ✔ Usa abbreviations (cta → cuenta) ✔
 * Genera valores realistas bancarios ✔ Respeta schemaType OpenAPI
 *
 * =========================================================
 */
public class SmartExampleUtil {

	// =========================================================
	// ✅ MAPA DE ABREVIATURAS
	// =========================================================
	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();

	static {
		loadAbbreviations();
	}

	// =========================================================
	// ✅ MÉTODO PRINCIPAL
	// =========================================================
	public static Object generate(String name, String schemaType, Map<String, Object> dataContext) {

		if (name == null) {

			return null;
		}

		if (dataContext == null) {

			dataContext = new LinkedHashMap<>();
		}

		String rawLower = name.toLowerCase();

		String lower = normalizeName(name);

		// ✅ tokens camelCase
		List<String> tokens = tokenize(name);

		// ✅ tokens normalizados
		tokens.addAll(tokenize(lower));

		// =====================================================
		// ✅ PRIORIDAD OPENAPI TYPE
		// =====================================================
		boolean numericField = "integer".equals(schemaType) || "number".equals(schemaType);

		boolean stringField = "string".equals(schemaType);

		boolean booleanField = "boolean".equals(schemaType);

		// =====================================================
		// ✅ CONTEXTO CONSISTENTE
		// =====================================================
		String nombre = (String) dataContext.computeIfAbsent("nombre", k -> "Juan");

		String apellido = (String) dataContext.computeIfAbsent("apellido", k -> "Perez");

		// =====================================================
		// ✅ BOOLEAN PRIORITY
		// =====================================================
		if (booleanField) {

			if (lower.contains("indic") || lower.contains("flag") || lower.contains("activo")
					|| lower.contains("enabled")) {

				return true;
			}
		}

		// =====================================================
		// ✅ AUTH / TOKEN
		// =====================================================
		if (lower.contains("tipotoken")) {

			return "Bearer";
		}

		if (lower.contains("accesstoken") || lower.contains("authtoken") || lower.contains("bearertoken")) {

			return "Bearer WjY3MjBEMDE6WjAxRDY3MjA=";
		}

		if (tokens.contains("token") || tokens.contains("auth")) {

			return "WjY3MjBEMDE6WjAxRDY3MjA=";
		}

		// =====================================================
		// ✅ FECHA
		// =====================================================
		if (rawLower.contains("fch") || lower.contains("fecha")) {

			return LocalDate.now()

					.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		}

		// =====================================================
		// ✅ HORA
		// =====================================================
		if (rawLower.contains("hh") || lower.contains("hora")) {

			return LocalTime.now()

					.format(DateTimeFormatter.ofPattern("HHmmss"));
		}

		// =====================================================
		// ✅ MONTO
		// =====================================================
		if (rawLower.contains("mto") || tokens.contains("monto")) {

			return numericField ? 1500.75 : "1500.75";
		}

		// =====================================================
		// ✅ CANTIDAD
		// =====================================================
		if (rawLower.contains("cant")) {

			return numericField ? 7 : "7";
		}

		// =====================================================
		// ✅ TASA
		// =====================================================
		if (tokens.contains("tasa")) {

			return numericField ? 0.16 : "0.16";
		}

		// =====================================================
		// ✅ IDENTIFICACIÓN
		// =====================================================
		if (tokens.contains("cedula") || tokens.contains("rif")) {

			return numericField ? 124332657 : "124332657";
		}

		// =====================================================
		// ✅ TIPO IDENTIFICACIÓN
		// =====================================================
		if (lower.contains("tipoidentificacion") || lower.contains("tipoid")) {

			if (numericField) {

				return 1;
			}

			return "V";
		}

		// =====================================================
		// ✅ TIPO PERSONA
		// =====================================================
		if (lower.contains("tipoper")) {

			return numericField ? 1 : "1";
		}

		// =====================================================
		// ✅ TIPO GENERICO
		// =====================================================
		if (tokens.contains("tipo") && !tokens.contains("cuenta") && !tokens.contains("cta")) {

			return numericField ? 1 : "1";
		}

		// =====================================================
		// ✅ INTENTOS
		// =====================================================
		if (lower.contains("intentos") && tokens.contains("disponibles")) {

			return numericField ? 3 : "3";
		}

		// =====================================================
		// ✅ ORIGEN
		// =====================================================
		if (lower.contains("origen")) {

			if (numericField) {

				return 1;
			}

			return "MPLUS";
		}

		// =====================================================
		// ✅ INDICADOR ACCION
		// =====================================================
		if (lower.contains("indicaccion")) {

			if (numericField) {

				return 1;
			}

			return "P";
		}

		// =====================================================
		// ✅ ACCION
		// =====================================================
		if (tokens.contains("accion") && tokens.contains("requer")) {

			return numericField ? 1 : "1";
		}

		// =====================================================
		// ✅ ESTATUS
		// =====================================================
		if (lower.contains("estatus") || lower.contains("estado")) {

			return numericField ? 1 : "1";
		}

		// =====================================================
		// ✅ ID NAC
		// =====================================================
		if (lower.contains("idnac")) {

			return numericField ? 1 : "V";
		}

		// =====================================================
		// ✅ ID
		// =====================================================
		if (tokens.contains("id") && !lower.contains("tipoid") && !lower.contains("guid") && !lower.contains("uuid")) {

			return numericField ? 123456 : "123456";
		}

		// =====================================================
		// ✅ CANAL
		// =====================================================
		if (lower.contains("subcanal")) {

			return numericField ? 9 : "0009";
		}

		if (lower.contains("canal")) {

			return numericField ? 6 : "0006";
		}

		// =====================================================
		// ✅ NOMBRES
		// =====================================================
		if (tokens.contains("nom") || lower.contains("nombre")) {

			if (numericField) {

				return 1;
			}

			if (tokens.contains("primer")) {

				return nombre;
			}

			if (tokens.contains("segundo")) {

				return "Carlos";
			}

			if (lower.contains("comerc")) {

				return "Empresa XYZ";
			}

			return nombre;
		}

		// =====================================================
		// ✅ APELLIDOS
		// =====================================================
		if (tokens.contains("apell") || tokens.contains("apellido")) {

			if (numericField) {

				return 1;
			}

			if (tokens.contains("primer")) {

				return apellido;
			}

			if (tokens.contains("segundo")) {

				return "Gonzalez";
			}

			if (lower.contains("casada")) {

				return "de Perez";
			}

			return apellido;
		}

		// =====================================================
		// ✅ OBSERVACIONES
		// =====================================================
		if (lower.startsWith("observ")) {

			return numericField ? 1 : "Observaciones ...";
		}

		// =====================================================
		// ✅ FRECUENCIA
		// =====================================================
		if (lower.equals("frec")) {

			return numericField ? 7 : "7";
		}

		// =====================================================
		// ✅ STATUS AFILIACION
		// =====================================================
		if (lower.contains("statusafili")) {

			return numericField ? 1 : "01";
		}

		// =====================================================
		// ✅ INDICE / BUSQUEDA (FIX REAL)
		// =====================================================
		if (lower.contains("indice") || lower.contains("indbusq")) {

			if (numericField) {
				return 1;
			}

			return "1";
		}

		// =====================================================
		// ✅ INDICADORES
		// =====================================================
		if (lower.contains("indic")) {

			if (booleanField) {

				return true;
			}

			return numericField ? 1 : "01";
		}

		// =====================================================
		// ✅ EMAIL / CORREO
		// =====================================================
		if (lower.contains("email") || lower.contains("correo")) {

			if (numericField) {

				return 1;
			}

			return nombre.toLowerCase() + "." + apellido.toLowerCase() + "@mercantil.com";
		}

		// =====================================================
		// ✅ TELEFONOS
		// =====================================================
		if (lower.contains("celular")) {

			return "04141234567";
		}

		if (lower.contains("telf") || lower.contains("telefono")) {

			return "02121234567";
		}

		// =====================================================
		// ✅ NUMERO PERSONA
		// =====================================================
		if ((tokens.contains("per") || tokens.contains("persona"))

				&& (tokens.contains("nro") || tokens.contains("numero"))) {

			return numericField ? 11844825 : "11844825";
		}

		// =====================================================
		// ✅ NUMERO CUENTA
		// =====================================================
		if ((tokens.contains("cuenta") || tokens.contains("cta"))

				&& (tokens.contains("numero") || tokens.contains("nro"))) {

			return "01020123456789012345";
		}

		// =====================================================
		// ✅ SALDOS
		// =====================================================
		if (tokens.contains("saldo")) {

			return numericField ? 1000.00 : "1000.00";
		}

		// =====================================================
		// ✅ PAIS BANCO
		// =====================================================
		if (lower.contains("paisbanco")) {

			if (numericField) {

				return 1;
			}

			if (tokens.contains("ext") || tokens.contains("internacional")) {

				return "US";
			}

			return "VE";
		}

		// =====================================================
		// ✅ PAIS CUENTA
		// =====================================================
		if (tokens.contains("pais") && (tokens.contains("cuenta") || tokens.contains("cta"))) {

			if (numericField) {

				return 1;
			}

			return "USA";
		}

		// =====================================================
		// ✅ MONEDA CUENTA
		// =====================================================
		if (tokens.contains("moneda") && (tokens.contains("cuenta") || tokens.contains("cta"))) {

			if (numericField) {

				return 1;
			}

			return "USD";
		}

		// =====================================================
		// ✅ TIPO CUENTA
		// =====================================================
		if ((tokens.contains("tipo") || lower.contains("tipocta"))

				&& (tokens.contains("cuenta") || tokens.contains("cta"))) {

			if (numericField) {

				return 1;
			}

			return "AHO";
		}

		// =====================================================
		// ✅ BANCO DESTINO
		// =====================================================
		if ((tokens.contains("banco") && tokens.contains("dest"))

				|| lower.contains("bancodest") || lower.contains("bancodestino")) {

			return numericField ? 114 : "0114";
		}

		// =====================================================
		// ✅ SWIFT
		// =====================================================
		if (lower.contains("swift")) {

			if (numericField) {

				return 1;
			}

			return "BOFAUS3N";
		}

		// =====================================================
		// ✅ IBAN
		// =====================================================
		if (lower.contains("iban")) {

			if (numericField) {

				return 1;
			}

			return "US12345678901234567890";
		}

		// =====================================================
		// ✅ BANCO
		// =====================================================
		if (lower.equals("bco") || tokens.contains("banco")) {

			return numericField ? 105 : "0105";
		}

		// =====================================================
		// ✅ APLICACION
		// =====================================================
		if (lower.equals("aplic") || tokens.contains("aplicacion")) {

			return numericField ? 1 : "01";
		}

		// =====================================================
		// ✅ TIPO PRODUCTO RECEPTOR
		// =====================================================
		if (lower.contains("codtipoproducrecept")) {

			return numericField ? 1 : "TE";
		}

		// =====================================================
		// ✅ CODIGO GRUPO
		// =====================================================
		if (lower.contains("codgrupo") || (tokens.contains("codigo") && tokens.contains("grupo"))) {

			return numericField ? 1 : "01";
		}

		// =====================================================
		// ✅ PASSWORD
		// =====================================================
		if (lower.contains("password")) {

			if (numericField) {

				return 1234;
			}

			return "hsgr42$%&";
		}

		// =====================================================
		// ✅ NEW PASSWORD
		// =====================================================
		if (lower.contains("newpassword") || (tokens.contains("new") && tokens.contains("password"))) {

			if (numericField) {

				return 1234;
			}

			return "87262%3$6*";
		}

		// =====================================================
		// ✅ RECURSO
		// =====================================================
		if (lower.contains("recurso")) {

			if (numericField) {

				return 1;
			}

			return "servicio";
		}

		// =====================================================
		// ✅ PERFIL
		// =====================================================
		if (lower.contains("profile") || lower.contains("perfil")) {

			if (numericField) {

				return 1;
			}

			return "analista";
		}

		// =====================================================
		// ✅ CUENTA GENERICA
		// =====================================================
		if (lower.contains("cuenta")) {

			return "01020123456789012345";
		}

		// =====================================================
		// ✅ TARJETA
		// =====================================================
		if (lower.contains("tarj")) {

			return generateCardNumber(16, "4");
		}

		// =====================================================
		// ✅ PRODUCTO
		// =====================================================
		if (lower.contains("producto")) {

			if (numericField) {

				return 1;
			}

			return "CUENTA CORRIENTE";
		}

		// =====================================================
		// ✅ CLAVE
		// =====================================================
		if (lower.contains("clave") || lower.equals("clv")) {

			return numericField ? 1234 : "1234";
		}

		// =====================================================
		// ✅ CODIGO MONEDA
		// =====================================================
		if ((tokens.contains("cod") && tokens.contains("moneda")) || lower.equals("codmoneda")) {

			if (numericField) {

				return 1;
			}

			return "VEB";
		}

		// =====================================================
		// ✅ CODIGO
		// =====================================================
		if (lower.startsWith("cod")) {

			return numericField ? 27 : "27";
		}

		// =====================================================
		// ✅ CIUDAD
		// =====================================================
		if (lower.contains("ciudad")) {

			if (numericField) {

				return 1;
			}

			return "Caracas";
		}

		// =====================================================
		// ✅ NUMEROS GENERICOS
		// =====================================================
		if (lower.contains("nro") || lower.contains("numero")) {

			return numericField ? 12345 : "12345";
		}

		// =====================================================
		// ✅ DESCRIPCION
		// =====================================================
		if (lower.contains("descrip")) {

			if (numericField) {

				return 1;
			}

			return "Descripción generada";
		}

		// =====================================================
		// ✅ DEFAULTS POR TIPO
		// =====================================================
		if (numericField) {

			if ("integer".equals(schemaType)) {

				return 1;
			}

			return 1.0;
		}

		if (booleanField) {

			return true;
		}

		if (stringField) {

			return "valor";
		}

		return null;
	}

	// =========================================================
	// ✅ NORMALIZE
	// =========================================================
	private static String normalizeName(String name) {

		if (name == null) {

			return "";
		}

		String lower = name.toLowerCase();

		for (Map.Entry<String, String> e : ABBREV_MAP.entrySet()) {

			lower = lower.replace(e.getKey(), e.getValue());
		}

		return lower;
	}

	// =========================================================
	// ✅ TOKENIZE
	// =========================================================
	private static List<String> tokenize(String name) {

		if (name == null) {

			return Collections.emptyList();
		}

		String split = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		return Arrays.stream(split.toLowerCase().split("[^a-z0-9]+"))

				.filter(s -> !s.isEmpty())

				.collect(Collectors.toList());
	}

	// =========================================================
	// ✅ GENERADOR TARJETA
	// =========================================================
	private static String generateCardNumber(int length, String prefix) {

		StringBuilder number = new StringBuilder(prefix);

		while (number.length() < length - 1) {

			number.append((int) (Math.random() * 10));
		}

		int sum = 0;

		boolean alternate = true;

		for (int i = number.length() - 1; i >= 0; i--) {

			int n = Character.getNumericValue(number.charAt(i));

			if (alternate) {

				n *= 2;

				if (n > 9) {

					n -= 9;
				}
			}

			sum += n;

			alternate = !alternate;
		}

		int checkDigit = (10 - (sum % 10)) % 10;

		number.append(checkDigit);

		return number.toString();
	}

	// =========================================================
	// ✅ LOAD ABBREVIATIONS
	// =========================================================
	private static void loadAbbreviations() {

		try (var is = SmartExampleUtil.class

				.getClassLoader()

				.getResourceAsStream("abbreviations.txt")) {

			if (is == null) {

				return;
			}

			new java.io.BufferedReader(new java.io.InputStreamReader(is))

					.lines()

					.map(String::trim)

					.forEach(line -> {

						if (!line.isEmpty() && line.contains("=")) {

							String[] parts = line.split("=");

							ABBREV_MAP.put(parts[0], parts[1]);
						}
					});

		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}
}