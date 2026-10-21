package com.dataanalytics.exception;

public class DatasetProcessingException extends RuntimeException {
    public DatasetProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
