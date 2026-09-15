package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.entity.ProductionPlanItemEntity;

import java.util.List;
import java.util.UUID;

public interface ProductionPlanItemRepository extends JpaRepository<ProductionPlanItemEntity, UUID> {
    List<ProductionPlanItemEntity> findByPlanIdOrderByPriorityScoreAsc(UUID planId);

    @Query("""
            select i from ProductionPlanItemEntity i
            where i.request.id = :requestId
              and i.plan.planningDate < :date
              and i.plan.status = :status
              and i.remainingQuantity > 0
            order by i.plan.planningDate desc
            """)
    List<ProductionPlanItemEntity> findClosedCarryOver(
            @Param("requestId") UUID requestId,
            @Param("date") java.time.LocalDate date,
            @Param("status") pt.rucodel.productionplanning.domain.ProductionPlanStatus status
    );
}
