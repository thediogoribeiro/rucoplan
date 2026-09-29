package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionPlanItemReconciliationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionPlanItemReconciliationRepository extends JpaRepository<ProductionPlanItemReconciliationEntity, UUID> {
    Optional<ProductionPlanItemReconciliationEntity> findFirstByProductionPlanItemIdAndRevertedFalseOrderByRevisionDesc(UUID itemId);

    Optional<ProductionPlanItemReconciliationEntity> findFirstByProductionSite_CodeAndProductionPlanItemIdAndRevertedFalseOrderByRevisionDesc(
            ProductionSiteCode siteCode,
            UUID itemId
    );

    List<ProductionPlanItemReconciliationEntity> findByProductionPlanItemIdOrderByRevisionDesc(UUID itemId);

    @Query("""
            select r from ProductionPlanItemReconciliationEntity r
            join fetch r.productionPlanItem i
            join fetch r.productionPlan p
            where r.request.id = :requestId
              and r.reverted = false
            order by r.planningDate desc, r.revision desc
            """)
    List<ProductionPlanItemReconciliationEntity> findActiveByRequestIdOrderByPlanningDateDescRevisionDesc(
            @Param("requestId") UUID requestId
    );

    @Query("""
            select r from ProductionPlanItemReconciliationEntity r
            join fetch r.productionPlanItem i
            join fetch r.productionPlan p
            where r.productionSite.code = :siteCode
              and r.request.id = :requestId
              and r.reverted = false
            order by r.planningDate desc, r.revision desc
            """)
    List<ProductionPlanItemReconciliationEntity> findActiveByRequestIdForSiteOrderByPlanningDateDescRevisionDesc(
            @Param("siteCode") ProductionSiteCode siteCode,
            @Param("requestId") UUID requestId
    );

    @Query("""
            select coalesce(max(r.revision), 0)
            from ProductionPlanItemReconciliationEntity r
            where r.productionPlanItem.id = :itemId
            """)
    int findMaxRevision(@Param("itemId") UUID itemId);

    @Query("""
            select coalesce(max(r.revision), 0)
            from ProductionPlanItemReconciliationEntity r
            where r.productionSite.code = :siteCode
              and r.productionPlanItem.id = :itemId
            """)
    int findMaxRevisionForSite(@Param("siteCode") ProductionSiteCode siteCode, @Param("itemId") UUID itemId);

    @Query("""
            select r from ProductionPlanItemReconciliationEntity r
            where r.request.id = :requestId
              and r.planningDate > :planningDate
              and r.reverted = false
            order by r.planningDate asc, r.closedAt asc
            """)
    List<ProductionPlanItemReconciliationEntity> findActiveLaterReconciliations(
            @Param("requestId") UUID requestId,
            @Param("planningDate") LocalDate planningDate
    );

    @Query("""
            select r from ProductionPlanItemReconciliationEntity r
            where r.productionSite.code = :siteCode
              and r.request.id = :requestId
              and r.planningDate > :planningDate
              and r.reverted = false
            order by r.planningDate asc, r.closedAt asc
            """)
    List<ProductionPlanItemReconciliationEntity> findActiveLaterReconciliationsForSite(
            @Param("siteCode") ProductionSiteCode siteCode,
            @Param("requestId") UUID requestId,
            @Param("planningDate") LocalDate planningDate
    );
}
