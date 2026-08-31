package com.bipros.ai.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentNotificationDeliveryRepository extends JpaRepository<AgentNotificationDelivery, UUID> {

    boolean existsByFindingIdAndChannelKeyAndRecipientUserId(UUID findingId, String channelKey, UUID recipientUserId);

    List<AgentNotificationDelivery> findByFindingId(UUID findingId);
}
