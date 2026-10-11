package com.gwonsystem.backend.exception;

import com.gwonsystem.backend.controller.NoticeController;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=NoticeController.class)
public class NoticeAuthExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String,String>> authentication(AuthenticationException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","로그인이 필요합니다."));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,String>> authorization(AccessDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","권한이 없습니다."));
    }
}
