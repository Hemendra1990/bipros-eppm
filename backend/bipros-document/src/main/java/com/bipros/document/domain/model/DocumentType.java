package com.bipros.document.domain.model;

/**
 * IC-PMS M6 document type taxonomy. Each type has a canonical document-number prefix
 * that is enforced when a Document is created.
 */
public enum DocumentType {
    DRAWING("DRW-"),
    SPECIFICATION("SPEC-"),
    RFI("RFI-"),
    MINUTES("MIN-"),
    CONTRACT_DOCUMENT("LOA-"),
    REPORT("RPT-"),
    BANK_GUARANTEE("BRD-"),
    LOA("LOA-");

    private final String codePrefix;

    DocumentType(String codePrefix) {
        this.codePrefix = codePrefix;
    }

    public String getCodePrefix() {
        return codePrefix;
    }
}
