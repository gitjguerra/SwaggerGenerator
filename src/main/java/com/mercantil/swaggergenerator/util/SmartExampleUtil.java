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
 * Genera valores realistas bancarios
 *
 * =========================================================
 */
public class SmartExampleUtil {

	// =========================================================
	// ✅ MAPA DE ABREVIATURAS (GLOBAL)
	// =========================================================
	private static final Map<String, String> ABBREV_MAP = new LinkedHashMap<>();

	static {
		loadAbbreviations();
	}

	// =========================================================
	// ✅ MÉTODO PRINCIPAL
	// =========================================================
	public static Object generate(String name, Map<String, Object> dataContext) {

		if (name == null)
			return null;

		String rawLower = name.toLowerCase();
		String lower = normalizeName(name);

		// ✅ tokens desde camelCase original
		List<String> tokens = tokenize(name);

		// ✅ tokens desde normalizado (cta → cuenta)
		tokens.addAll(tokenize(lower));

		// =========================================================
		// ✅ CONTEXTO CONSISTENTE
		// =========================================================
		String nombre = (String) dataContext.computeIfAbsent("nombre", k -> "Juan");
		String apellido = (String) dataContext.computeIfAbsent("apellido", k -> "Perez");

		// =========================================================
		// ✅ AUTH / TOKEN
		// =========================================================
		if (tokens.contains("token") || tokens.contains("auth")) {
			return "Bearer WjY3MjBEMDE6WjAxRDY3MjA=";
		}

		// =========================================================
		// ✅ FECHA
		// =========================================================
		if (rawLower.contains("fch") || lower.contains("fecha")) {
			return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		}

		// =========================================================
		// ✅ HORA
		// =========================================================
		if (rawLower.contains("hh") || lower.contains("hora")) {
			return LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
		}

		// =========================================================
		// ✅ MONTO
		// =========================================================
		if (rawLower.contains("mto") || tokens.contains("monto"))
			return 1500.75;

		// =========================================================
		// ✅ CANTIDAD
		// =========================================================
		if (rawLower.contains("cant"))
			return 7;

		// =========================================================
		// ✅ TASA
		// =========================================================
		if (tokens.contains("tasa"))
			return "0.16";

		// =========================================================
		// ✅ IDENTIFICACIÓN
		// =========================================================
		if (tokens.contains("cedula") || tokens.contains("rif"))
			return 124332657;

		// =========================================================
		// ✅ TIPO
		// =========================================================
		if (tokens.contains("tipo"))
			return "1";

		// =========================================================
		// ✅ INTENTOS DISPONIBLES
		// =========================================================
		if (lower.contains("intentos") && tokens.contains("disponibles")) {
			return "3";
		}

		// =========================================================
		// ✅ SISTEMA ORIGEN
		// =========================================================
		if (lower.contains("origen"))
			return "MPLUS";

		// =========================================================
		// ✅ ACCIÓN
		// =========================================================
		if (tokens.contains("accion") && tokens.contains("requer"))
			return "1";

		// =========================================================
		// ✅ ESTATUS
		// =========================================================
		if (lower.contains("estatus") || lower.contains("estado"))
			return "ACTIVO";

		if (lower.contains("tipoid"))
			return "V";

		if (tokens.contains("id"))
			return 123456;

		if (lower.contains("canal"))
			return "0006";

		if (lower.contains("subcanal"))
			return "0009";

		// =========================================================
		// ✅ NOMBRES
		// =========================================================
		if (lower.contains("nombenef") || tokens.contains("cliente")) {
			return nombre + " " + apellido;
		}

		// =========================================================
		// ✅ OBSERVACIONES
		// =========================================================
		if (lower.startsWith("observ"))
			return "Observaciones ...";

		// =========================================================
		// ✅ FRECUENCIA
		// =========================================================
		if (lower.equals("frec"))
			return 7;

		// =========================================================
		// ✅ STATUS AFILIACION
		// =========================================================
		if (lower.contains("statusafili"))
			return "01";

		// =========================================================
		// ✅ INDICADORES
		// =========================================================
		if (lower.contains("indic"))
			return "01";

		// =========================================================
		// ✅ EMAIL
		// =========================================================
		if (lower.contains("email")) {
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
		// ✅ NUMERO PERSONA (PRIORIDAD ALTA 🔥)
		// nroPer, numeroPersona
		// =========================================================
		if (tokens.contains("per") || tokens.contains("persona")) {

			if (tokens.contains("nro") || tokens.contains("numero")) {
				return 11844825;
			}
		}

		// =========================================================
		// ✅ NUMERO CUENTA (PRIORIDAD ALTA 🔥)
		// nroCta, numeroCuenta
		// =========================================================
		if ((tokens.contains("cuenta") || tokens.contains("cta"))
				&& (tokens.contains("numero") || tokens.contains("nro"))) {

			return "01020123456789012345";
		}

		// =========================================================
		// ✅ SALDOS (GENERAL 🔥)
		// saldoDisp, saldoBloq, saldoActual, etc.
		// =========================================================
		if (tokens.contains("saldo")) {
			return "1000,00";
		}

		// =========================================================
		// ✅ PAIS CUENTA
		// =========================================================
		if (tokens.contains("pais") && (tokens.contains("cuenta") || tokens.contains("cta"))) {
			return "USA";
		}

		// =========================================================
		// ✅ MONEDA CUENTA
		// =========================================================
		if (tokens.contains("moneda") && (tokens.contains("cuenta") || tokens.contains("cta"))) {
			return "USD";
		}

		// =========================================================
		// ✅ TIPO CUENTA
		// =========================================================
		if ((tokens.contains("tipo") || lower.contains("tipocta"))
				&& (tokens.contains("cuenta") || tokens.contains("cta"))) {
			return "AHO";
		}

		// =========================================================
		// ✅ BANCO DESTINO
		// =========================================================
		if ((tokens.contains("banco") && tokens.contains("dest")) || lower.contains("bancodest")
				|| lower.contains("bancodestino")) {
			return "0114";
		}

		// =========================================================
		// ✅ SWIFT
		// =========================================================
		if (lower.contains("swift")) {
			return "BOFAUS3N";
		}

		// =========================================================
		// ✅ IBAN
		// =========================================================
		if (lower.contains("iban")) {
			return "US12345678901234567890";
		}

		// =========================================================
		// ✅ BANCO
		// =========================================================
		if (lower.equals("bco") || tokens.contains("banco")) {
			return "0105";
		}

		// =========================================================
		// ✅ APLICACION
		// =========================================================
		if (lower.equals("aplic") || tokens.contains("aplicacion")) {
			return "01";
		}

		// =========================================================
		// ✅ CODIGO GRUPO
		// =========================================================
		if (lower.contains("codgrupo") || (tokens.contains("codigo") && tokens.contains("grupo"))) {
			return "01";
		}

		// =========================================================
		// ✅ PASSWORD
		// =========================================================
		if (lower.contains("password")) {
			return "hsgr42$%&";
		}

		// =========================================================
		// ✅ NEW PASSWORD
		// =========================================================
		if (lower.contains("newpassword") || (tokens.contains("new") && tokens.contains("password"))) {
			return "87262%3$6*";
		}

		// =========================================================
		// ✅ RECURSO
		// =========================================================
		if (lower.contains("recurso")) {
			return "servicio";
		}

		// =========================================================
		// ✅ PROFILE
		// =========================================================
		if (lower.contains("profile") || lower.contains("perfil")) {
			return "analista";
		}

		// =========================================================
		// ✅ CUENTA (GENÉRICO)
		// =========================================================
		if (lower.contains("cuenta"))
			return "01020123456789012345";

		// =========================================================
		// ✅ TARJETA
		// =========================================================
		if (lower.contains("tarj")) {

			if (tokens.contains("deb"))
				return generateCardNumber(18, "5");

			if (tokens.contains("cred"))
				return generateCardNumber(16, "4");

			return generateCardNumber(16, "4");
		}

		// =========================================================
		// ✅ PRODUCTO
		// =========================================================
		if (lower.contains("producto"))
			return "CUENTA CORRIENTE";

		// =========================================================
		// ✅ CLAVE
		// =========================================================
		if (lower.contains("clave") || lower.equals("clv"))
			return "1234";

		// =========================================================
		// ✅ CODIGO
		// =========================================================
		if (lower.startsWith("cod"))
			return 27;

		// =========================================================
		// ✅ UBICACION
		// =========================================================
		if (lower.contains("ciudad"))
			return "Caracas";

		// =========================================================
		// ✅ NUMEROS GENERICOS
		// =========================================================
		if ((lower.contains("nro") || lower.contains("numero"))
				&& !(tokens.contains("cuenta") || tokens.contains("persona") || tokens.contains("per"))) {
			return "12345";
		}

		// =========================================================
		// ✅ DESCRIPCIÓN
		// =========================================================
		if (lower.contains("descrip"))
			return "Descripción generada";

		return null;
	}

	// =========================================================
	// ✅ NORMALIZE (aplica abbreviations)
	// =========================================================
	private static String normalizeName(String name) {

		if (name == null)
			return "";

		String lower = name.toLowerCase();

		for (Map.Entry<String, String> e : ABBREV_MAP.entrySet()) {
			lower = lower.replace(e.getKey(), e.getValue());
		}

		return lower;
	}

	// =========================================================
	// ✅ TOKENIZE (camelCase + limpieza)
	// =========================================================
	private static List<String> tokenize(String name) {

		if (name == null)
			return Collections.emptyList();

		String split = name.replaceAll("([a-z])([A-Z])", "$1 $2");

		return Arrays.stream(split.toLowerCase().split("[^a-z0-9]+")).filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}

	// =========================================================
	// ✅ GENERADOR TARJETA (LUHN)
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

	// =========================================================
	// ✅ LOAD ABBREVIATIONS (desde resources)
	// =========================================================
	private static void loadAbbreviations() {

		try (var is = SmartExampleUtil.class.getClassLoader().getResourceAsStream("abbreviations.txt")) {

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
}