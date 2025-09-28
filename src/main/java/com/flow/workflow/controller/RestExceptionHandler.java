package com.flow.workflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * Global exception handler used by the app.
 * Important: don't swallow errors for swagger / actuator / api-docs requests.
 */
@ControllerAdvice
public class RestExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /**
     * Generic exception handler for application endpoints.
     * For OpenAPI/swagger/actuator paths we rethrow so the framework can serve the static resources / docs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllExceptions(HttpServletRequest request, Exception ex) throws Exception {
        String path = request.getRequestURI();
        // Allow framework to handle swagger / api-docs / actuator requests instead of masking them:
        if (path != null &&
                (path.startsWith("/v3/") || path.startsWith("/swagger-ui") || path.startsWith("/swagger-ui.html")
                        || path.startsWith("/api-docs") || path.startsWith("/actuator"))) {
            // rethrow so container/springdoc will respond normally (do not swallow)
            log.debug("Rethrowing exception for path {} so framework can handle it: {}", path, ex.toString());
            throw ex;
        }

        // For all other requests return friendly JSON (your previous behaviour)
        log.error("Unhandled exception for {} — returning JSON error: {}", path, ex.toString(), ex);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", ex.getMessage() == null ? "Internal Server Error" : ex.getMessage());
        body.put("error", ex.getClass().getSimpleName());
        body.put("path", path);
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
