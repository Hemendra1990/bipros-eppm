package com.bipros.api.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    /** Port note 2026-08-05: the SMTP gate is now the JavaMailSender bean's presence
     *  (ObjectProvider), matching the agent EmailChannel — a null provider = no SMTP host. */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender senderOrNull) {
        ObjectProvider<JavaMailSender> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(senderOrNull);
        return p;
    }

    @Test void preview_when_no_smtp_configured() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService svc = new EmailService(provider(null), "from@x.com", "");
        var r = svc.send(new EmailMessage(List.of("a@b.com"), "s", "<p>h</p>", null, null));
        assertThat(r).isEqualTo(EmailService.SendResult.PREVIEW);
        verifyNoInteractions(sender);
    }

    @Test void preview_when_no_recipients() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService svc = new EmailService(provider(sender), "from@x.com", "");
        var r = svc.send(new EmailMessage(List.of(), "s", "<p>h</p>", null, null));
        assertThat(r).isEqualTo(EmailService.SendResult.PREVIEW);
        verifyNoInteractions(sender);
    }

    @Test void sends_when_smtp_and_recipients() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        EmailService svc = new EmailService(provider(sender), "from@x.com", "");
        var r = svc.send(new EmailMessage(List.of("a@b.com"), "s", "<p>h</p>", null, null));
        assertThat(r).isEqualTo(EmailService.SendResult.SENT);
        verify(sender).send(any(MimeMessage.class));
    }
}
