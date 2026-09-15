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

    @Query("""
            select u from ApplicationUserEntity u
            left join fetch u.driver
            where u.username = :username
            """)
    Optional<ApplicationUserEntity> findWithDriverByUsername(String username);

    boolean existsByUsername(String username);

    List<ApplicationUserEntity> findByRoleOrderByDisplayName(UserRole role);
}
