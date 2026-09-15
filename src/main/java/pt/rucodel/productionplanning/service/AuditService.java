package pt.rucodel.productionplanning.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.dto.AuditEventResponse;
import pt.rucodel.productionplanning.dto.PageResponse;
import pt.rucodel.productionplanning.entity.PlanningAuditEventEntity;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.PlanningAuditEventRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuditService {
    private final PlanningAuditEventRepository auditEvents;
    private final ApiMapper mapper;
    private final Clock clock;

    public AuditService(PlanningAuditEventRepository auditEvents, ApiMapper mapper, Clock clock) {
        this.auditEvents = auditEvents;
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
        auditEvents.save(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(int page, int size) {
        return mapper.toPage(
                auditEvents.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                mapper::toAudit
        );
    }
}
