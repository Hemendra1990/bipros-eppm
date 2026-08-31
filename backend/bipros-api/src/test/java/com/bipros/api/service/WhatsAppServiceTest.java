package com.bipros.api.service;

import com.bipros.api.config.WhatsAppProperties;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.security.infrastructure.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppService")
class WhatsAppServiceTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    ProjectRepository projectRepository;

    WhatsAppProperties properties;

    WhatsAppService service;

    private static final String VALID_PROJECT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setFrontendUrl("http://localhost:3000");
        service = new WhatsAppService(jwtTokenProvider, projectRepository, properties);
    }

    @Nested
    @DisplayName("redirectToGis")
    class RedirectToGis {

        @Test
        @DisplayName("redirects to deeplink page with auth and projectId query params")
        void redirectsWithAuthParamOnValidTokenAndProject() {
            when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
            when(projectRepository.existsById(UUID.fromString(VALID_PROJECT_ID))).thenReturn(true);

            ResponseEntity<Void> response = service.redirectToGis(VALID_PROJECT_ID, VALID_TOKEN);

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).isEqualTo(
                "http://localhost:3000/auth/deeplink?auth=" + VALID_TOKEN + "&projectId=" + VALID_PROJECT_ID);
        }

        @Test
        @DisplayName("redirects to deeplink page when project does not exist")
        void redirectsWithAuthParamWhenProjectNotFound() {
            when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
            when(projectRepository.existsById(UUID.fromString(VALID_PROJECT_ID))).thenReturn(false);

            ResponseEntity<Void> response = service.redirectToGis(VALID_PROJECT_ID, VALID_TOKEN);

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).startsWith("http://localhost:3000/auth/deeplink")
                .contains("auth=" + VALID_TOKEN);
        }

        @Test
        @DisplayName("redirects to login with error when token is invalid")
        void redirectsWithErrorOnInvalidToken() {
            when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(false);

            ResponseEntity<Void> response = service.redirectToGis(VALID_PROJECT_ID, VALID_TOKEN);

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).isEqualTo("http://localhost:3000/auth/login?error=invalid_token");
        }

        @Test
        @DisplayName("redirects to login with error on blank token")
        void redirectsWithErrorOnBlankToken() {
            ResponseEntity<Void> response = service.redirectToGis(VALID_PROJECT_ID, "   ");

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).isEqualTo("http://localhost:3000/auth/login?error=missing_token");
        }

        @Test
        @DisplayName("redirects to login with error on null token")
        void redirectsWithErrorOnNullToken() {
            ResponseEntity<Void> response = service.redirectToGis(VALID_PROJECT_ID, null);

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).isEqualTo("http://localhost:3000/auth/login?error=missing_token");
        }

        @Test
        @DisplayName("redirects to login with error on invalid project UUID")
        void redirectsWithErrorOnInvalidProjectId() {
            ResponseEntity<Void> response = service.redirectToGis("not-a-uuid", VALID_TOKEN);

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).isEqualTo("http://localhost:3000/auth/login?error=invalid_project_id");
        }
    }
}
