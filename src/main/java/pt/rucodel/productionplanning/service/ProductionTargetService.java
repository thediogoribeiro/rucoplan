package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.PlanningTargetDefaults;
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
                .orElseGet(() -> systemDefaultTarget(date));
    }

    @Transactional(readOnly = true)
    public List<PlanningTargetResponse> list() {
        List<ProductionTargetConfigurationEntity> configured = targets.findAllByOrderByEffectiveFromDescCreatedAtDesc();
        if (configured.isEmpty()) {
            return List.of(toResponse(systemDefaultTarget(LocalDate.now())));
        }
        return configured.stream()
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
            throw new InvalidRequestException("O target mínimo não pode ser negativo.");
        }
        if (regularDailyCapacity <= 0) {
            throw new InvalidRequestException("O target máximo deve ser superior a zero.");
        }
        if (minimumDailyTarget > regularDailyCapacity) {
            throw new InvalidRequestException("O target mínimo não pode ser superior ao target máximo.");
        }
    }

    private PlanningTargetResponse toResponse(ProductionTargetConfigurationEntity entity) {
        return new PlanningTargetResponse(
                entity.getId(),
                entity.getMinimumDailyTarget(),
                entity.getRegularDailyCapacity(),
                entity.getEffectiveFrom(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.isSystemDefault()
                        ? PlanningTargetDefaults.SOURCE_SYSTEM_DEFAULT
                        : PlanningTargetDefaults.SOURCE_DATABASE
        );
    }

    private ProductionTargetConfigurationEntity systemDefaultTarget(LocalDate date) {
        ProductionTargetConfigurationEntity fallback = new ProductionTargetConfigurationEntity();
        fallback.setMinimumDailyTarget(PlanningTargetDefaults.MINIMUM_DAILY_TARGET);
        fallback.setRegularDailyCapacity(PlanningTargetDefaults.REGULAR_DAILY_CAPACITY);
        fallback.setEffectiveFrom(date == null ? PlanningTargetDefaults.EFFECTIVE_FROM : date);
        fallback.setCreatedBy(PlanningTargetDefaults.CREATED_BY);
        fallback.markSystemDefault();
        return fallback;
    }
}
