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
    private final Map<String, Map<String, String>> apiRules =
            new LinkedHashMap<>();

    // =========================================================
    // ✅ REQUEST RULES
    // ✅ key:
    // ✅ /safe/consultar/saldo-cuenta
    // =========================================================
    private final Map<String, Map<String, String>> requestRules =
            new LinkedHashMap<>();

    // =========================================================
    // ✅ RESPONSE RULES
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

                String externalPath =
                        System.getProperty("user.dir")
                                + "/rules.xml";

                is =
                        new java.io.FileInputStream(
                                externalPath);

                System.out.println(
                        "✅ Cargando rules.xml externo: "
                                + externalPath);

            } catch (Exception e) {

                System.out.println(
                        "⚠️ No se encontró rules.xml externo, usando interno...");

                is =
                        getClass()
                                .getClassLoader()
                                .getResourceAsStream(
                                        "rules.xml");

                if (is == null) {

                    throw new RuntimeException(
                            "❌ No se encontró rules.xml");
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

                // =================================================
                // ✅ NUEVO
                // ✅ PRIORIDAD path
                // =================================================
                String apiKey =
                        api.getAttribute("path");

                // =================================================
                // ✅ BACKWARD COMPATIBILITY
                // =================================================
                if (apiKey == null
                        || apiKey.isBlank()) {

                    apiKey =
                            api.getAttribute("name");
                }

                if (apiKey == null
                        || apiKey.isBlank()) {

                    System.out.println(
                            "⚠️ API ignorada sin name/path");

                    continue;
                }

                // =================================================
                // ✅ NORMALIZAR
                // =================================================
                apiKey =
                        apiKey.trim();

                // =================================================
                // ✅ LEGACY
                // =================================================
                Map<String, String> legacyFields =
                        new LinkedHashMap<>();

                // =================================================
                // ✅ REQUEST
                // =================================================
                Map<String, String> requestFields =
                        new LinkedHashMap<>();

                // =================================================
                // ✅ RESPONSE
                // =================================================
                Map<String, String> responseFields =
                        new LinkedHashMap<>();

                // =================================================
                // ✅ REQUEST
                // =================================================
                NodeList requestNodes =
                        api.getElementsByTagName(
                                "request");

                if (requestNodes.getLength() > 0) {

                    Element request =
                            (Element) requestNodes.item(0);

                    NodeList fieldNodes =
                            request.getElementsByTagName(
                                    "field");

                    for (int j = 0;
                         j < fieldNodes.getLength();
                         j++) {

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
                        api.getElementsByTagName(
                                "response");

                if (responseNodes.getLength() > 0) {

                    Element response =
                            (Element) responseNodes.item(0);

                    NodeList fieldNodes =
                            response.getElementsByTagName(
                                    "field");

                    for (int j = 0;
                         j < fieldNodes.getLength();
                         j++) {

                        Element field =
                                (Element) fieldNodes.item(j);

                        responseFields.put(
                                field.getAttribute("name"),

                                field.getAttribute("value"));
                    }
                }

                // =================================================
                // ✅ LEGACY DIRECT FIELD
                // =================================================
                NodeList directFields =
                        api.getChildNodes();

                for (int j = 0;
                     j < directFields.getLength();
                     j++) {

                    if (!(directFields.item(j)
                            instanceof Element)) {

                        continue;
                    }

                    Element el =
                            (Element) directFields.item(j);

                    if (!"field".equals(
                            el.getTagName())) {

                        continue;
                    }

                    legacyFields.put(
                            el.getAttribute("name"),

                            el.getAttribute("value"));
                }

                // =================================================
                // ✅ LEGACY SUPPORT
                // =================================================
                if (!legacyFields.isEmpty()) {

                    requestFields.putAll(
                            legacyFields);

                    apiRules.put(
                            apiKey,
                            legacyFields);
                }

                // =================================================
                // ✅ REQUEST RULES
                // =================================================
                if (!requestFields.isEmpty()) {

                    requestRules.put(
                            apiKey,
                            requestFields);
                }

                // =================================================
                // ✅ RESPONSE RULES
                // =================================================
                if (!responseFields.isEmpty()) {

                    responseRules.put(
                            apiKey,
                            responseFields);
                }
            }

            // =====================================================
            // ✅ DEBUG
            // =====================================================
            System.out.println(
                    "✅ Request rules cargadas:");

            requestRules.keySet()
                    .forEach(k ->
                            System.out.println(
                                    "   ➜ " + k));

            System.out.println(
                    "✅ Response rules cargadas:");

            responseRules.keySet()
                    .forEach(k ->
                            System.out.println(
                                    "   ➜ " + k));

        } catch (Exception e) {

            throw new RuntimeException(
                    "❌ Error cargando rules.xml",
                    e);
        }
    }

    // =========================================================
    // ✅ LEGACY
    // =========================================================
    public String getValue(
            String apiKey,
            String fieldName) {

        return getRequestValue(
                apiKey,
                fieldName);
    }

    // =========================================================
    // ✅ REQUEST VALUE
    // =========================================================
    public String getRequestValue(
            String apiKey,
            String fieldName) {

        return getFieldValue(
                requestRules,
                apiKey,
                fieldName);
    }

    // =========================================================
    // ✅ RESPONSE VALUE
    // =========================================================
    public String getResponseValue(
            String apiKey,
            String fieldName) {

        return getFieldValue(
                responseRules,
                apiKey,
                fieldName);
    }

    // =========================================================
    // ✅ INTERNAL FIELD RESOLUTION
    // =========================================================
    private String getFieldValue(
            Map<String, Map<String, String>> source,
            String apiKey,
            String fieldName) {

        if (apiKey == null
                || fieldName == null) {

            return null;
        }

        Map<String, String> fields =
                source.get(apiKey);

        if (fields == null) {
            return null;
        }

        // ✅ exact
        if (fields.containsKey(fieldName)) {

            return fields.get(fieldName);
        }

        // ✅ case insensitive
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
            String apiKey) {

        if (apiKey == null) {

            return Collections.emptyMap();
        }

        return requestRules.getOrDefault(
                apiKey,
                Collections.emptyMap());
    }

    // =========================================================
    // ✅ RESPONSE RULES
    // =========================================================
    public Map<String, String> getResponseRules(
            String apiKey) {

        if (apiKey == null) {

            return Collections.emptyMap();
        }

        return responseRules.getOrDefault(
                apiKey,
                Collections.emptyMap());
    }

    // =========================================================
    // ✅ HAS REQUEST RULES
    // =========================================================
    public boolean hasRequestRules(
            String apiKey) {

        return requestRules.containsKey(
                apiKey);
    }

    // =========================================================
    // ✅ HAS RESPONSE RULES
    // =========================================================
    public boolean hasResponseRules(
            String apiKey) {

        return responseRules.containsKey(
                apiKey);
    }
}