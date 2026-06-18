package com.mercantil.swaggergenerator.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mercantil.swaggergenerator.model.ServiceItem;

public final class ServiceLoader {

    private ServiceLoader() {

        throw new IllegalStateException(
                "Utility class");
    }

    public static List<ServiceItem> load() {

        List<ServiceItem> list = new ArrayList<>();

        try {

            String path = System.getProperty("pathServices");

            if (path == null || path.isBlank()) {

                throw new IllegalStateException(
                        "pathServices no configurado");
            }

            try (InputStream is = new FileInputStream(path)) {

                DocumentBuilderFactory factory =
                        DocumentBuilderFactory.newInstance();

                factory.setFeature(
                        XMLConstants.FEATURE_SECURE_PROCESSING,
                        true);

                Document doc =
                        factory.newDocumentBuilder().parse(is);

                NodeList nodes =
                        doc.getElementsByTagName("service");

                for (int i = 0; i < nodes.getLength(); i++) {

                    Element el =
                            (Element) nodes.item(i);

                    ServiceItem s =
                            new ServiceItem();

                    s.setName(
                            el.getAttribute("name"));

                    s.setControllersPath(
                            el.getElementsByTagName("controllersPath")
                                    .item(0)
                                    .getTextContent()
                                    .trim());

                    NodeList beanNodes =
                            el.getElementsByTagName("beansPath");

                    List<String> beanPaths =
                            new ArrayList<>();

                    for (int j = 0; j < beanNodes.getLength(); j++) {

                        beanPaths.add(
                                beanNodes.item(j)
                                        .getTextContent()
                                        .trim());
                    }

                    s.setBeansPath(beanPaths);

                    s.setBasePackage(
                            el.getElementsByTagName("basePackage")
                                    .item(0)
                                    .getTextContent());

                    s.setBasePath(
                            el.getElementsByTagName("basePath")
                                    .item(0)
                                    .getTextContent());

                    list.add(s);
                }
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Error cargando services.xml",
                    e);
        }

        return list;
    }
}