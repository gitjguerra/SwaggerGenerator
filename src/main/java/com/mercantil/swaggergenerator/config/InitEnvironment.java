package com.mercantil.swaggergenerator.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mercantil.swaggergenerator.exception.ExceptionFile;
import com.mercantil.swaggergenerator.util.FileUtility;
import com.mercantil.swaggergenerator.util.Utils;

/**
 * CLASE ENCARGADA DE CONFIGURAR EL GENERADOR DE SWAGGER.
 * <strong>SWAGGER-GENERATOR</strong>.
 * 
 * @author <strong>JOSE LUIS GUERRA</strong>
 * <hr><code>Copyright 2026. Banco Mercantil, All rights reserved.</code>
 */
public class InitEnvironment {

    /** The log. */
    private static final Logger log = LogManager.getLogger(InitEnvironment.class);

    /**
     * CONSTRUCTOR.
     */
    private InitEnvironment() { super(); }

    /**
     * METODO ENCARGADO DE OBTENER LA
     * CONFIGURACION DEL GENERADOR.
     */
    public static void getConfig() {

        // =====================================================
        // ✅ DECLARACION DE VARIABLES
        // =====================================================
        Properties props = new Properties();

        String pathFile = Utils.getFileAppConfig(BusinessCard.APP.getValue());

        String pathFileLog4j2;
        String pathFileRules;
        String pathFileAbbreviations;

        try (InputStream is = new FileInputStream(pathFile)) {

            // =================================================
            // ✅ CARGAMOS EL PROPERTIES
            // =================================================
            props.loadFromXML(is);

            // =================================================
            // ✅ CONFIGURAMOS EL ENTORNO (igual OSBA)
            // =================================================
            Utils.setListtags(
                Paths.get(pathFile).getParent(),
                Boolean.parseBoolean(
                    props.getProperty(
                        BusinessCard.TEST_ENVIRONMENT.getValue()
                    )
                )
            );

            // =================================================
            // ✅ RESOLVEMOS LAS RUTAS
            // =================================================
            pathFileLog4j2 = Utils.resolvePath(
                props.getProperty(
                    BusinessCard.LOG4J.getValue()
                )
            );

            pathFileRules = Utils.resolvePath(
                props.getProperty(
                    BusinessCard.RULES.getValue()
                )
            );

            pathFileAbbreviations = Utils.resolvePath(
                props.getProperty(
                    BusinessCard.ABBREVIATIONS.getValue()
                )
            );

            // =================================================
            // ✅ VALIDAMOS LOS ARCHIVOS DE CONFIGURACION
            // =================================================
            FileUtility.validatePaths(
                    pathFile,
                    pathFileLog4j2,
                    pathFileRules,
                    pathFileAbbreviations
            );

            // =================================================
            // ✅ CONFIGURAMOS EL LOG4J2
            // =================================================
            Utils.setConfigLog4j(pathFileLog4j2);

            // =================================================
            // ✅ LOG FINAL
            // =================================================
            log.info("APLICACION [{}] CONFIGURADA EXITOSAMENTE", BusinessCard.APP.getValue());

        } catch (IOException e) {
            log.error("ERROR AL CARGAR LA CONFIGURACION: {}", e.getMessage());
            System.exit(0);

        } catch (ExceptionFile e) {
            log.error("PROBLEMAS CON EL ARCHIVO: {}", e.getMessage());
            System.exit(0);
        }
    }
}