package com.bipros.resource.domain.model;

public enum LabourCategory {
    SITE_MANAGEMENT      ("SM", "Site Management"),
    PLANT_EQUIPMENT      ("PO", "Plant & Equipment Operators"),
    SKILLED_LABOUR       ("SL", "Skilled Labour"),
    SEMI_SKILLED_LABOUR  ("SS", "Semi-Skilled Labour"),
    GENERAL_UNSKILLED    ("GL", "General / Unskilled Labour");

    private final String codePrefix;
    private final String displayName;

    LabourCategory(String codePrefix, String displayName) {
        this.codePrefix = codePrefix;
        this.displayName = displayName;
    }

    public String getCodePrefix() { return codePrefix; }
    public String getDisplayName() { return displayName; }

    public static LabourCategory fromCodePrefix(String prefix) {
        for (LabourCategory c : values()) {
            if (c.codePrefix.equals(prefix)) return c;
        }
        throw new IllegalArgumentException("Unknown labour category prefix: " + prefix);
    }
}
