package com.bipros.api.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The permanent consistency guarantee of the access-control round (2026-08-11): every REST
 * endpoint in every module must carry a {@code @PreAuthorize} declaration (on the method or
 * its class). Before this round, five controllers (all 16 DBS GETs, BOQ reads, issue reads,
 * the whole Team controller, the material consumption report) were reachable by any logged-in
 * user; this test makes that class of bug a BUILD FAILURE instead of a security audit finding.
 *
 * <p>Whitelist = endpoints that are public BY DESIGN, each with the reason. Adding to it is a
 * reviewed decision, not a convenience.
 */
@DisplayName("Architecture — every endpoint declares @PreAuthorize")
class EndpointGuardArchitectureTest {

    /** Public-by-design controllers (simple class names) — reason documented per entry. */
    private static final Set<String> WHITELIST = Set.of(
            // login / register / refresh are permitAll; /me is isAuthenticated at class level
            "AuthController",
            // public QR permit verification (SecurityConfig permits GET /v1/permits/verify/**)
            "PermitVerifyController",
            // inbound provider webhook — callers are external systems, verified by token payload
            "WhatsAppController",
            // /v1/public/branding — white-label config for the login page, permitAll by design
            // (SecurityConfig /v1/public/**); documented in the controller javadoc
            "BrandingController"
    );

    @Test
    void everyEndpointDeclaresPreAuthorize() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> offenders = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.bipros")) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            if (WHITELIST.contains(controller.getSimpleName())) continue;
            boolean classGuarded = AnnotatedElementUtils
                    .hasAnnotation(controller, PreAuthorize.class);
            for (Method m : controller.getDeclaredMethods()) {
                boolean isEndpoint = AnnotatedElementUtils
                        .hasAnnotation(m, RequestMapping.class);   // meta-matches Get/Post/…Mapping
                if (!isEndpoint) continue;
                boolean methodGuarded = AnnotatedElementUtils
                        .hasAnnotation(m, PreAuthorize.class);
                if (!classGuarded && !methodGuarded) {
                    offenders.add(controller.getSimpleName() + "." + m.getName());
                }
            }
        }

        assertThat(offenders)
                .withFailMessage("Endpoints without @PreAuthorize (guard them or add the "
                        + "controller to the documented whitelist):%n%s", String.join("\n", offenders))
                .isEmpty();
    }
}
