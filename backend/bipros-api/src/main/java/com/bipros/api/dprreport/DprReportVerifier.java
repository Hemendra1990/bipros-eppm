package com.bipros.api.dprreport;

import com.bipros.ai.insights.dto.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DprReportVerifier {
    public record VerifyResult(boolean clean, List<String> unverifiedNumbers, InsightsResponse sanitized) {}

    private static final Pattern NUM = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    public VerifyResult verify(InsightsResponse n, Set<String> allowed) {
        StringBuilder text = new StringBuilder();
        if (n.summary() != null) text.append(n.summary()).append(' ');
        if (n.findings() != null) n.findings().forEach(f -> text.append(f.label()).append(' ').append(f.detail()).append(' '));
        if (n.recommendations() != null) n.recommendations().forEach(r -> text.append(r.title()).append(' ').append(r.action()).append(' ').append(r.rationale()).append(' '));

        List<String> unverified = new ArrayList<>();
        Matcher mm = NUM.matcher(text);
        while (mm.find()) {
            String tok = mm.group();
            String bare = tok.replace(",", "");
            if (!bare.contains(".")) {
                try {
                    long asLong = Long.parseLong(bare);
                    if (asLong <= 9) continue;                 // ignore small counts
                    if (asLong >= 1900 && asLong <= 2100) continue; // ignore years
                } catch (NumberFormatException ignore) { /* fall through */ }
            }
            if (!allowed.contains(tok) && !allowed.contains(bare)) unverified.add(tok);
        }
        if (unverified.isEmpty()) return new VerifyResult(true, List.of(), n);

        String caveat = (n.rationale() == null ? "" : n.rationale() + " ")
            + "[Note: some figures could not be verified against source data (unverified: "
            + String.join(", ", unverified) + ") and were flagged.]";
        InsightsResponse sanitized = new InsightsResponse(n.summary(), n.highlights(), n.variances(),
            n.recommendations(), n.findings(), caveat, n.mdx(), n.charts());
        return new VerifyResult(false, unverified, sanitized);
    }
}
