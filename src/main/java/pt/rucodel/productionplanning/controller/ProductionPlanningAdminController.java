package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.dto.*;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.MultiDayProductionPlanningService;
import pt.rucodel.productionplanning.service.ProductionTargetService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class ProductionPlanningAdminController {
    private final MultiDayProductionPlanningService productionPlanning;
    private final ProductionTargetService targets;
    private final CurrentUserService currentUserService;

    public ProductionPlanningAdminController(MultiDayProductionPlanningService productionPlanning,
                                             ProductionTargetService targets,
                                             CurrentUserService currentUserService) {
        this.productionPlanning = productionPlanning;
        this.targets = targets;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/production-plans")
    public List<DailyProductionPlanResponse> listPlans(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return productionPlanning.list(from, to);
    }

    @GetMapping("/production-plans/{date}")
    public DailyProductionPlanResponse getPlan(@PathVariable LocalDate date) {
        return productionPlanning.getOrGenerate(date);
    }

    @PostMapping("/production-plans/{date}/recalculate")
    public DailyProductionPlanResponse recalculate(@PathVariable LocalDate date) {
        return productionPlanning.recalculate(date, GenerationTrigger.MANUAL, currentUserService.requireUser());
    }

    @GetMapping("/production-plans/{date}/reconciliation")
    public DailyProductionPlanResponse reconciliation(@PathVariable LocalDate date) {
        return productionPlanning.reconciliation(date);
    }

    @PutMapping("/production-plans/{date}/reconciliation")
    public DailyProductionPlanResponse saveReconciliation(@PathVariable LocalDate date,
                                                          @Valid @RequestBody ReconciliationRequest request) {
        return productionPlanning.saveReconciliation(date, request, currentUserService.requireUser());
    }

    @PostMapping("/production-plans/{date}/close")
    public DailyProductionPlanResponse close(@PathVariable LocalDate date, @Valid @RequestBody ReconciliationRequest request) {
        return productionPlanning.close(date, request, currentUserService.requireUser());
    }

    @PostMapping("/production-plans/{date}/reopen")
    public DailyProductionPlanResponse reopen(@PathVariable LocalDate date, @Valid @RequestBody ReopenPlanRequest request) {
        return productionPlanning.reopen(date, request, currentUserService.requireUser());
    }

    @GetMapping("/planning-targets")
    public List<PlanningTargetResponse> targets() {
        return targets.list();
    }

    @PostMapping("/planning-targets")
    public PlanningTargetResponse createTarget(@Valid @RequestBody PlanningTargetRequest request) {
        var user = currentUserService.requireUser();
        PlanningTargetResponse saved = targets.create(request, user.actorLabel());
        productionPlanning.recalculate(request.effectiveFrom(), GenerationTrigger.AUTOMATIC_RECALCULATION, user);
        return saved;
    }
}
