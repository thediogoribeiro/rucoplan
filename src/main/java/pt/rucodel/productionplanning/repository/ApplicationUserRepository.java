package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationUserRepository extends JpaRepository<ApplicationUserEntity, UUID> {
    Optional<ApplicationUserEntity> findByUsername(String username);

    Optional<ApplicationUserEntity> findByUsernameIgnoreCase(String username);

    @Query("""
            select u from ApplicationUserEntity u
            left join fetch u.driver
            where lower(u.username) = :normalizedUsername
            """)
    Optional<ApplicationUserEntity> findWithDriverByNormalizedUsername(String normalizedUsername);

    @Query("""
            select u from ApplicationUserEntity u
            left join fetch u.driver
            where u.id = :id
            """)
    Optional<ApplicationUserEntity> findWithDriverById(UUID id);

    boolean existsByUsernameIgnoreCase(String username);

    List<ApplicationUserEntity> findByRoleOrderByDisplayName(UserRole role);
}
