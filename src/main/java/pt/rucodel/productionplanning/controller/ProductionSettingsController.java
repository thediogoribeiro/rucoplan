package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.DailySettingsRequest;
import pt.rucodel.productionplanning.dto.DailySettingsResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.ProductionSettingsService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/settings/daily")
public class ProductionSettingsController {
    private final ProductionSettingsService settings;
    private final CurrentUserService currentUserService;

    public ProductionSettingsController(ProductionSettingsService settings, CurrentUserService currentUserService) {
        this.settings = settings;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public DailySettingsResponse get(@RequestParam LocalDate date) {
        return settings.get(date);
    }

    @PutMapping
    public DailySettingsResponse update(@RequestParam LocalDate date, @Valid @RequestBody DailySettingsRequest request) {
        return settings.update(date, request, currentUserService.requireUser().actorLabel());
    }
}
