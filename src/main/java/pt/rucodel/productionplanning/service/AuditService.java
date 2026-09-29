package pt.rucodel.productionplanning.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.dto.AuditEventResponse;
import pt.rucodel.productionplanning.dto.PageResponse;
import pt.rucodel.productionplanning.entity.PlanningAuditEventEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.PlanningAuditEventRepository;
import pt.rucodel.productionplanning.repository.ProductionPlanRepository;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuditService {
    private final PlanningAuditEventRepository auditEvents;
    private final ProductionPlanRepository plans;
    private final WheelIntakeRequestRepository requests;
    private final ApiMapper mapper;
    private final Clock clock;

    public AuditService(PlanningAuditEventRepository auditEvents,
                        ProductionPlanRepository plans,
                        WheelIntakeRequestRepository requests,
                        ApiMapper mapper,
                        Clock clock) {
        this.auditEvents = auditEvents;
        this.plans = plans;
        this.requests = requests;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID planId, UUID requestId, String eventType, String actor, String detail) {
        PlanningAuditEventEntity event = new PlanningAuditEventEntity();
        event.setPlanId(planId);
        event.setRequestId(requestId);
        event.setEventType(eventType);
        event.setActor(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        event.setDetail(detail);
        event.setCreatedAt(OffsetDateTime.now(clock));
        resolveSite(planId, requestId).ifPresent(event::setProductionSite);
        auditEvents.save(event);
    }

    @Transactional
    public void record(ProductionSiteEntity site, UUID planId, UUID requestId, String eventType, String actor, String detail) {
        PlanningAuditEventEntity event = new PlanningAuditEventEntity();
        event.setProductionSite(site);
        event.setPlanId(planId);
        event.setRequestId(requestId);
        event.setEventType(eventType);
        event.setActor(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        event.setDetail(detail);
        event.setCreatedAt(OffsetDateTime.now(clock));
        auditEvents.save(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(int page, int size) {
        return list(ProductionSiteCode.PT, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(ProductionSiteCode siteCode, int page, int size) {
        return mapper.toPage(
                auditEvents.findByProductionSite_CodeOrderByCreatedAtDesc(siteCode,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                mapper::toAudit
        );
    }

    private java.util.Optional<ProductionSiteEntity> resolveSite(UUID planId, UUID requestId) {
        if (planId != null) {
            java.util.Optional<ProductionSiteEntity> site = plans.findById(planId)
                    .map(plan -> plan.getProductionSite());
            if (site.isPresent()) {
                return site;
            }
        }
        if (requestId != null) {
            return requests.findById(requestId).map(request -> request.getProductionSite());
        }
        return java.util.Optional.empty();
    }
}
