package com.mercantil.swaggergenerator.component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class RuleEngine {

	// =========================================================
	// ✅ LEGACY (compatibilidad)
	// =========================================================
	private final Map<String, Map<String, String>> apiRules = new LinkedHashMap<>();

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
	@SuppressWarnings("resource")
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
						System.out.println("⚠️ API ignorada sin name/path");
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
						NodeList fieldNodes = request.getElementsByTagName("field");

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
						NodeList fieldNodes = response.getElementsByTagName("field");

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
					// ✅ LEGACY SUPPORT
					// =================================================
					if (!legacyFields.isEmpty()) {

						requestFields.putAll(legacyFields);
						apiRules.put(apiKey, legacyFields);
					}

					// =================================================
					// ✅ REQUEST RULES
					// =================================================
					if (!requestFields.isEmpty()) {
						requestRules.put(apiKey, requestFields);
					}

					// =================================================
					// ✅ RESPONSE RULES
					// =================================================
					if (!responseFields.isEmpty()) {
						responseRules.put(apiKey, responseFields);
					}
				}

				// =====================================================
				// ✅ DEBUG
				// =====================================================
				System.out.println("✅ Request rules cargadas:");
				requestRules.keySet().forEach(k -> System.out.println("   ➜ " + k));

				System.out.println("✅ Response rules cargadas:");
				responseRules.keySet().forEach(k -> System.out.println("   ➜ " + k));
			}

		} catch (Exception e) {
			throw new RuntimeException("❌ Error cargando rules.xml", e);
		}
	}

	// =========================================================
	// ✅ MÉTODO AUXILIAR (CORREGIDO)
	// =========================================================
	private static InputStream loadInputStream(String pathFile) {

		try {

			InputStream is = new java.io.FileInputStream(pathFile);

			System.out.println("✅ Cargando rules.xml: " + pathFile);

			return is;

		} catch (Exception e) {

			System.out.println("⚠️ No se encontró rules.xml externo, usando interno...");

			InputStream is = RuleEngine.class.getClassLoader().getResourceAsStream("rules.xml");

			if (is == null) {
				throw new RuntimeException("❌ No se encontró rules.xml");
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

		if (apiKey == null || fieldName == null)
			return null;

		String normalizedKey = normalizePath(apiKey);
		Map<String, String> fields = source.get(normalizedKey);

		if (fields == null) {
			for (String key : source.keySet()) {
				if (normalizePath(key).equalsIgnoreCase(normalizedKey)) {
					fields = source.get(key);
					break;
				}
			}
		}

		if (fields == null)
			return null;

		if (fields.containsKey(fieldName)) {
			return fields.get(fieldName);
		}

		for (Map.Entry<String, String> entry : fields.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(fieldName)) {
				return entry.getValue();
			}
		}

		for (Map.Entry<String, String> entry : fields.entrySet()) {
			if (fieldName.toLowerCase().endsWith(entry.getKey().toLowerCase())) {
				return entry.getValue();
			}
		}

		return null;
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

		if (path == null || path.isBlank())
			return "";

		path = path.trim().replace("\\", "/").replaceAll("//+", "/");

		if (!path.startsWith("/"))
			path = "/" + path;

		if (path.length() > 1 && path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}

		return path;
	}
}