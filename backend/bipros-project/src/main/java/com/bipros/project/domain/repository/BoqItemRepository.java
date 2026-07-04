package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.BoqItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoqItemRepository extends JpaRepository<BoqItem, UUID> {

  List<BoqItem> findByProjectId(UUID projectId);

  List<BoqItem> findByProjectIdOrderByItemNoAsc(UUID projectId);

  Optional<BoqItem> findByProjectIdAndItemNo(UUID projectId, String itemNo);

  boolean existsByProjectIdAndItemNo(UUID projectId, String itemNo);

  List<BoqItem> findByWbsNodeIdIn(Collection<UUID> wbsNodeIds);

  @Modifying
  @Query("update BoqItem b set b.wbsNodeId = null where b.wbsNodeId in :ids")
  int nullWbsNodeByWbsNodeIdIn(@Param("ids") Collection<UUID> ids);

  /** Σ budgeted amount (boq_qty × budgeted_rate) across the project's BOQ — the EVM cost baseline. */
  @Query("SELECT COALESCE(SUM(b.budgetedAmount), 0) FROM BoqItem b WHERE b.projectId = :projectId")
  BigDecimal sumBudgetedAmount(@Param("projectId") UUID projectId);

  /** Σ capped earned value (min(qty_executed, boq_qty) × budgeted_rate) — the numerator of
   *  Cost % Complete. Capped so EV can never exceed BAC when a line is over-executed. */
  @Query("SELECT COALESCE(SUM((CASE WHEN b.qtyExecutedToDate < b.boqQty THEN b.qtyExecutedToDate ELSE b.boqQty END) * b.budgetedRate), 0) FROM BoqItem b " +
         "WHERE b.projectId = :projectId AND b.qtyExecutedToDate IS NOT NULL AND b.budgetedRate IS NOT NULL AND b.boqQty IS NOT NULL")
  BigDecimal sumEarnedBudgetedValue(@Param("projectId") UUID projectId);

  /** Targeted column-only bulk relabel of BOQ item units. Returns rows changed. */
  @Modifying
  @Query("update BoqItem b set b.unit = :unit where b.id in :ids and (b.unit is null or b.unit <> :unit)")
  int bulkSetUnit(@Param("ids") List<UUID> ids, @Param("unit") String unit);
}
