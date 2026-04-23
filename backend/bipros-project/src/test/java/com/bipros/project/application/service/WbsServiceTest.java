package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateWbsNodeRequest;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectActivityCounter;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WbsService")
class WbsServiceTest {

    @Mock private WbsNodeRepository wbsNodeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectActivityCounter projectActivityCounter;
    @Mock private AuditService auditService;

    private WbsService service;

    @BeforeEach
    void setUp() {
        service = new WbsService(wbsNodeRepository, projectRepository, projectActivityCounter, auditService);
    }

    @Nested
    @DisplayName("Create node — duplicate-code protection")
    class DuplicateCode {

        @Test
        @DisplayName("throws BusinessRuleException(WBS_CODE_DUPLICATE) when code already exists")
        void duplicateCodeThrows() {
            UUID projectId = UUID.randomUUID();
            String code = "WO10-01";
            CreateWbsNodeRequest req = new CreateWbsNodeRequest(code, "Site Clearing", null, projectId, null);

            when(projectRepository.existsById(projectId)).thenReturn(true);
            when(wbsNodeRepository.existsByCode(code)).thenReturn(true);

            assertThatThrownBy(() -> service.createNode(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException br = (BusinessRuleException) ex;
                        assertThat(br.getRuleCode()).isEqualTo("WBS_CODE_DUPLICATE");
                        assertThat(br.getMessage()).contains(code);
                    });

            verify(wbsNodeRepository, never()).save(any(WbsNode.class));
        }
    }
}
