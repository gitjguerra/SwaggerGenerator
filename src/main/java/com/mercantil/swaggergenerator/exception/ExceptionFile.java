package com.mercantil.swaggergenerator.exception;

/**
 * CLASE ENCARGADA DE MANEJAR EXCEPCIONES DE ARCHIVOS.
 */
public class ExceptionFile extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * CONSTRUCTOR.
     */
    public ExceptionFile(String message) {
        super(message);
    }

    /**
     * CONSTRUCTOR CON CAUSA.
     */
    public ExceptionFile(String message, Throwable cause) {
        super(message, cause);
    }
}