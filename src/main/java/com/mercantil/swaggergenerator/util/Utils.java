package com.mercantil.swaggergenerator.util;

import java.io.File;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import com.mercantil.swaggergenerator.exception.ExceptionFile;

/**
 * CLASE UTILITARIA PARA LA APLICACION.
 * <hr>
 * <code>swagger-generator - Banco Mercantil</code>
 */
public class Utils {

    /** The Constant SEPARATOR. */
    public static final String SEPARATOR = System.getProperty("file.separator");

    /** The Constant BM_HOME. */
    private static final String BM_HOME = "BM_HOME";

    private Utils() {}

    /**
     * METODO ENCARGADO DE RETORNAR LA RUTA DEL ARCHIVO DE CONFIGURACION.
     * @param appName the app name
     * @return the file app config
     */
    public static String getFileAppConfig(String appName) {

        StringBuilder path = new StringBuilder();

        return path
                .append(getPathHome(appName))
                .append(appName)
                .append(".xml")
                .toString();
    }

    /**
     * METODO ENCARGADO DE RETORNAR LA RUTA BASE DE LA APLICACION.
     */
    private static String getPathHome(String appName) {

        return new StringBuilder()
                .append(System.getenv(BM_HOME))
                .append(SEPARATOR)
                .append("appl")
                .append(SEPARATOR)
                .append(appName)
                .append(SEPARATOR)
                .append("config")
                .append(SEPARATOR)
                .toString();
    }

    /**
     * METODO ENCARGADO DE RESOLVER UNA RUTA.
     */
    public static String resolvePath(String value) {

        if (value == null || value.trim().isEmpty())
            return value;

        return value
                .replace("${BM_HOME}", System.getenv(BM_HOME))
                .replace("${env:BM_HOME}", System.getenv(BM_HOME));
    }

    /**
     * METODO ENCARGADO DE CONFIGURAR LOS TAGS.
     */
    public static void setListtags(Path pathFile, boolean test) {
        // mantener igual OSBA aunque no se use aún
    }

    /**
     * METODO ENCARGADO DE CONFIGURAR LOG4J.
     */
    public static void setConfigLog4j(String path) throws ExceptionFile {

        // ✅ VALIDACION
        if (!FileUtility.isValidatePath(path)) {
            throw new ExceptionFile("NO ESTA EL ARCHIVO [" + path + "]");
        }

        File file = new File(path);

        // ✅ LOG4J2 REAL (OSBA STYLE)
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(file.toURI());

        LogManager.getLogger(Utils.class)
                .info("LOG4J INICIADO EN: {}", path);
    }
}