package pt.rucodel.productionplanning.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import pt.rucodel.productionplanning.dto.ErrorResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductionPlanningException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ProductionPlanningException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.errorCode()) {
            case "ENTITY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN_OPERATION" -> HttpStatus.FORBIDDEN;
            case "DUPLICATE_MESSAGE", "OPTIMISTIC_LOCK", "AMBIGUOUS_CUSTOMER" -> HttpStatus.CONFLICT;
            case "AUTHENTICATION_FAILED" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(status, ex.errorCode(), ex.getMessage(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        List<String> details;
        if (ex instanceof MethodArgumentNotValidException validation) {
            details = validation.getBindingResult().getFieldErrors().stream()
                    .map(field -> field.getField() + ": " + field.getDefaultMessage())
                    .toList();
        } else {
            details = List.of(ex.getMessage());
        }
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "The request is not valid.", request.getRequestURI(), details));
    }

    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleCredentials(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED", "Invalid username or password.", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(HttpStatus.FORBIDDEN,
                "ACCESS_DENIED", "You do not have permission for this operation.", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT,
                "OPTIMISTIC_LOCK", "The record was changed by another user. Reload and try again.",
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION", "The operation conflicts with stored data or constraints.",
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(error(status, status.name(), ex.getReason(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE", "Send structured JSON only.", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST,
                "INVALID_JSON", "O pedido contém JSON inválido ou valores não suportados.",
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected API failure path={}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(error(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "An unexpected error occurred.", request.getRequestURI(), List.of()));
    }

    private ErrorResponse error(HttpStatus status, String code, String message, String path, List<String> details) {
        return new ErrorResponse(OffsetDateTime.now(ZoneOffset.UTC), status.value(), code, message, path, details);
    }
}
