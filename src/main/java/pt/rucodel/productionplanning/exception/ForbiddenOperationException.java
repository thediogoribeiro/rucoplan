package pt.rucodel.productionplanning.exception;

public class ForbiddenOperationException extends ProductionPlanningException {
    public ForbiddenOperationException(String message) {
        super("FORBIDDEN_OPERATION", message);
    }
}
