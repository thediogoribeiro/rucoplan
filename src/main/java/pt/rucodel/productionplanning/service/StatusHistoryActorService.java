package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.security.AuthenticatedUser;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class StatusHistoryActorService {
    private static final String DISABLED_PASSWORD_HASH = "!DISABLED_AUDIT_ACTOR!";

    private final ApplicationUserRepository users;

    public StatusHistoryActorService(ApplicationUserRepository users) {
        this.users = users;
    }

    @Transactional
    public UUID authenticatedActorId(AuthenticatedUser user) {
        if (user == null) {
            return technicalActorId("SYSTEM", "Sistema");
        }
        if (user.id() != null && users.existsById(user.id())) {
            return user.id();
        }
        String username = user.username() == null || user.username().isBlank()
                ? "audit-authenticated-unknown"
                : user.username().trim();
        return users.findByUsernameIgnoreCase(username)
                .map(ApplicationUserEntity::getId)
                .orElseGet(() -> disabledActorId(
                        username,
                        user.displayName() == null || user.displayName().isBlank() ? username : user.displayName(),
                        user.role() == null || user.role() == UserRole.DRIVER ? UserRole.ADMIN : user.role(),
                        user.id()));
    }

    @Transactional
    public UUID technicalActorId(String source, String displayName) {
        String normalizedSource = source == null || source.isBlank()
                ? "SYSTEM"
                : source.trim().toUpperCase(Locale.ROOT);
        String username = "audit-" + normalizedSource.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String name = displayName == null || displayName.isBlank() ? normalizedSource : displayName.trim();
        UUID stableId = UUID.nameUUIDFromBytes(("audit-actor:" + username).getBytes(StandardCharsets.UTF_8));
        return users.findByUsernameIgnoreCase(username)
                .map(ApplicationUserEntity::getId)
                .orElseGet(() -> disabledActorId(username, name, UserRole.ADMIN, stableId));
    }

    private UUID disabledActorId(String username, String displayName, UserRole role, UUID preferredId) {
        ApplicationUserEntity actor = new ApplicationUserEntity();
        if (preferredId != null) {
            actor.setId(preferredId);
        }
        actor.setUsername(username);
        actor.setDisplayName(displayName);
        actor.setRole(role);
        actor.setPasswordHash(DISABLED_PASSWORD_HASH);
        actor.setActive(false);
        actor.setCreatedBy("AUDIT_ACTOR_RESOLVER");
        actor.setUpdatedBy("AUDIT_ACTOR_RESOLVER");
        return users.save(actor).getId();
    }
}
