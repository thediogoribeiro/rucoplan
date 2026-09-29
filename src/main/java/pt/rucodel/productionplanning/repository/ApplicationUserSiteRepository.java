package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ApplicationUserSiteEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationUserSiteRepository extends JpaRepository<ApplicationUserSiteEntity, UUID> {
    @Query("""
            select s from ApplicationUserSiteEntity s
            where s.user.id = :userId
              and s.productionSite.code = :code
              and s.active = true
            """)
    Optional<ApplicationUserSiteEntity> findByUserIdAndProductionSiteCodeAndActiveTrue(@Param("userId") UUID userId,
                                                                                       @Param("code") ProductionSiteCode code);

    @Query("""
            select s from ApplicationUserSiteEntity s
            where s.user.id = :userId
              and s.active = true
            order by s.productionSite.code asc
            """)
    List<ApplicationUserSiteEntity> findByUserIdAndActiveTrueOrderByProductionSiteCodeAsc(@Param("userId") UUID userId);
}
