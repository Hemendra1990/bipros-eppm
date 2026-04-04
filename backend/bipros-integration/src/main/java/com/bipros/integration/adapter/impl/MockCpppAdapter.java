package com.bipros.integration.adapter.impl;

import com.bipros.integration.adapter.CpppAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!production")
public class MockCpppAdapter implements CpppAdapter {

    @Override
    public CpppPublishResult publishTender(CpppTenderRequest request) {
        String cpppTenderNumber = "CPPP-" + UUID.randomUUID();
        return new CpppPublishResult(
            true,
            cpppTenderNumber,
            "https://cppp.gov.in/tender/" + cpppTenderNumber,
            "Tender published successfully on CPPP portal"
        );
    }

    @Override
    public List<CpppBidSummary> getBidSummary(String cpppTenderNumber) {
        return List.of(
            new CpppBidSummary(
                "Vendor A Solutions",
                "₹2,50,000",
                LocalDate.now().minusDays(2),
                "SUBMITTED"
            ),
            new CpppBidSummary(
                "Construction & Co",
                "₹2,75,000",
                LocalDate.now().minusDays(1),
                "SUBMITTED"
            ),
            new CpppBidSummary(
                "Global Builders Inc",
                "₹2,40,000",
                LocalDate.now(),
                "SUBMITTED"
            )
        );
    }
}
