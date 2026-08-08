package com.bipros.api.email;
import java.util.List;
public record EmailMessage(List<String> to, String subject, String html, String attachmentName, byte[] attachment) {}
