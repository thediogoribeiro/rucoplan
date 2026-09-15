package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.domain.CapacityAlertStatus;
import pt.rucodel.productionplanning.domain.CapacityAlertType;
import pt.rucodel.productionplanning.entity.CapacityAlertEntity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapacityAlertRepository extends JpaRepository<CapacityAlertEntity, UUID> {
    List<CapacityAlertEntity> findByStatusInOrderByAffectedDateAscCreatedAtAsc(Collection<CapacityAlertStatus> statuses);

    Optional<CapacityAlertEntity> findFirstByTypeAndAffectedDateAndStatusIn(
            CapacityAlertType type,
            LocalDate affectedDate,
            Collection<CapacityAlertStatus> statuses
    );
}
