package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.PlanningTargetDefaults;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.dto.PlanningTargetRequest;
import pt.rucodel.productionplanning.dto.PlanningTargetResponse;
import pt.rucodel.productionplanning.entity.ProductionTargetConfigurationEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.ProductionTargetConfigurationRepository;

import java.time.LocalDate;
import java.util.List;

@Service
public class ProductionTargetService {
    private final ProductionTargetConfigurationRepository targets;
    private final ProductionSiteService productionSites;

    public ProductionTargetService(ProductionTargetConfigurationRepository targets, ProductionSiteService productionSites) {
        this.targets = targets;
        this.productionSites = productionSites;
    }

    @Transactional(readOnly = true)
    public ProductionTargetConfigurationEntity effectiveFor(LocalDate date) {
        return effectiveFor(ProductionSiteCode.PT, date);
    }

    @Transactional(readOnly = true)
    public ProductionTargetConfigurationEntity effectiveFor(ProductionSiteCode siteCode, LocalDate date) {
        return targets.findFirstByProductionSite_CodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(siteCode, date)
                .orElseGet(() -> {
                    if (siteCode == ProductionSiteCode.PT) {
                        return systemDefaultTarget(productionSites.portugal(), date);
                    }
                    throw new InvalidRequestException(
                            "TARGET_CONFIGURATION_MISSING",
                            "Configure os targets da unidade de produção antes de gerar o plano."
                    );
                });
    }

    @Transactional(readOnly = true)
    public List<PlanningTargetResponse> list() {
        return list(ProductionSiteCode.PT);
    }

    @Transactional(readOnly = true)
    public List<PlanningTargetResponse> list(ProductionSiteCode siteCode) {
        List<ProductionTargetConfigurationEntity> configured = targets.findAllByProductionSite_CodeOrderByEffectiveFromDescCreatedAtDesc(siteCode);
        if (configured.isEmpty()) {
            if (siteCode == ProductionSiteCode.PT) {
                return List.of(toResponse(systemDefaultTarget(productionSites.portugal(), LocalDate.now())));
            }
            return List.of();
        }
        return configured.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PlanningTargetResponse create(PlanningTargetRequest request, String actor) {
        return create(ProductionSiteCode.PT, request, actor);
    }

    @Transactional
    public PlanningTargetResponse create(ProductionSiteCode siteCode, PlanningTargetRequest request, String actor) {
        validate(request.minimumDailyTarget(), request.regularDailyCapacity());
        ProductionSiteEntity site = productionSites.requireActive(siteCode);
        ProductionTargetConfigurationEntity entity = new ProductionTargetConfigurationEntity();
        entity.setProductionSite(site);
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

    private ProductionTargetConfigurationEntity systemDefaultTarget(ProductionSiteEntity site, LocalDate date) {
        ProductionTargetConfigurationEntity fallback = new ProductionTargetConfigurationEntity();
        fallback.setProductionSite(site);
        fallback.setMinimumDailyTarget(PlanningTargetDefaults.MINIMUM_DAILY_TARGET);
        fallback.setRegularDailyCapacity(PlanningTargetDefaults.REGULAR_DAILY_CAPACITY);
        fallback.setEffectiveFrom(date == null ? PlanningTargetDefaults.EFFECTIVE_FROM : date);
        fallback.setCreatedBy(PlanningTargetDefaults.CREATED_BY);
        fallback.markSystemDefault();
        return fallback;
    }
}
