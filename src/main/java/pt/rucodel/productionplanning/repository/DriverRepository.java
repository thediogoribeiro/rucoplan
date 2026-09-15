package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.DriverEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID> {
    Optional<DriverEntity> findByExternalId(String externalId);

    Optional<DriverEntity> findByTelegramUserId(Long telegramUserId);

    List<DriverEntity> findByActiveTrueOrderByName();
}
