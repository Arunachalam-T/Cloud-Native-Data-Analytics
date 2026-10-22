package com.dataanalytics.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DatasetNotFoundException.class)
    public ProblemDetail handleNotFound(DatasetNotFoundException ex) {
        log.warn("Dataset not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Dataset Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DatasetProcessingException.class)
    public ProblemDetail handleProcessingError(DatasetProcessingException ex) {
        log.error("Dataset processing error: {}", ex.getMessage(), ex.getCause());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Dataset Processing Error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleFileTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
            "File size exceeds the maximum allowed limit (100 MB).");
        pd.setTitle("File Too Large");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ProblemDetail handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again.");
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
