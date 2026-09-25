package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.PlanningRunEntity;

import java.util.Optional;
import java.util.UUID;

public interface PlanningRunRepository extends JpaRepository<PlanningRunEntity, UUID> {
    Optional<PlanningRunEntity> findFirstByOrderByStartedAtDesc();
}
