package pt.rucodel.productionplanning.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import pt.rucodel.productionplanning.dto.ErrorResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");

    @ExceptionHandler(ProductionPlanningException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ProductionPlanningException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.errorCode()) {
            case "ENTITY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN_OPERATION" -> HttpStatus.FORBIDDEN;
            case "DUPLICATE_MESSAGE", "OPTIMISTIC_LOCK", "AMBIGUOUS_CUSTOMER", "PRODUCTION_PLAN_CLOSED",
                 "PRODUCTION_PLAN_CONFLICT" -> HttpStatus.CONFLICT;
            case "AUTHENTICATION_FAILED" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        return response(status, error(status, ex.errorCode(), ex.getMessage(), request.getRequestURI(), List.of(), correlationId(request)));
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
        return response(HttpStatus.BAD_REQUEST, error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "The request is not valid.", request.getRequestURI(), details, correlationId(request)));
    }

    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleCredentials(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, error(HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED", "Invalid username or password.", request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, error(HttpStatus.FORBIDDEN,
                "ACCESS_DENIED", "You do not have permission for this operation.", request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, error(HttpStatus.CONFLICT,
                "OPTIMISTIC_LOCK", "The record was changed by another user. Reload and try again.",
                request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, error(HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION", "The operation conflicts with stored data or constraints.",
                request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(DataAccessException ex, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOGGER.error("Database unavailable path={} correlationId={}", request.getRequestURI(), correlationId, ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE, error(HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE", "Não foi possível estabelecer ligação à base de dados.",
                request.getRequestURI(), List.of(), correlationId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return response(status, error(status, status.name(), ex.getReason(), request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE", "Send structured JSON only.", request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, error(HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED", "O método HTTP não é suportado neste endpoint.",
                request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, error(HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_PARAMETER", "Um parâmetro do pedido não tem o formato esperado.",
                request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, error(HttpStatus.BAD_REQUEST,
                "INVALID_JSON", "O pedido contém JSON inválido ou valores não suportados.",
                request.getRequestURI(), List.of(), correlationId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOGGER.error("Unexpected API failure path={} correlationId={}", request.getRequestURI(), correlationId, ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, error(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "An unexpected error occurred.", request.getRequestURI(), List.of(), correlationId));
    }

    private ErrorResponse error(HttpStatus status, String code, String message, String path, List<String> details) {
        return error(status, code, message, path, details, UUID.randomUUID().toString());
    }

    private ErrorResponse error(HttpStatus status, String code, String message, String path, List<String> details, String correlationId) {
        return ErrorResponse.problem(status, code, message, path, details, correlationId);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorResponse error) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Correlation-ID", error.correlationId())
                .body(error);
    }

    private String correlationId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Correlation-ID");
        if (supplied != null && SAFE_CORRELATION_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
