package com.bipros.integration.service;

import com.bipros.integration.adapter.PfmsAdapter;
import com.bipros.integration.model.PfmsFundTransfer;
import com.bipros.integration.repository.PfmsFundTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PfmsIntegrationService {

    private final PfmsAdapter pfmsAdapter;
    private final PfmsFundTransferRepository fundTransferRepository;

    public PfmsFundTransfer checkAndLogFundStatus(String sanctionOrderNumber) {
        PfmsAdapter.PfmsFundStatus status = pfmsAdapter.checkFundStatus(sanctionOrderNumber);

        PfmsFundTransfer transfer = fundTransferRepository.findBySanctionOrderNumber(sanctionOrderNumber)
            .orElseGet(() -> new PfmsFundTransfer());

        transfer.setSanctionOrderNumber(sanctionOrderNumber);
        transfer.setPfmsReferenceNumber(status.referenceNumber());
        transfer.setAmount(status.amount());
        transfer.setTransferDate(status.transferDate());
        transfer.setPfmsStatus(status.status());

        return fundTransferRepository.save(transfer);
    }

    public PfmsFundTransfer initiateFundTransfer(
        UUID projectId,
        String sanctionOrderNumber,
        String beneficiary,
        BigDecimal amount,
        String purpose
    ) {
        PfmsAdapter.PfmsPaymentRequest request = new PfmsAdapter.PfmsPaymentRequest(
            sanctionOrderNumber,
            beneficiary,
            amount,
            purpose
        );

        PfmsAdapter.PfmsPaymentResult result = pfmsAdapter.initiatePayment(request);

        PfmsFundTransfer transfer = new PfmsFundTransfer();
        transfer.setProjectId(projectId);
        transfer.setSanctionOrderNumber(sanctionOrderNumber);
        transfer.setBeneficiary(beneficiary);
        transfer.setAmount(amount);
        transfer.setPurpose(purpose);
        transfer.setTransferDate(LocalDate.now());
        transfer.setPfmsReferenceNumber(result.referenceNumber());
        transfer.setStatus(result.success()
            ? PfmsFundTransfer.FundTransferStatus.PROCESSING
            : PfmsFundTransfer.FundTransferStatus.FAILED
        );

        return fundTransferRepository.save(transfer);
    }

    public Page<PfmsFundTransfer> getProjectFundTransfers(UUID projectId, int page, int size) {
        return fundTransferRepository.findByProjectIdOrderByCreatedAtDesc(
            projectId,
            PageRequest.of(page, size)
        );
    }
}
