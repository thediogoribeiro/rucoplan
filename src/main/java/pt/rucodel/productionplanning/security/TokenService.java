package pt.rucodel.productionplanning.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String secret;
    private final long ttlMinutes;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public TokenService(
            @Value("${app.auth.token-secret}") String secret,
            @Value("${app.auth.token-ttl-minutes}") long ttlMinutes,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.secret = secret;
        this.ttlMinutes = ttlMinutes;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public IssuedToken issue(ApplicationUserEntity user) {
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusMinutes(ttlMinutes);
        UUID driverId = user.getDriver() == null ? null : user.getDriver().getId();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId().toString());
        payload.put("username", user.getUsername());
        payload.put("displayName", user.getDisplayName());
        payload.put("role", user.getRole().name());
        payload.put("driverId", driverId == null ? "" : driverId.toString());
        payload.put("exp", Long.toString(expiresAt.toEpochSecond()));
        try {
            String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = sign(body);
            return new IssuedToken(body + "." + signature, expiresAt);
        } catch (Exception ex) {
            throw new InvalidRequestException("TOKEN_CREATION_FAILED", "Could not create authentication token.");
        }
    }

    public TokenClaims parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidRequestException("AUTHENTICATION_FAILED", "Invalid authentication token.");
        }
        String expected = sign(parts[0]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidRequestException("AUTHENTICATION_FAILED", "Invalid authentication token.");
        }
        try {
            Map<String, String> payload = objectMapper.readValue(DECODER.decode(parts[0]), new TypeReference<>() {});
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(Long.parseLong(payload.get("exp"))),
                    ZoneOffset.UTC
            );
            if (!expiresAt.isAfter(OffsetDateTime.now(clock))) {
                throw new InvalidRequestException("AUTHENTICATION_FAILED", "Authentication token has expired.");
            }
            String driverId = payload.get("driverId");
            return new TokenClaims(
                    UUID.fromString(payload.get("sub")),
                    payload.get("username"),
                    payload.get("displayName"),
                    UserRole.valueOf(payload.get("role")),
                    driverId == null || driverId.isBlank() ? null : UUID.fromString(driverId),
                    expiresAt
            );
        } catch (InvalidRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidRequestException("AUTHENTICATION_FAILED", "Invalid authentication token.");
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new InvalidRequestException("TOKEN_CREATION_FAILED", "Could not sign authentication token.");
        }
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

}
