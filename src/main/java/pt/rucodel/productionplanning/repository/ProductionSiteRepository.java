package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionSiteRepository extends JpaRepository<ProductionSiteEntity, UUID> {
    Optional<ProductionSiteEntity> findByCode(ProductionSiteCode code);

    List<ProductionSiteEntity> findByActiveTrueOrderByCodeAsc();
}
