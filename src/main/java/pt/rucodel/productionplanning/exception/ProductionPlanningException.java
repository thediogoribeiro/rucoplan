package pt.rucodel.productionplanning.exception;

public class ProductionPlanningException extends RuntimeException {
    private final String errorCode;

    public ProductionPlanningException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProductionPlanningException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
