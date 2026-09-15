package pt.rucodel.productionplanning.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.exception.ForbiddenOperationException;

import java.util.UUID;

@Service
public class CurrentUserService {
    public AuthenticatedUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ForbiddenOperationException("Authentication is required.");
        }
        return user;
    }

    public UUID requireDriverId() {
        AuthenticatedUser user = requireUser();
        if (user.role() != UserRole.DRIVER || user.driverId() == null) {
            throw new ForbiddenOperationException("A driver account is required.");
        }
        return user.driverId();
    }
}
