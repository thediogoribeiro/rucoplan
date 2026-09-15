package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.domain.TelegramDraftStatus;
import pt.rucodel.productionplanning.entity.TelegramIntakeDraftEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelegramIntakeDraftRepository extends JpaRepository<TelegramIntakeDraftEntity, UUID> {
    Optional<TelegramIntakeDraftEntity> findFirstByDriverIdAndStatusOrderByCreatedAtDesc(UUID driverId, TelegramDraftStatus status);

    List<TelegramIntakeDraftEntity> findByDriverIdOrderByCreatedAtDesc(UUID driverId);
}
