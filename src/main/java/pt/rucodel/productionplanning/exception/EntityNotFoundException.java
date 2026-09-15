package pt.rucodel.productionplanning.exception;

public class EntityNotFoundException extends ProductionPlanningException {
    public EntityNotFoundException(String message) {
        super("ENTITY_NOT_FOUND", message);
    }
}
