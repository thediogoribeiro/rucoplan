package pt.rucodel.productionplanning.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.PlanningAuditEventEntity;

import java.util.UUID;

public interface PlanningAuditEventRepository extends JpaRepository<PlanningAuditEventEntity, UUID> {
    Page<PlanningAuditEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
