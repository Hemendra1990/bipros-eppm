package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.StretchActivityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StretchActivityLinkRepository
    extends JpaRepository<StretchActivityLink, UUID> {

    List<StretchActivityLink> findByStretchId(UUID stretchId);

    List<StretchActivityLink> findByBoqItemId(UUID boqItemId);

    void deleteByStretchId(UUID stretchId);
}
