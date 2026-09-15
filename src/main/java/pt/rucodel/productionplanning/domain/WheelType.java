package pt.rucodel.productionplanning.domain;

public enum WheelType {
    BIPARTITE("Bipartidas"),
    WASHED("Lavadas"),
    NORMAL("Normais");

    private final String label;

    WheelType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
