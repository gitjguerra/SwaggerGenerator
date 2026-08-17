package com.mercantil.swaggergenerator.component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mercantil.swaggergenerator.exception.RuleEngineException;
import com.mercantil.swaggergenerator.util.PathUtil;

@Component
public class RuleEngine {

	private static final Logger log = LogManager.getLogger(RuleEngine.class);

	private static final String FIELD = "field";

	// =========================================================
	// ✅ REQUEST RULES
	// =========================================================
	private final Map<String, Map<String, String>> requestRules = new LinkedHashMap<>();

	// =========================================================
	// ✅ RESPONSE RULES
	// =========================================================
	private final Map<String, Map<String, String>> responseRules = new LinkedHashMap<>();

	// =========================================================
	// ✅ LOAD RULES
	// =========================================================
	@PostConstruct
	public void loadRules() {

		try {

			// =====================================================
			// ✅ OSBA STYLE (BM_HOME desde InitEnvironment)
			// =====================================================
			String pathFileRules = System.getProperty("pathRules");

			// ✅ FIX: try-with-resources (NO RESOURCE LEAK)
			try (InputStream is = loadInputStream(pathFileRules)) {

				// =====================================================
				// ✅ PARSE XML (DOM — TU IMPLEMENTACIÓN ORIGINAL)
				// =====================================================
				Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);

				NodeList apis = doc.getElementsByTagName("api");

				for (int i = 0; i < apis.getLength(); i++) {

					Element api = (Element) apis.item(i);

					String apiKey = api.getAttribute("path");

					if (apiKey == null || apiKey.isBlank()) {
						apiKey = api.getAttribute("name");
					}

					if (apiKey == null || apiKey.isBlank()) {
						log.info("API ignorada sin name/path");
						continue;
					}

					apiKey = normalizePath(apiKey);

					Map<String, String> legacyFields = new LinkedHashMap<>();
					Map<String, String> requestFields = new LinkedHashMap<>();
					Map<String, String> responseFields = new LinkedHashMap<>();

					// =================================================
					// ✅ REQUEST
					// =================================================
					NodeList requestNodes = api.getElementsByTagName("request");

					if (requestNodes.getLength() > 0) {

						Element request = (Element) requestNodes.item(0);
						NodeList fieldNodes = request.getElementsByTagName(FIELD);

						for (int j = 0; j < fieldNodes.getLength(); j++) {

							Element field = (Element) fieldNodes.item(j);

							requestFields.put(field.getAttribute("name"), field.getAttribute("value"));
						}
					}

					// =================================================
					// ✅ RESPONSE
					// =================================================
					NodeList responseNodes = api.getElementsByTagName("response");

					if (responseNodes.getLength() > 0) {

						Element response = (Element) responseNodes.item(0);
						NodeList fieldNodes = response.getElementsByTagName(FIELD);

						for (int j = 0; j < fieldNodes.getLength(); j++) {

							Element field = (Element) fieldNodes.item(j);

							responseFields.put(field.getAttribute("name"), field.getAttribute("value"));
						}
					}

					// =================================================
					// ✅ LEGACY DIRECT FIELD
					// =================================================
					NodeList directFields = api.getChildNodes();

					for (int j = 0; j < directFields.getLength(); j++) {

						if (!(directFields.item(j) instanceof Element)) {
							continue;
						}

						Element el = (Element) directFields.item(j);

						if (!"field".equals(el.getTagName())) {
							continue;
						}

						legacyFields.put(el.getAttribute("name"), el.getAttribute("value"));
					}

					// =================================================
					// ✅ LEGACY SUPPORT: rules.xml externos antiguos pueden traer
					// <field> sueltos directamente bajo <api> (sin <request>/<response>);
					// se tratan como reglas de request.
					// =================================================
					if (!legacyFields.isEmpty()) {

						requestFields.putAll(legacyFields);
					}

					// =================================================
					// ✅ REQUEST RULES
					// =================================================
					if (!requestFields.isEmpty()) {

						if (requestRules.containsKey(apiKey)) {
							log.warn("api path duplicado en rules.xml (request): '{}', se sobreescribe la regla anterior",
									apiKey);
						}

						requestRules.put(apiKey, requestFields);
					}

					// =================================================
					// ✅ RESPONSE RULES
					// =================================================
					if (!responseFields.isEmpty()) {

						if (responseRules.containsKey(apiKey)) {
							log.warn("api path duplicado en rules.xml (response): '{}', se sobreescribe la regla anterior",
									apiKey);
						}

						responseRules.put(apiKey, responseFields);
					}
				}

				// =====================================================
				// ✅ DEBUG
				// =====================================================
				log.info("Request rules cargadas:");
				requestRules.keySet().forEach(k -> log.info("   - {}", k));

				log.info("Response rules cargadas:");
				responseRules.keySet().forEach(k -> log.info("   - {}", k));
			}

		} catch (Exception e) {
			throw new RuleEngineException("Error cargando rules.xml", e);
		}
	}

	// =========================================================
	// ✅ MÉTODO AUXILIAR
	// =========================================================
	private static InputStream loadInputStream(String pathFile) {

		try {

			log.info("Cargando rules.xml: {}", pathFile);

			return new FileInputStream(pathFile);

		} catch (Exception e) {

			log.info("No se encontró rules.xml externo, usando interno...");

			InputStream is = RuleEngine.class.getClassLoader().getResourceAsStream("rules.xml");

			if (is == null) {
				throw new RuleEngineException("No se encontró rules.xml");
			}

			return is;
		}
	}

	// =========================================================
	// ✅ PUBLIC API
	// =========================================================

	public String getValue(String apiKey, String fieldName) {
		return getRequestValue(apiKey, fieldName);
	}

	public String getRequestValue(String apiKey, String fieldName) {
		return getFieldValue(requestRules, apiKey, fieldName);
	}

	public String getResponseValue(String apiKey, String fieldName) {
		return getFieldValue(responseRules, apiKey, fieldName);
	}

	private String getFieldValue(Map<String, Map<String, String>> source, String apiKey, String fieldName) {

		if (apiKey == null || fieldName == null) {
			return null;
		}

		String normalizedKey = normalizePath(apiKey);

		Map<String, String> fields = resolveFields(source, normalizedKey);

		if (fields.isEmpty()) {
			return null;
		}

		String exactValue = findExactField(fields, fieldName);

		if (exactValue != null) {
			return exactValue;
		}

		return findSuffixField(fields, fieldName);
	}

	private Map<String, String> resolveFields(Map<String, Map<String, String>> source, String normalizedKey) {

		Map<String, String> fields = source.get(normalizedKey);

		if (fields != null) {
			return fields;
		}

		for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {

			if (normalizePath(entry.getKey()).equalsIgnoreCase(normalizedKey)) {

				return entry.getValue();
			}
		}

		return Map.of();
	}

	private String findExactField(Map<String, String> fields, String fieldName) {

		String value = fields.get(fieldName);

		if (value != null) {
			return value;
		}

		for (Map.Entry<String, String> entry : fields.entrySet()) {

			if (entry.getKey().equalsIgnoreCase(fieldName)) {
				return entry.getValue();
			}
		}

		return null;
	}

	private String findSuffixField(Map<String, String> fields, String fieldName) {

		// ✅ matching por segmentos completos separados por "." (no por substring
		// crudo): evita que una regla corta como "id" matchee campos no
		// relacionados que solo terminan con esos mismos caracteres, p.ej.
		// "producto.id" o "clienteId" cuando la regla era para "cliente.id".
		// Ante múltiples matches, se prefiere la regla más específica (más
		// segmentos coincidentes).
		String[] fieldSegments = fieldName.toLowerCase().split("\\.");

		String bestValue = null;
		int bestMatchedSegments = 0;

		for (Map.Entry<String, String> entry : fields.entrySet()) {

			String[] ruleSegments = entry.getKey().toLowerCase().split("\\.");

			if (ruleSegments.length > fieldSegments.length || ruleSegments.length <= bestMatchedSegments) {
				continue;
			}

			if (matchesTrailingSegments(fieldSegments, ruleSegments)) {

				bestMatchedSegments = ruleSegments.length;
				bestValue = entry.getValue();
			}
		}

		return bestValue;
	}

	private boolean matchesTrailingSegments(String[] fieldSegments, String[] ruleSegments) {

		int offset = fieldSegments.length - ruleSegments.length;

		for (int i = 0; i < ruleSegments.length; i++) {

			if (!fieldSegments[offset + i].equals(ruleSegments[i])) {
				return false;
			}
		}

		return true;
	}

	public Map<String, String> getRequestRules(String apiKey) {
		if (apiKey == null)
			return Collections.emptyMap();
		return requestRules.getOrDefault(normalizePath(apiKey), Collections.emptyMap());
	}

	public Map<String, String> getResponseRules(String apiKey) {
		if (apiKey == null)
			return Collections.emptyMap();
		return responseRules.getOrDefault(normalizePath(apiKey), Collections.emptyMap());
	}

	public boolean hasRequestRules(String apiKey) {
		return requestRules.containsKey(apiKey);
	}

	public boolean hasResponseRules(String apiKey) {
		return responseRules.containsKey(apiKey);
	}

	private String normalizePath(String path) {

		return PathUtil.normalize(path);
	}
}