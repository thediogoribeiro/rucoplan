package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.PlanningTargetRequest;
import pt.rucodel.productionplanning.dto.PlanningTargetResponse;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.ProductionTargetConfigurationRepository;

import java.time.LocalDate;
import java.util.List;

@Service
public class ProductionTargetService {
    private final ProductionTargetConfigurationRepository targets;

    public ProductionTargetService(ProductionTargetConfigurationRepository targets) {
        this.targets = targets;
    }

    @Transactional(readOnly = true)
    public ProductionTargetConfigurationEntity effectiveFor(LocalDate date) {
        return targets.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(date)
                .orElseGet(() -> {
                    ProductionTargetConfigurationEntity fallback = new ProductionTargetConfigurationEntity();
                    fallback.setMinimumDailyTarget(32);
                    fallback.setRegularDailyCapacity(40);
                    fallback.setEffectiveFrom(date);
                    fallback.setCreatedBy("SYSTEM");
                    return fallback;
                });
    }

    @Transactional(readOnly = true)
    public List<PlanningTargetResponse> list() {
        return targets.findAllByOrderByEffectiveFromDescCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PlanningTargetResponse create(PlanningTargetRequest request, String actor) {
        validate(request.minimumDailyTarget(), request.regularDailyCapacity());
        ProductionTargetConfigurationEntity entity = new ProductionTargetConfigurationEntity();
        entity.setMinimumDailyTarget(request.minimumDailyTarget());
        entity.setRegularDailyCapacity(request.regularDailyCapacity());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setCreatedBy(actor);
        ProductionTargetConfigurationEntity saved = targets.saveAndFlush(entity);
        return toResponse(saved);
    }

    private void validate(int minimumDailyTarget, int regularDailyCapacity) {
        if (minimumDailyTarget < 0) {
            throw new InvalidRequestException("Target mínimo diário tem de ser maior ou igual a zero.");
        }
        if (regularDailyCapacity <= 0) {
            throw new InvalidRequestException("Target máximo diário — capacidade regular tem de ser superior a zero.");
        }
        if (minimumDailyTarget > regularDailyCapacity) {
            throw new InvalidRequestException("Target mínimo diário não pode ser superior ao target máximo diário.");
        }
    }

    private PlanningTargetResponse toResponse(ProductionTargetConfigurationEntity entity) {
        return new PlanningTargetResponse(
                entity.getId(),
                entity.getMinimumDailyTarget(),
                entity.getRegularDailyCapacity(),
                entity.getEffectiveFrom(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }
}
