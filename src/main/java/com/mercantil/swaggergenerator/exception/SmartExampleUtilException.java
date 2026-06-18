package com.mercantil.swaggergenerator.exception;

public class SmartExampleUtilException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SmartExampleUtilException(String message) {
        super(message);
    }

    public SmartExampleUtilException(String message, Throwable cause) {
        super(message, cause);
    }

	public SmartExampleUtilException(Exception e) {
		super(e);
	}
}
