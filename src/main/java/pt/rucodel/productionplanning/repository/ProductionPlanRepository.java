package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionPlanStatus;
import pt.rucodel.productionplanning.entity.ProductionPlanEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlanEntity, UUID> {
    Optional<ProductionPlanEntity> findFirstByPlanningDateOrderByVersionNumberDesc(LocalDate planningDate);

    Optional<ProductionPlanEntity> findFirstByPlanningDateAndCurrentPlanTrueOrderByVersionNumberDesc(LocalDate planningDate);

    List<ProductionPlanEntity> findByPlanningDateOrderByVersionNumberDesc(LocalDate planningDate);

    List<ProductionPlanEntity> findByPlanningDateBetweenAndCurrentPlanTrueOrderByPlanningDateAsc(LocalDate from, LocalDate to);

    long countByCurrentPlanTrueAndStatusNot(ProductionPlanStatus status);

    @Query("select coalesce(max(p.versionNumber), 0) from ProductionPlanEntity p where p.planningDate = :date")
    int findMaxVersion(@Param("date") LocalDate date);

    @Modifying
    @Query("update ProductionPlanEntity p set p.currentPlan = false where p.planningDate = :date and p.currentPlan = true")
    int clearCurrentPlan(@Param("date") LocalDate date);

    @Modifying
    @Query("""
            update ProductionPlanEntity p
            set p.requiresRecalculation = true
            where p.currentPlan = true
              and p.planningDate >= :date
            """)
    int markCurrentPlansForRecalculation(@Param("date") LocalDate date);

    @Query("select max(p.planningDate) from ProductionPlanEntity p where p.currentPlan = true")
    LocalDate findMaxCurrentPlanningDate();
}
