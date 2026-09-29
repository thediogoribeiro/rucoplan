package pt.rucodel.productionplanning.security.local;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalAuthenticationProperties {
    private final String username;
    private final String password;
    private final String displayName;

    public LocalAuthenticationProperties(
            @Value("${app.bootstrap-admin.username:admin}") String username,
            @Value("${app.bootstrap-admin.password:admin123}") String password,
            @Value("${app.bootstrap-admin.display-name:Administrador}") String displayName
    ) {
        this.username = username == null || username.isBlank() ? "admin" : username.trim();
        this.password = password == null || password.isBlank() ? "admin123" : password;
        this.displayName = displayName == null || displayName.isBlank() ? "Administrador" : displayName.trim();
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String displayName() {
        return displayName;
    }
}
