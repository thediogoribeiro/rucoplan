package pt.rucodel.productionplanning.exception;

public class InvalidRequestException extends ProductionPlanningException {
    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message);
    }

    public InvalidRequestException(String errorCode, String message) {
        super(errorCode, message);
    }
}
