package com.mercantil.swaggergenerator.component;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Component
public class RuleEngine {

	private Map<String, Map<String, String>> apiRules = new HashMap<>();

	@SuppressWarnings("resource")
	@PostConstruct
	public void loadRules() {

		try {

			InputStream is;

			try {
				// ✅ INTENTA ARCHIVO EXTERNO
				String externalPath = System.getProperty("user.dir") + "/rules.xml";

				is = new java.io.FileInputStream(externalPath);

				System.out.println("✅ Cargando rules.xml externo: " + externalPath);

			} catch (Exception e) {

				System.out.println("⚠️ No se encontró rules.xml externo, usando interno...");

				is = getClass().getClassLoader().getResourceAsStream("rules.xml");

				if (is == null) {
					throw new RuntimeException("❌ No se encontró rules.xml en ningún lado");
				}
			}

			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);

			NodeList apis = doc.getElementsByTagName("api");

			for (int i = 0; i < apis.getLength(); i++) {

				Element api = (Element) apis.item(i);
				String apiName = api.getAttribute("name");

				Map<String, String> fields = new HashMap<>();

				NodeList fieldNodes = api.getElementsByTagName("field");

				for (int j = 0; j < fieldNodes.getLength(); j++) {

					Element field = (Element) fieldNodes.item(j);

					fields.put(field.getAttribute("name"), field.getAttribute("value"));
				}

				apiRules.put(apiName, fields);
			}

			System.out.println("✅ Rules cargadas: " + apiRules.keySet());

		} catch (Exception e) {
			throw new RuntimeException("Error cargando rules.xml", e);
		}
	}

	public String getValue(String apiName, String fieldName) {

		if (!apiRules.containsKey(apiName))
			return null;

		Map<String, String> fields = apiRules.get(apiName);

		// ✅ exact match
		if (fields.containsKey(fieldName))
			return fields.get(fieldName);

		// ✅ case-insensitive fallback
		for (String key : fields.keySet()) {
			if (key.equalsIgnoreCase(fieldName)) {
				return fields.get(key);
			}
		}

		return null;
	}

}