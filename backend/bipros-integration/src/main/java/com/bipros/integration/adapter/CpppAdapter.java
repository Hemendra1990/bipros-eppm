package com.bipros.integration.adapter;

import java.time.LocalDate;
import java.util.List;

public interface CpppAdapter {

    /**
     * Publish a tender to CPPP portal
     */
    CpppPublishResult publishTender(CpppTenderRequest request);

    /**
     * Get bid summary for a published tender
     */
    List<CpppBidSummary> getBidSummary(String cpppTenderNumber);

    record CpppTenderRequest(
        String nitReferenceNumber,
        String tenderDescription,
        LocalDate bidSubmissionDeadline,
        LocalDate technicalBidOpeningDate
    ) {}

    record CpppPublishResult(
        boolean success,
        String cpppTenderNumber,
        String cpppUrl,
        String message
    ) {}

    record CpppBidSummary(
        String vendorName,
        String bidAmount,
        LocalDate bidDate,
        String status
    ) {}
}
