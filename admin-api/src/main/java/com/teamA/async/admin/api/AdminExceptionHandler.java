package com.teamA.async.admin.api;

import com.teamA.async.admin.service.AdminEventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(AdminEventService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(AdminEventService.ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }
}
