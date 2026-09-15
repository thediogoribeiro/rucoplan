package pt.rucodel.productionplanning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.DailySettingsRequest;
import pt.rucodel.productionplanning.dto.DailySettingsResponse;
import pt.rucodel.productionplanning.dto.TimeWindowDto;
import pt.rucodel.productionplanning.entity.DailyProductionSettingsEntity;
import pt.rucodel.productionplanning.entity.ProductionTimeWindowEntity;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.DailyProductionSettingsRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductionSettingsService {
    public static final String DEFAULT_KEY = "DEFAULT";

    private final DailyProductionSettingsRepository settings;
    private final ApiMapper mapper;
    private final RecalculationService recalculationService;
    private final CapacityAlertService capacityAlertService;
    private final int defaultFallbackMinutes;

    public ProductionSettingsService(DailyProductionSettingsRepository settings, ApiMapper mapper,
                                     RecalculationService recalculationService,
                                     CapacityAlertService capacityAlertService,
                                     @Value("${app.planning.fallback-minutes-per-wheel}") int defaultFallbackMinutes) {
        this.settings = settings;
        this.mapper = mapper;
        this.recalculationService = recalculationService;
        this.capacityAlertService = capacityAlertService;
        this.defaultFallbackMinutes = defaultFallbackMinutes;
    }

    @Transactional
    public DailyProductionSettingsEntity effectiveSettings(LocalDate date) {
        return settings.findBySettingsDate(date).orElseGet(this::defaultSettings);
    }

    @Transactional(readOnly = true)
    public DailySettingsResponse get(LocalDate date) {
        return mapper.toSettings(settings.findBySettingsDate(date)
                .or(() -> settings.findBySettingsKey(DEFAULT_KEY))
                .orElseGet(this::newUnsavedDefaultSettings));
    }

    @Transactional
    public DailySettingsResponse update(LocalDate date, DailySettingsRequest request, String actor) {
        DailyProductionSettingsEntity entity = settings.findBySettingsDate(date).orElseGet(() -> {
            DailyProductionSettingsEntity created = new DailyProductionSettingsEntity();
            created.setSettingsKey("DATE:" + date);
            created.setSettingsDate(date);
            created.setCreatedBy(actor);
            return created;
        });
        if (request.version() != null && entity.getId() != null && request.version() != entity.getVersion()) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "Daily settings were changed by another user.");
        }
        apply(entity, request, actor);
        DailyProductionSettingsEntity saved = settings.save(entity);
        recalculationService.markCurrentAndFuturePlans();
        capacityAlertService.recalculate(date);
        return mapper.toSettings(saved);
    }

    @Transactional
    public DailyProductionSettingsEntity defaultSettings() {
        return settings.findBySettingsKey(DEFAULT_KEY).orElseGet(() -> {
            DailyProductionSettingsEntity entity = newUnsavedDefaultSettings();
            return settings.save(entity);
        });
    }

    private void apply(DailyProductionSettingsEntity entity, DailySettingsRequest request, String actor) {
        entity.setDailyCapacity(request.dailyCapacity());
        entity.setDailyTarget(request.dailyTarget());
        entity.setFallbackMinutesPerWheel(request.fallbackMinutesPerWheel());
        entity.setUpdatedBy(actor);
        List<TimeWindowDto> requestedWindows = request.timeWindows() == null || request.timeWindows().isEmpty()
                ? defaultWindows()
                : request.timeWindows();
        validateWindows(requestedWindows);
        entity.replaceTimeWindows(toEntities(requestedWindows));
    }

    private DailyProductionSettingsEntity newUnsavedDefaultSettings() {
        DailyProductionSettingsEntity entity = new DailyProductionSettingsEntity();
        entity.setSettingsKey(DEFAULT_KEY);
        entity.setSettingsDate(null);
        entity.setDailyCapacity(40);
        entity.setDailyTarget(32);
        entity.setFallbackMinutesPerWheel(defaultFallbackMinutes);
        entity.replaceTimeWindows(toEntities(defaultWindows()));
        return entity;
    }

    private List<ProductionTimeWindowEntity> toEntities(List<TimeWindowDto> windows) {
        List<TimeWindowDto> sorted = windows.stream()
                .sorted(Comparator.comparing(window -> window.sortOrder() == null ? 0 : window.sortOrder()))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (!sorted.get(i).cutoffTime().isAfter(sorted.get(i - 1).cutoffTime())) {
                throw new InvalidRequestException("Production time-window cut-offs must be strictly increasing.");
            }
        }
        java.util.ArrayList<ProductionTimeWindowEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            TimeWindowDto dto = sorted.get(i);
            ProductionTimeWindowEntity entity = new ProductionTimeWindowEntity();
            entity.setLabel(dto.label().trim());
            entity.setCutoffTime(dto.cutoffTime());
            entity.setSortOrder(dto.sortOrder() == null ? i + 1 : dto.sortOrder());
            entities.add(entity);
        }
        return entities;
    }

    private List<TimeWindowDto> defaultWindows() {
        return List.of(
                new TimeWindowDto("Fim da manhã", LocalTime.of(12, 30), 1),
                new TimeWindowDto("Meio da tarde", LocalTime.of(15, 30), 2),
                new TimeWindowDto("Fim do dia", LocalTime.of(18, 30), 3)
        );
    }

    private void validateWindows(List<TimeWindowDto> windows) {
        if (windows.isEmpty()) {
            throw new InvalidRequestException("At least one production time window is required.");
        }
        windows.forEach(window -> {
            if (window.label() == null || window.label().isBlank()) {
                throw new InvalidRequestException("Production time-window labels are required.");
            }
            if (window.cutoffTime() == null) {
                throw new InvalidRequestException("Production time-window cut-offs are required.");
            }
        });
    }
}
