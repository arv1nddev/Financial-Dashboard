package com.zorvyn.financedashboard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ============================================================================
 * RESOURCE NOT FOUND EXCEPTION
 * ============================================================================
 *
 * Thrown when a requested entity doesn't exist (or is soft-deleted).
 *
 * @ResponseStatus(NOT_FOUND) ensures that if this exception somehow
 * escapes our GlobalExceptionHandler, Spring will still return 404.
 * However, our handler catches it explicitly for a richer error response.
 *
 * Constructor format: "Resource with field value not found"
 * Example: "Transaction with id 42 not found"
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s with %s '%s' not found", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceName() { return resourceName; }
    public String getFieldName() { return fieldName; }
    public Object getFieldValue() { return fieldValue; }
}
