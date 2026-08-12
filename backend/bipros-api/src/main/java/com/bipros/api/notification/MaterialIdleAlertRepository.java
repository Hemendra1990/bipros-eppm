package com.bipros.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialIdleAlertRepository extends JpaRepository<MaterialIdleAlert, UUID> {

    List<MaterialIdleAlert> findByProjectIdAndResolvedAtIsNull(UUID projectId);

    Optional<MaterialIdleAlert> findByProjectIdAndCustodianUserIdAndMaterialKeyAndBucketKey(
        UUID projectId, UUID custodianUserId, String materialKey, String bucketKey);
}
