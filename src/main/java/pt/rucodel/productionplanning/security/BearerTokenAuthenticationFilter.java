package pt.rucodel.productionplanning.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.rucodel.productionplanning.controller.AuthController;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final ApplicationUserRepository users;

    public BearerTokenAuthenticationFilter(TokenService tokenService, ApplicationUserRepository users) {
        this.tokenService = tokenService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authenticate(header.substring("Bearer ".length()).trim());
        } else if (request.getRequestURI().endsWith("/stream")) {
            streamAuthCookie(request).ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private java.util.Optional<String> streamAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> AuthController.STREAM_AUTH_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private void authenticate(String token) {
        try {
            TokenClaims claims = tokenService.parse(token);
            ApplicationUserEntity user = users.findWithDriverByUsername(claims.username())
                    .filter(ApplicationUserEntity::isActive)
                    .filter(entity -> entity.getId().equals(claims.userId()))
                    .orElseThrow(() -> new InvalidRequestException("AUTHENTICATION_FAILED", "Invalid authentication token."));
            AuthenticatedUser principal = new AuthenticatedUser(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getDriver() == null ? null : user.getDriver().getId()
            );
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
