package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.rucodel.productionplanning.dto.AuthUserResponse;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.dto.LoginResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.me(currentUserService.requireUser());
    }
}
