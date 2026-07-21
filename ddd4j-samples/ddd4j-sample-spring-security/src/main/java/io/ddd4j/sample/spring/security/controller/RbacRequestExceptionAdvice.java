package io.ddd4j.sample.spring.security.controller;

import io.ddd4j.core.api.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps invalid RBAC requests to HTTP 400 while preserving the ddd4j response body.
 */
@RestControllerAdvice(assignableTypes = {
        AuthenticationController.class,
        AuthorizationController.class
})
public class RbacRequestExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(exception.getMessage()));
    }
}
