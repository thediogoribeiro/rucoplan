package pt.rucodel.productionplanning.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.AuthUserResponse;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.dto.LoginResponse;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.security.AuthenticatedUser;
import pt.rucodel.productionplanning.security.TokenService;

@Service
public class AuthService {
    private final ApplicationUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(ApplicationUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        ApplicationUserEntity user = users.findWithDriverByUsername(request.username())
                .filter(ApplicationUserEntity::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        TokenService.IssuedToken issued = tokenService.issue(user);
        return new LoginResponse(issued.token(), issued.expiresAt(), toResponse(user));
    }

    public AuthUserResponse me(AuthenticatedUser user) {
        return new AuthUserResponse(user.id(), user.username(), user.displayName(), user.role(), user.driverId());
    }

    private AuthUserResponse toResponse(ApplicationUserEntity user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getDriver() == null ? null : user.getDriver().getId()
        );
    }
}
