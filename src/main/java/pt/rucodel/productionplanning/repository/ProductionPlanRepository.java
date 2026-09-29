package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionPlanStatus;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionPlanEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlanEntity, UUID> {
    Optional<ProductionPlanEntity> findFirstByPlanningDateOrderByVersionNumberDesc(LocalDate planningDate);

    Optional<ProductionPlanEntity> findFirstByProductionSite_CodeAndPlanningDateOrderByVersionNumberDesc(
            ProductionSiteCode siteCode,
            LocalDate planningDate
    );

    Optional<ProductionPlanEntity> findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(LocalDate planningDate);

    Optional<ProductionPlanEntity> findFirstByProductionSite_CodeAndPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(
            ProductionSiteCode siteCode,
            LocalDate planningDate
    );

    List<ProductionPlanEntity> findByPlanningDateOrderByVersionNumberDesc(LocalDate planningDate);

    List<ProductionPlanEntity> findByProductionSite_CodeAndPlanningDateOrderByVersionNumberDesc(
            ProductionSiteCode siteCode,
            LocalDate planningDate
    );

    List<ProductionPlanEntity> findByPlanningDateBetweenAndCurrentPlanTrueOrderByPlanningDateAsc(LocalDate from, LocalDate to);

    List<ProductionPlanEntity> findByProductionSite_CodeAndPlanningDateBetweenAndCurrentPlanTrueOrderByPlanningDateAsc(
            ProductionSiteCode siteCode,
            LocalDate from,
            LocalDate to
    );

    long countByCurrentPlanTrueAndStatusNot(ProductionPlanStatus status);

    long countByProductionSite_CodeAndCurrentPlanTrueAndStatusNot(ProductionSiteCode siteCode, ProductionPlanStatus status);

    @Query("select coalesce(max(p.versionNumber), 0) from ProductionPlanEntity p where p.planningDate = :date")
    int findMaxVersion(@Param("date") LocalDate date);

    @Query("""
            select coalesce(max(p.versionNumber), 0) from ProductionPlanEntity p
            where p.productionSite.code = :siteCode
              and p.planningDate = :date
            """)
    int findMaxVersionForSite(@Param("siteCode") ProductionSiteCode siteCode, @Param("date") LocalDate date);

    @Modifying
    @Query("update ProductionPlanEntity p set p.currentPlan = false where p.planningDate = :date and p.currentPlan = true")
    int clearCurrentPlan(@Param("date") LocalDate date);

    @Modifying
    @Query("""
            update ProductionPlanEntity p
            set p.currentPlan = false
            where p.productionSite.code = :siteCode
              and p.planningDate = :date
              and p.currentPlan = true
            """)
    int clearCurrentPlanForSite(@Param("siteCode") ProductionSiteCode siteCode, @Param("date") LocalDate date);

    @Modifying
    @Query("""
            update ProductionPlanEntity p
            set p.requiresRecalculation = true
            where p.currentPlan = true
              and p.planningDate >= :date
            """)
    int markCurrentPlansForRecalculation(@Param("date") LocalDate date);

    @Modifying
    @Query("""
            update ProductionPlanEntity p
            set p.requiresRecalculation = true
            where p.productionSite.code = :siteCode
              and p.currentPlan = true
              and p.planningDate >= :date
            """)
    int markCurrentPlansForRecalculationForSite(@Param("siteCode") ProductionSiteCode siteCode, @Param("date") LocalDate date);

    @Query("select max(p.planningDate) from ProductionPlanEntity p where p.currentPlan = true")
    LocalDate findMaxCurrentPlanningDate();

    @Query("""
            select max(p.planningDate) from ProductionPlanEntity p
            where p.productionSite.code = :siteCode
              and p.currentPlan = true
            """)
    LocalDate findMaxCurrentPlanningDateForSite(@Param("siteCode") ProductionSiteCode siteCode);
}
