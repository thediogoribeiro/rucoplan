package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.DailyProductionSettingsEntity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyProductionSettingsRepository extends JpaRepository<DailyProductionSettingsEntity, UUID> {
    Optional<DailyProductionSettingsEntity> findBySettingsKey(String settingsKey);

    Optional<DailyProductionSettingsEntity> findBySettingsDate(LocalDate settingsDate);
}
