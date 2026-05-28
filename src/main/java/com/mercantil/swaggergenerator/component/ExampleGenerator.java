package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.mercantil.swaggergenerator.util.AbbreviationUtil;

@Component
public class ExampleGenerator {

	// ✅ CONTEXTO
	private Map<String, Object> dataContext = new LinkedHashMap<>();

	private Map<String, Map<String, Object>> schemaMap = new LinkedHashMap<>();

	public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
		this.schemaMap = schemaMap;
	}

	public Object generateSmartExample(String name) {

	    String normalized = AbbreviationUtil.normalizeName(name);
	    List<String> tokens = AbbreviationUtil.tokenize(normalized);

	    // =========================================================
	    // ✅ 0. REGLAS DIRECTAS (🔥 PRIORIDAD MÁXIMA)
	    // =========================================================
	    switch (name) {

	        case "nroCuenta":
	            return "123456789012";
	        case "nroExt":
	            return "5342";	
	            
	        case "motivoServicio":
	            return "EJEMPLO";	            
	            
	        case "fchIni":
	        case "fchInicio":
	        case "fchRolIni":
	            return "01012026";

	        case "fchFin":
	        case "fchRolFin":
	            return "31122026";

	        case "tipoId":
	        case "tipoIdV":
	        case "tipoIdentificacion":
	            return "V";

	        case "codPais":
	        case "codPaisV":
	        case "codigoPais":
	            return "VE";

	        case "rol":
	            return "CLIENTE";
	            
	        case "nroTelf":
	            return "02126624076";	            
	            
	    }

	    // =========================================================
	    // ✅ 1. CONTEXTO
	    // =========================================================
	    for (Map.Entry<String, Object> entry : dataContext.entrySet()) {
	        if (tokens.contains(entry.getKey())) {
	            return entry.getValue();
	        }
	    }

	    // =========================================================
	    // ✅ 2. REGLAS FUERTES
	    // =========================================================
	    if (tokens.contains("codigo") && tokens.contains("producto"))
	        return "02";

	    if (tokens.contains("codigo") && tokens.contains("pais"))
	        return "VE";

	    if (tokens.contains("codigo") && tokens.contains("empresa"))
	        return "0108";

	    if (tokens.contains("razon"))
	        return "01";

	    // =========================================================
	    // ✅ 3. SEMÁNTICA
	    // =========================================================

	    // ✅ tipo identificación
	    if (tokens.contains("tipo") && tokens.contains("identificacion")) {
	        String v = "V";
	        dataContext.put("identificacion", v);
	        return v;
	    }

	    // ✅ FECHAS
	    if (tokens.contains("fecha")) {

	        if (tokens.contains("ini"))
	            return "01012026";

	        if (tokens.contains("fin"))
	            return "31122026";

	        return "01012026";
	    }

	    // ✅ identificación
	    if (tokens.contains("cedula") || tokens.contains("rif") || tokens.contains("identificacion")) {
	        Integer v = 12345678;
	        dataContext.put("identificacion", v);
	        return v;
	    }

	    // ✅ persona
	    if (tokens.contains("persona")) {
	        Integer v = 12345678;
	        dataContext.put("persona", v);
	        return v;
	    }

	    // ✅ cuenta
	    if (tokens.contains("cuenta")) {
	        return "123456789012";
	    }

	    // ✅ subcanal
	    if (tokens.contains("subcanal"))
	        return "09";

	    // ✅ moneda
	    if (tokens.contains("moneda"))
	        return "VES";

	    // ✅ tarjeta
	    if (tokens.contains("tarjeta")) {
	        return tokens.contains("lista")
	                ? List.of("1234567890123456")
	                : "1234567890123456";
	    }

	    // ✅ numero
	    if (tokens.contains("numero"))
	        return 12345678;

	    // ✅ códigos
	    if (tokens.contains("codigo"))
	        return "0001";

	    // =========================================================
	    // ✅ FINANCIERO
	    // =========================================================
	    if (tokens.contains("monto"))
	        return 1500.50;

	    if (tokens.contains("tasa"))
	        return 36.75;

	    // =========================================================
	    // ✅ TEXTO
	    // =========================================================
	    if (tokens.contains("nombre"))
	        return "JUAN";

	    if (tokens.contains("apellido"))
	        return "PEREZ";

	    if (tokens.contains("email"))
	        return "test@email.com";

	    if (tokens.contains("telefono"))
	        return "04141234567";

	    if (tokens.contains("direccion"))
	        return "AV PRINCIPAL CARACAS";

	    // =========================================================
	    // ✅ FLAGS
	    // =========================================================
	    if (tokens.contains("status") || tokens.contains("estatus"))
	        return "ACTIVO";

	    if (tokens.contains("indicador"))
	        return "S";

	    if (tokens.contains("rol"))
	        return "CLIENTE";

	    // =========================================================
	    // ✅ GENERALES
	    // =========================================================
	    if (tokens.contains("tipo"))
	        return "01";

	    // =========================================================
	    // ✅ FALLBACK FINAL
	    // =========================================================
	    if (tokens.contains("cuenta"))
	        return "123456789012";

	    if (tokens.contains("numero") || tokens.contains("id"))
	        return 12345678;

	    return "VALOR";
	}

	public Object buildExampleFromType(String type) {

		if (type.equals("byte") || type.equals("byte[]")) {
			return "base64-string";
		}

		Map<String, Object> schema = schemaMap.get(type);

		if (schema != null && schema.containsKey("enum")) {
			List<?> values = (List<?>) schema.get("enum");
			if (!values.isEmpty())
				return values.get(0);
		}

		if (schema == null)
			return new LinkedHashMap<>();

		Map<String, Object> example = new LinkedHashMap<>();

		Object propsObj = schema.get("properties");

		if (!(propsObj instanceof Map))
			return example;

		Map<String, Object> props = (Map<String, Object>) propsObj;

		props.forEach((key, val) -> {

			if (!(val instanceof Map))
				return;

			Map<String, Object> prop = (Map<String, Object>) val;

			if (prop.containsKey("$ref")) {

				String ref = prop.get("$ref").toString();
				String refType = ref.substring(ref.lastIndexOf("/") + 1);

				example.put(key, buildExampleFromType(refType));
			}

			else if ("array".equals(prop.get("type"))) {

				Map<?, ?> items = (Map<?, ?>) prop.get("items");

				if (items != null && items.containsKey("$ref")) {

					String ref = items.get("$ref").toString();
					String refType = ref.substring(ref.lastIndexOf("/") + 1);

					example.put(key, List.of(buildExampleFromType(refType)));

				} else {

					if (prop.containsKey("example")) {
						example.put(key, prop.get("example"));
					} else {
						example.put(key, List.of(generateSmartExample(key)));
					}

				}
			}

			else {

				// ✅ 1. si el schema ya tiene example → usarlo
				if (prop.containsKey("example")) {
					example.put(key, prop.get("example"));
					return;
				}

				// ✅ 2. fallback a generación
				Object value = generateSmartExample(key);

				if (value == null) {
					value = fallbackByType(prop);
				}

				example.put(key, value);
			}

		});

		if (example.isEmpty()) {
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("id", 12345678); // ✅ FIX
			return fallback;
		}

		return example;
	}

	public String inferType(String name) {

		String normalized = AbbreviationUtil.normalizeName(name);
		List<String> tokens = AbbreviationUtil.tokenize(normalized);

		if (tokens.contains("fecha"))
			return "string";
		if (tokens.contains("cuenta") || tokens.contains("tarjeta"))
			return "string";
		if (tokens.contains("telefono"))
			return "string";
		if (tokens.contains("codigo"))
			return "string";
		if (tokens.contains("monto"))
			return "number";
		if (tokens.contains("identificacion"))
			return "integer";

		return null; // ✅ FIX
	}

	private Object fallbackByType(Map<String, Object> prop) {

		Object typeObj = prop.get("type");

		if (typeObj == null)
			return "VALOR";

		switch (typeObj.toString()) {
		case "string":
			return "VALOR";
		case "integer":
			return 123;
		case "number":
			return 10.5;
		case "boolean":
			return true;
		default:
			return "VALOR";
		}
	}

	public void reset() {
		dataContext.clear();
	}
}