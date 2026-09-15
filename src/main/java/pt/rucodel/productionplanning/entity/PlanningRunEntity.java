package pt.rucodel.productionplanning.entity;

import jakarta.persistence.*;
import pt.rucodel.productionplanning.domain.GenerationTrigger;
import pt.rucodel.productionplanning.domain.PlanningRunStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "planning_run")
public class PlanningRunEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 40)
    private GenerationTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PlanningRunStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "affected_date_from", nullable = false)
    private LocalDate affectedDateFrom;

    @Column(name = "affected_date_to", nullable = false)
    private LocalDate affectedDateTo;

    @Column(name = "actor", nullable = false, length = 160)
    private String actor;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    public UUID getId() {
        return id;
    }

    public void setTrigger(GenerationTrigger trigger) {
        this.trigger = trigger;
    }

    public void setStatus(PlanningRunStatus status) {
        this.status = status;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setAffectedDateFrom(LocalDate affectedDateFrom) {
        this.affectedDateFrom = affectedDateFrom;
    }

    public void setAffectedDateTo(LocalDate affectedDateTo) {
        this.affectedDateTo = affectedDateTo;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
