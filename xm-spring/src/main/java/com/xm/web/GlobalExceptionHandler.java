package com.xm.web;

import com.xm.scenario.order.domain.exception.IllegalOrderStateException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * controller的异常处理
 *
 * @author XM
 * @date 2025/9/10
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalOrderStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalOrderState(IllegalOrderStateException ex,
                                                                       HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
                                                                     HttpServletRequest request) {
        if (ex.getMessage() != null && ex.getMessage().startsWith("Order not found:")) {
            return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage(), request);
        }
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex,
                                                                  HttpServletRequest request) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.startsWith("Duplicate request:")
                || message.startsWith("Order cannot be submitted:")
                || message.startsWith("Optimistic lock conflict:")
                || message.startsWith("Failed to acquire")) {
            return error(HttpStatus.CONFLICT, "ORDER_CONFLICT", message, request);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status,
                                                      String code,
                                                      String message,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message == null ? status.getReasonPhrase() : message,
                "path", request.getRequestURI()
        ));
    }
}
