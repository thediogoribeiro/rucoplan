package pt.rucodel.productionplanning.controller;

import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.dto.AdminDashboardResponse;
import pt.rucodel.productionplanning.dto.PlanResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.ProductionPlanService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
public class ProductionPlanController {
    private final ProductionPlanService plans;
    private final CurrentUserService currentUserService;

    public ProductionPlanController(ProductionPlanService plans, CurrentUserService currentUserService) {
        this.plans = plans;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/plans")
    public PlanResponse get(@RequestParam LocalDate date) {
        return plans.getOrGenerate(date);
    }

    @PostMapping("/plans/generate")
    public PlanResponse generate(@RequestParam LocalDate date) {
        return plans.generate(date, GenerationTrigger.MANUAL, currentUserService.requireUser());
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard(@RequestParam LocalDate date) {
        return plans.dashboard(date);
    }
}
