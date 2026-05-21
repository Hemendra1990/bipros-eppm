package com.bipros.hds.application.library;

import com.bipros.hds.domain.HdsVersion;
import com.bipros.hds.domain.enums.HdsVersionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Invalidates cached query answers when a version transitions to INDEXED or FAILED.
 *
 * <p>Wired manually from the ingestion orchestrator after a terminal status transition,
 * so it doesn't need to be a JPA entity listener (which can't be Spring-managed cleanly).
 *
 * <p>Track A (the ingestion orchestrator) should call
 * {@code versionStatusListener.onIndexedOrFailed(version)} after marking INDEXED/FAILED.
 */
@Component
@RequiredArgsConstructor
public class VersionStatusListener {

    // TODO(hds-cache-invalidate): QueryCache not yet committed by Track B. Once it lands,
    //   replace this no-op with `private final QueryCache cache;` and uncomment the call below.

    public void onIndexedOrFailed(HdsVersion v) {
        if (v.getStatus() == HdsVersionStatus.INDEXED || v.getStatus() == HdsVersionStatus.FAILED) {
            // TODO(hds-cache-invalidate): QueryCache not yet committed by Track B
            // cache.invalidateForVersion(v.getId());
        }
    }
}
