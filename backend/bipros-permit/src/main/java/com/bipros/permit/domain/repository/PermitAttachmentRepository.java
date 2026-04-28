package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermitAttachmentRepository extends JpaRepository<PermitAttachment, UUID> {
    List<PermitAttachment> findByPermitId(UUID permitId);
}
