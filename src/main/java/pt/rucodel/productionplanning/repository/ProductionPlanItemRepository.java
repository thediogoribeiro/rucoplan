package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionPlanItemEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProductionPlanItemRepository extends JpaRepository<ProductionPlanItemEntity, UUID> {
    long countByProductionSite_Code(ProductionSiteCode siteCode);

    List<ProductionPlanItemEntity> findByPlanIdOrderByPriorityScoreAsc(UUID planId);

    @Query("""
            select i from ProductionPlanItemEntity i
            where i.productionSite.code = :siteCode
              and i.plan.id = :planId
            order by i.priorityScore asc
            """)
    List<ProductionPlanItemEntity> findByPlanIdForSiteOrderByPriorityScoreAsc(@Param("siteCode") ProductionSiteCode siteCode,
                                                                               @Param("planId") UUID planId);

    @Query("""
            select i from ProductionPlanItemEntity i
            where i.plan.id = :planId
              and i.lineStatus not in (
                pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.CLOSED_COMPLETE,
                pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.CLOSED_PARTIAL
              )
            order by i.priorityScore asc
            """)
    List<ProductionPlanItemEntity> findOpenByPlanIdOrderByPriorityScoreAsc(@Param("planId") UUID planId);

    @Query("""
            select i from ProductionPlanItemEntity i
            where i.productionSite.code = :siteCode
              and i.plan.id = :planId
              and i.lineStatus not in (
                pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.CLOSED_COMPLETE,
                pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.CLOSED_PARTIAL
              )
            order by i.priorityScore asc
            """)
    List<ProductionPlanItemEntity> findOpenByPlanIdForSiteOrderByPriorityScoreAsc(@Param("siteCode") ProductionSiteCode siteCode,
                                                                                   @Param("planId") UUID planId);

    @Query("""
            select i from ProductionPlanItemEntity i
            join fetch i.plan p
            join fetch i.request r
            where p.planningDate = :productionDate
              and p.currentPlan = true
            order by i.priorityScore asc
            """)
    List<ProductionPlanItemEntity> findCurrentByProductionDate(@Param("productionDate") LocalDate productionDate);

    @Query("""
            select i from ProductionPlanItemEntity i
            join fetch i.plan p
            join fetch i.request r
            where i.productionSite.code = :siteCode
              and p.planningDate = :productionDate
              and p.currentPlan = true
            order by i.priorityScore asc
            """)
    List<ProductionPlanItemEntity> findCurrentByProductionDateForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                                       @Param("productionDate") LocalDate productionDate);

    @Query("""
            select i from ProductionPlanItemEntity i
            where i.request.id = :requestId
              and i.plan.planningDate < :date
              and (i.plan.status = :status or i.lineStatus = pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.CLOSED_PARTIAL)
              and i.remainingQuantity > 0
            order by i.plan.planningDate desc
            """)
    List<ProductionPlanItemEntity> findClosedCarryOver(
            @Param("requestId") UUID requestId,
            @Param("date") java.time.LocalDate date,
            @Param("status") pt.rucodel.productionplanning.domain.ProductionPlanStatus status
    );

    @Query("""
            select i from ProductionPlanItemEntity i
            where i.productionSite.code = :siteCode
              and i.request.id = :requestId
              and i.plan.planningDate < :date
              and (i.plan.status = :status or i.lineStatus = pt.rucodel.productionplanning.domain.ProductionPlanLineStatus.CLOSED_PARTIAL)
              and i.remainingQuantity > 0
            order by i.plan.planningDate desc
            """)
    List<ProductionPlanItemEntity> findClosedCarryOverForSite(
            @Param("siteCode") ProductionSiteCode siteCode,
            @Param("requestId") UUID requestId,
            @Param("date") java.time.LocalDate date,
            @Param("status") pt.rucodel.productionplanning.domain.ProductionPlanStatus status
    );
}
