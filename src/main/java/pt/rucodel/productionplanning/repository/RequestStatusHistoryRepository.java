package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.RequestStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistoryEntity, UUID> {
    List<RequestStatusHistoryEntity> findByRequestIdOrderByChangedAtAsc(UUID requestId);
}
