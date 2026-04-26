package com.bipros.common.web.json;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Promotes the active Jackson {@code @JsonView} on every JSON response based on the current
 * user's roles. Without this advice, controllers would have to pick a view manually.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Determine the highest {@link Views} class the caller's roles entitle them to (see role
 *       table in {@link Views}).</li>
 *   <li>If the controller method (or class) already declares a more permissive {@code @JsonView},
 *       leave it alone — the explicit annotation wins.</li>
 *   <li>Otherwise wrap the response body in a {@link MappingJacksonValue} with the resolved view
 *       set.</li>
 * </ol>
 *
 * <p>Note: {@code @JsonView} only filters response serialisation. Write endpoints must still
 * validate that the caller is allowed to set sensitive fields — the advice cannot enforce that.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class RoleAwareViewAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        // Don't wrap non-JSON responses (file streams, plain strings, etc.).
        if (selectedContentType != null && !selectedContentType.includes(MediaType.APPLICATION_JSON)) {
            return body;
        }

        Class<?> resolvedView = pickViewForCurrentUser();

        // Respect explicit @JsonView on the controller method or its declaring class — the
        // explicit annotation is treated as authoritative when it picks a wider view than the
        // role-derived one. A narrower explicit view is also honoured (controller knows best).
        JsonView explicit = returnType.getMethodAnnotation(JsonView.class);
        if (explicit == null && returnType.getDeclaringClass() != null) {
            explicit = returnType.getDeclaringClass().getAnnotation(JsonView.class);
        }
        if (explicit != null && explicit.value().length > 0) {
            resolvedView = explicit.value()[0];
        }

        if (body instanceof MappingJacksonValue existing) {
            if (existing.getSerializationView() == null) {
                existing.setSerializationView(resolvedView);
            }
            return existing;
        }
        MappingJacksonValue wrapped = new MappingJacksonValue(body);
        wrapped.setSerializationView(resolvedView);
        return wrapped;
    }

    private Class<?> pickViewForCurrentUser() {
        Set<String> roles = currentRoles();
        if (roles.contains("ROLE_ADMIN")) {
            return Views.Admin.class;
        }
        if (roles.contains("ROLE_FINANCE")) {
            return Views.FinanceConfidential.class;
        }
        if (roles.contains("ROLE_PMO")
                || roles.contains("ROLE_EXECUTIVE")
                || roles.contains("ROLE_PROJECT_MANAGER")) {
            return Views.Internal.class;
        }
        return Views.Public.class;
    }

    private static Set<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
