package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionTargetConfigurationRepository extends JpaRepository<ProductionTargetConfigurationEntity, UUID> {
    Optional<ProductionTargetConfigurationEntity> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(LocalDate date);

    List<ProductionTargetConfigurationEntity> findAllByOrderByEffectiveFromDescCreatedAtDesc();
}
