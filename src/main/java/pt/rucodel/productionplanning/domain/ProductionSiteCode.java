package pt.rucodel.productionplanning.domain;

public enum ProductionSiteCode {
    PT("Portugal", "Europe/Lisbon"),
    LUX("Luxemburgo", "Europe/Luxembourg");

    private final String displayName;
    private final String timezone;

    ProductionSiteCode(String displayName, String timezone) {
        this.displayName = displayName;
        this.timezone = timezone;
    }

    public String displayName() {
        return displayName;
    }

    public String timezone() {
        return timezone;
    }

    public static ProductionSiteCode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ProductionSiteCode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
