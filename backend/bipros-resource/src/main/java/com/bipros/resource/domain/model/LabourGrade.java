package com.bipros.resource.domain.model;

public enum LabourGrade {
    A("Senior Management / Principal Engineer", "OMR 95 – 125/day",
      "15+ years experience, PMP/FIDIC/RE certified, contract authority, direct client interface."),
    B("Mid-Level Engineer / Specialist", "OMR 50 – 70/day",
      "7–12 years experience, professional certification (NEBOSH/RICS/AWS), team supervision role."),
    C("Skilled Tradesperson / Senior Operator", "OMR 26 – 48/day",
      "4–8 years experience, trade-tested Grade II or equipment license, independent work execution."),
    D("Semi-Skilled Worker / Junior Operator", "OMR 16 – 28/day",
      "2–4 years experience, site safety card mandatory, supervised task execution."),
    E("General / Unskilled Labour", "OMR 10 – 14/day",
      "1+ year experience, site induction card, works under direct supervision at all times.");

    private final String classification;
    private final String dailyRateRange;
    private final String description;

    LabourGrade(String classification, String dailyRateRange, String description) {
        this.classification = classification;
        this.dailyRateRange = dailyRateRange;
        this.description = description;
    }

    public String getClassification() { return classification; }
    public String getDailyRateRange() { return dailyRateRange; }
    public String getDescription() { return description; }
}
