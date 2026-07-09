package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentNotificationDelivery;
import com.bipros.ai.agent.domain.AgentNotificationDeliveryRepository;
import com.bipros.ai.agent.domain.AgentNotificationRuleRepository;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.common.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRouterTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID FINDING = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Mock private AgentNotificationRuleRepository ruleRepository;
    @Mock private AgentNotificationDeliveryRepository deliveryRepository;
    @Mock private StakeholderResolver stakeholderResolver;
    @Mock private AgentMemoryService memoryService;
    @Mock private NotificationService notificationService;

    @Mock private NotificationChannel inApp;
    @Mock private NotificationChannel email;
    @Mock private NotificationChannel whatsapp;

    private AgentNotifyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AgentNotifyProperties();
        properties.getRouting().put("high", routing("in_app,email", true));
        properties.getRouting().put("low", routing("in_app", false));
    }

    private static AgentNotifyProperties.Routing routing(String channels, boolean immediate) {
        AgentNotifyProperties.Routing r = new AgentNotifyProperties.Routing();
        r.setChannels(channels);
        r.setImmediate(immediate);
        return r;
    }

    private NotificationRouter router() {
        return new NotificationRouter(ruleRepository, deliveryRepository, properties,
                stakeholderResolver, memoryService, notificationService,
                List.of(inApp, email, whatsapp));
    }

    private static AgentFinding finding(Severity severity) {
        AgentFinding f = new AgentFinding();
        f.setId(FINDING);
        f.setProjectId(PROJECT);
        f.setSeverity(severity);
        f.setFindingType("CRITICAL_PATH_SLIP");
        f.setTitle("Critical path slips 12 days");
        f.setWhatHappened("The scheduled finish is 12 days late.");
        return f;
    }

    private void stubChannelKeys() {
        when(inApp.key()).thenReturn("in_app");
        when(email.key()).thenReturn("email");
        when(whatsapp.key()).thenReturn("whatsapp");
    }

    private void stubYmlFallback(Severity severity) {
        when(ruleRepository.findByProjectIdAndSeverity(PROJECT, severity)).thenReturn(Optional.empty());
        when(ruleRepository.findByProjectIdIsNullAndSeverity(severity)).thenReturn(Optional.empty());
    }

    private void stubOneRecipient() {
        when(stakeholderResolver.resolve(any())).thenReturn(
                List.of(new StakeholderResolver.Recipient(USER, "Alice", "alice@example.com", "+15550001")));
        lenient().when(memoryService.readEvidence(any())).thenReturn(List.of());
        lenient().when(memoryService.readStakeholders(any())).thenReturn(Map.of());
    }

    @Test
    void routesHighToInAppAndEmailOnly_recordingDeliveries() {
        stubYmlFallback(Severity.HIGH);
        stubOneRecipient();
        stubChannelKeys();
        when(inApp.isEnabled()).thenReturn(true);
        when(email.isEnabled()).thenReturn(true);
        lenient().when(whatsapp.isEnabled()).thenReturn(true);
        when(deliveryRepository.existsByFindingIdAndChannelKeyAndRecipientUserId(eq(FINDING), anyString(), eq(USER)))
                .thenReturn(false);
        when(notificationService.existsSince(eq(FINDING), anyString(), eq(USER), any(Instant.class)))
                .thenReturn(false);

        router().route(finding(Severity.HIGH));

        verify(inApp, times(1)).send(any(ResolvedNotification.class));
        verify(email, times(1)).send(any(ResolvedNotification.class));
        verify(whatsapp, never()).send(any(ResolvedNotification.class));   // HIGH omits whatsapp
        verify(deliveryRepository, times(2)).save(any(AgentNotificationDelivery.class));
    }

    @Test
    void suppressesChannelAlreadyDeliveredButStillSendsTheOther() {
        stubYmlFallback(Severity.HIGH);
        stubOneRecipient();
        stubChannelKeys();
        when(inApp.isEnabled()).thenReturn(true);
        when(email.isEnabled()).thenReturn(true);
        // in_app already delivered to this recipient; email not yet.
        when(deliveryRepository.existsByFindingIdAndChannelKeyAndRecipientUserId(FINDING, "in_app", USER))
                .thenReturn(true);
        when(deliveryRepository.existsByFindingIdAndChannelKeyAndRecipientUserId(FINDING, "email", USER))
                .thenReturn(false);
        when(notificationService.existsSince(eq(FINDING), anyString(), eq(USER), any(Instant.class)))
                .thenReturn(false);

        router().route(finding(Severity.HIGH));

        verify(inApp, never()).send(any(ResolvedNotification.class));
        verify(email, times(1)).send(any(ResolvedNotification.class));
        verify(deliveryRepository, times(1)).save(any(AgentNotificationDelivery.class));
    }

    @Test
    void inAppDedupWindowSuppressesAllChannels() {
        stubYmlFallback(Severity.HIGH);
        stubOneRecipient();
        stubChannelKeys();
        when(inApp.isEnabled()).thenReturn(true);
        when(email.isEnabled()).thenReturn(true);
        when(deliveryRepository.existsByFindingIdAndChannelKeyAndRecipientUserId(eq(FINDING), anyString(), eq(USER)))
                .thenReturn(false);
        // A recent in-app notification exists -> 24h window suppresses every (channel, recipient).
        when(notificationService.existsSince(eq(FINDING), anyString(), eq(USER), any(Instant.class)))
                .thenReturn(true);

        router().route(finding(Severity.HIGH));

        verify(inApp, never()).send(any(ResolvedNotification.class));
        verify(email, never()).send(any(ResolvedNotification.class));
        verify(deliveryRepository, never()).save(any(AgentNotificationDelivery.class));
    }

    @Test
    void deferredSeverityIsNotSentImmediately() {
        stubYmlFallback(Severity.LOW);   // routing "low" is immediate=false

        router().route(finding(Severity.LOW));

        verify(inApp, never()).send(any(ResolvedNotification.class));
        verify(email, never()).send(any(ResolvedNotification.class));
        verify(whatsapp, never()).send(any(ResolvedNotification.class));
        verify(deliveryRepository, never()).save(any(AgentNotificationDelivery.class));
        verify(stakeholderResolver, never()).resolve(any());
    }
}
