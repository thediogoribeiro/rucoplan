package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.rucodel.productionplanning.dto.AuthUserResponse;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.dto.LoginResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.security.TokenService;
import pt.rucodel.productionplanning.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public static final String STREAM_AUTH_COOKIE = "RUCOPLAN_STREAM_AUTH";

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, CurrentUserService currentUserService, TokenService tokenService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, streamCookie(response.token(), response.expiresAt()).toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearStreamCookie().toString())
                .build();
    }

    @PostMapping("/stream-session")
    public ResponseEntity<Void> streamSession(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = authorization == null || !authorization.startsWith("Bearer ")
                ? ""
                : authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        var claims = tokenService.parse(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, streamCookie(token, claims.expiresAt()).toString())
                .build();
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.me(currentUserService.requireUser());
    }

    private ResponseCookie streamCookie(String token, java.time.OffsetDateTime expiresAt) {
        long maxAge = Math.max(0, java.time.Duration.between(java.time.OffsetDateTime.now(), expiresAt).getSeconds());
        return ResponseCookie.from(STREAM_AUTH_COOKIE, token)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api/v1/admin/dashboard/stream")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearStreamCookie() {
        return ResponseCookie.from(STREAM_AUTH_COOKIE, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api/v1/admin/dashboard/stream")
                .maxAge(0)
                .build();
    }
}
