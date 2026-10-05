package pt.rucodel.productionplanning.security.production;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.security.UsernameNormalizer;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "DATABASE")
public class DatabaseUserDetailsService implements UserDetailsService {
    private final ApplicationUserRepository users;

    public DatabaseUserDetailsService(ApplicationUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        ApplicationUserEntity user = users.findWithDriverByNormalizedUsername(UsernameNormalizer.normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
