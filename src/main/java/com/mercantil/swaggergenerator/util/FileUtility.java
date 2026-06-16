package com.mercantil.swaggergenerator.util;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.mercantil.swaggergenerator.exception.ExceptionFile;

/**
 * CLASE UTILITARIA ENCARGADA DE MANEJO DE ARCHIVOS.
 */
public class FileUtility {

    private FileUtility() {}

    /**
     * METODO ENCARGADO DE VALIDAR LAS RUTAS.
     */
    public static void validatePaths(String... paths) throws ExceptionFile {

        for (int i = 0; i < paths.length; i++) {

            if (!isValidatePath(paths[i])) {
                throw new ExceptionFile("EL PATH NO DEFINIDO EN LA POSICION [" + (i + 1) + "]");
            }
        }
    }

    /**
     * METODO ENCARGADO DE VALIDAR EXISTENCIA.
     */
    public static boolean isValidatePath(String path) {

        if (path == null || path.trim().isEmpty()) return false;

        Path tmpPath = Paths.get(path);
        return tmpPath.toFile().exists();
    }

    /**
     * METODO ENCARGADO DE VALIDAR SI ES ARCHIVO.
     */
    public static boolean isFile(Path path) {
        return path.toFile().exists();
    }

    /**
     * METODO ENCARGADO DE VALIDAR SI ES DIRECTORIO.
     */
    public static boolean isDirectory(Path path) {
        return path.toFile().isDirectory();
    }
}