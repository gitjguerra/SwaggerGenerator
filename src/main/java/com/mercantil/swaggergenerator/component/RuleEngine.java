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
    // ✅ LEGACY (compatibilidad hacia atrás)
    // =========================================================
    private final Map<String, Map<String, String>> apiRules =
            new LinkedHashMap<>();

    // =========================================================
    // ✅ NUEVO REQUEST RULES
    // =========================================================
    private final Map<String, Map<String, String>> requestRules =
            new LinkedHashMap<>();

    // =========================================================
    // ✅ NUEVO RESPONSE RULES
    // =========================================================
    private final Map<String, Map<String, String>> responseRules =
            new LinkedHashMap<>();

    // =========================================================
    // ✅ LOAD RULES
    // =========================================================
    @SuppressWarnings("resource")
    @PostConstruct
    public void loadRules() {

        try {

            InputStream is;

            try {

                // ✅ externo
                String externalPath =
                        System.getProperty("user.dir")
                                + "/rules.xml";

                is = new java.io.FileInputStream(externalPath);

                System.out.println(
                        "✅ Cargando rules.xml externo: "
                                + externalPath);

            } catch (Exception e) {

                System.out.println(
                        "⚠️ No se encontró rules.xml externo, usando interno...");

                is = getClass()
                        .getClassLoader()
                        .getResourceAsStream("rules.xml");

                if (is == null) {

                    throw new RuntimeException(
                            "❌ No se encontró rules.xml en ningún lado");
                }
            }

            Document doc =
                    DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(is);

            NodeList apis =
                    doc.getElementsByTagName("api");

            // =====================================================
            // ✅ RECORRER APIs
            // =====================================================
            for (int i = 0; i < apis.getLength(); i++) {

                Element api =
                        (Element) apis.item(i);

                String apiName =
                        api.getAttribute("name");

                // ✅ legacy
                Map<String, String> legacyFields =
                        new LinkedHashMap<>();

                // ✅ request
                Map<String, String> requestFields =
                        new LinkedHashMap<>();

                // ✅ response
                Map<String, String> responseFields =
                        new LinkedHashMap<>();

                // =================================================
                // ✅ REQUEST
                // =================================================
                NodeList requestNodes =
                        api.getElementsByTagName("request");

                if (requestNodes.getLength() > 0) {

                    Element request =
                            (Element) requestNodes.item(0);

                    NodeList fieldNodes =
                            request.getElementsByTagName("field");

                    for (int j = 0; j < fieldNodes.getLength(); j++) {

                        Element field =
                                (Element) fieldNodes.item(j);

                        requestFields.put(
                                field.getAttribute("name"),
                                field.getAttribute("value"));
                    }
                }

                // =================================================
                // ✅ RESPONSE
                // =================================================
                NodeList responseNodes =
                        api.getElementsByTagName("response");

                if (responseNodes.getLength() > 0) {

                    Element response =
                            (Element) responseNodes.item(0);

                    NodeList fieldNodes =
                            response.getElementsByTagName("field");

                    for (int j = 0; j < fieldNodes.getLength(); j++) {

                        Element field =
                                (Element) fieldNodes.item(j);

                        responseFields.put(
                                field.getAttribute("name"),
                                field.getAttribute("value"));
                    }
                }

                // =================================================
                // ✅ LEGACY SUPPORT
                // ✅ <api><field .../></api>
                // =================================================
                NodeList directFields =
                        api.getChildNodes();

                for (int j = 0; j < directFields.getLength(); j++) {

                    if (!(directFields.item(j) instanceof Element)) {
                        continue;
                    }

                    Element el =
                            (Element) directFields.item(j);

                    if (!"field".equals(el.getTagName())) {
                        continue;
                    }

                    legacyFields.put(
                            el.getAttribute("name"),
                            el.getAttribute("value"));
                }

                // =================================================
                // ✅ SI EXISTEN LEGACY
                // ✅ tratarlos como request
                // =================================================
                if (!legacyFields.isEmpty()) {

                    requestFields.putAll(legacyFields);

                    apiRules.put(apiName, legacyFields);
                }

                // =================================================
                // ✅ guardar request
                // =================================================
                if (!requestFields.isEmpty()) {

                    requestRules.put(
                            apiName,
                            requestFields);
                }

                // =================================================
                // ✅ guardar response
                // =================================================
                if (!responseFields.isEmpty()) {

                    responseRules.put(
                            apiName,
                            responseFields);
                }
            }

            System.out.println(
                    "✅ Request rules cargadas: "
                            + requestRules.keySet());

            System.out.println(
                    "✅ Response rules cargadas: "
                            + responseRules.keySet());

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error cargando rules.xml",
                    e);
        }
    }

    // =========================================================
    // ✅ LEGACY
    // =========================================================
    public String getValue(
            String apiName,
            String fieldName) {

        return getRequestValue(apiName, fieldName);
    }

    // =========================================================
    // ✅ REQUEST VALUE
    // =========================================================
    public String getRequestValue(
            String apiName,
            String fieldName) {

        return getFieldValue(
                requestRules,
                apiName,
                fieldName);
    }

    // =========================================================
    // ✅ RESPONSE VALUE
    // =========================================================
    public String getResponseValue(
            String apiName,
            String fieldName) {

        return getFieldValue(
                responseRules,
                apiName,
                fieldName);
    }

    // =========================================================
    // ✅ INTERNAL FIELD RESOLUTION
    // =========================================================
    private String getFieldValue(
            Map<String, Map<String, String>> source,
            String apiName,
            String fieldName) {

        if (apiName == null
                || fieldName == null) {

            return null;
        }

        if (!source.containsKey(apiName)) {
            return null;
        }

        Map<String, String> fields =
                source.get(apiName);

        // ✅ exact match
        if (fields.containsKey(fieldName)) {

            return fields.get(fieldName);
        }

        // ✅ case insensitive fallback
        for (String key : fields.keySet()) {

            if (key.equalsIgnoreCase(fieldName)) {

                return fields.get(key);
            }
        }

        return null;
    }

    // =========================================================
    // ✅ REQUEST RULES
    // =========================================================
    public Map<String, String> getRequestRules(
            String apiName) {

        if (apiName == null) {
            return Collections.emptyMap();
        }

        return requestRules.getOrDefault(
                apiName,
                Collections.emptyMap());
    }

    // =========================================================
    // ✅ RESPONSE RULES
    // =========================================================
    public Map<String, String> getResponseRules(
            String apiName) {

        if (apiName == null) {
            return Collections.emptyMap();
        }

        return responseRules.getOrDefault(
                apiName,
                Collections.emptyMap());
    }

    // =========================================================
    // ✅ HAS REQUEST RULES
    // =========================================================
    public boolean hasRequestRules(
            String apiName) {

        return requestRules.containsKey(apiName);
    }

    // =========================================================
    // ✅ HAS RESPONSE RULES
    // =========================================================
    public boolean hasResponseRules(
            String apiName) {

        return responseRules.containsKey(apiName);
    }
}