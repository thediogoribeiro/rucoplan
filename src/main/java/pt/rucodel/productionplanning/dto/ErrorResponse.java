package pt.rucodel.productionplanning.dto;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details,
        String correlationId
) {
    public static ErrorResponse problem(HttpStatus status, String code, String message, String path,
                                        List<String> details, String correlationId) {
        return new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                code,
                code,
                message,
                path,
                details,
                correlationId
        );
    }
}
