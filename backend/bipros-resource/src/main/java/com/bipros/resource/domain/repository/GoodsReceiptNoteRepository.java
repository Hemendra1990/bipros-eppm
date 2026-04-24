package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.GoodsReceiptNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoodsReceiptNoteRepository extends JpaRepository<GoodsReceiptNote, UUID> {

    List<GoodsReceiptNote> findByMaterialIdOrderByReceivedDateDesc(UUID materialId);
    List<GoodsReceiptNote> findByProjectIdOrderByReceivedDateDesc(UUID projectId);
    Optional<GoodsReceiptNote> findByGrnNumber(String grnNumber);
    List<GoodsReceiptNote> findByProjectIdAndReceivedDateBetween(UUID projectId, LocalDate from, LocalDate to);

    /** Greatest numeric suffix for a given GRN year-month prefix (e.g. "GRN-202604-"). */
    @Query("select max(cast(substring(g.grnNumber, ?2) as integer)) "
        + "from GoodsReceiptNote g where g.grnNumber like ?1")
    Integer findMaxSuffixForPrefix(String likePattern, int suffixStart);
}
