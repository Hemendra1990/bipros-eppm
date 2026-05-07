package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprManpower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DprManpowerRepository extends JpaRepository<DprManpower, UUID> {

    List<DprManpower> findByDprIdOrderByTradeAsc(UUID dprId);

    /** Batch fetch for the list endpoint — avoids an N+1 across DPR rows. */
    List<DprManpower> findByDprIdIn(Collection<UUID> dprIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByDprId(UUID dprId);
}
