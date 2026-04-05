package com.zorvyn.financedashboard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ============================================================================
 * DUPLICATE RESOURCE EXCEPTION
 * ============================================================================
 *
 * Thrown when a unique constraint would be violated (e.g., registering
 * with an email that already exists). We catch this in the service layer
 * BEFORE the database constraint fires, providing a user-friendly error
 * message instead of a cryptic DataIntegrityViolationException.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s with %s '%s' already exists", resourceName, fieldName, fieldValue));
    }
}
