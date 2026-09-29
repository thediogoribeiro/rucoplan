package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.DriverProductionSiteEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverProductionSiteRepository extends JpaRepository<DriverProductionSiteEntity, UUID> {
    @Query("""
            select s from DriverProductionSiteEntity s
            where s.driver.id = :driverId
              and s.productionSite.code = :code
            """)
    Optional<DriverProductionSiteEntity> findByDriverIdAndProductionSiteCode(@Param("driverId") UUID driverId,
                                                                             @Param("code") ProductionSiteCode code);

    @Query("""
            select count(s) > 0 from DriverProductionSiteEntity s
            where s.driver.id = :driverId
              and s.productionSite.code = :code
              and s.active = true
            """)
    boolean existsByDriverIdAndProductionSiteCodeAndActiveTrue(@Param("driverId") UUID driverId,
                                                               @Param("code") ProductionSiteCode code);

    @Query("""
            select s from DriverProductionSiteEntity s
            where s.driver.id = :driverId
              and s.active = true
            order by s.productionSite.code asc
            """)
    List<DriverProductionSiteEntity> findByDriverIdAndActiveTrueOrderByProductionSiteCodeAsc(@Param("driverId") UUID driverId);
}
