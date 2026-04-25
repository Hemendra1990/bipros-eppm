package com.bipros.document.application.listener;

import com.bipros.common.event.ProjectCreatedEvent;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultFolderSeeder")
class DefaultFolderSeederTest {

    @Mock private DocumentFolderRepository folderRepository;

    private DefaultFolderSeeder seeder;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        seeder = new DefaultFolderSeeder(folderRepository);
        projectId = UUID.randomUUID();
    }

    @Test
    @DisplayName("creates 7 root folders in canonical order when none exist")
    void onProjectCreated_createsSevenRootFolders_whenNoneExist() {
        when(folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId))
            .thenReturn(Collections.emptyList());

        seeder.onProjectCreated(new ProjectCreatedEvent(projectId, "P-001", "Test Project"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentFolder>> captor = ArgumentCaptor.forClass(List.class);
        verify(folderRepository).saveAll(captor.capture());

        List<DocumentFolder> created = captor.getValue();
        assertThat(created).hasSize(7);
        assertThat(created).extracting(DocumentFolder::getName)
            .containsExactly("Drawings", "Specifications", "Contracts",
                "Approvals", "Correspondence", "As-Built", "General");
        assertThat(created).extracting(DocumentFolder::getCode)
            .containsExactly("DRW", "SPEC", "CON", "APR", "COR", "AB", "GEN");
        assertThat(created).extracting(DocumentFolder::getCategory)
            .containsExactly(DocumentCategory.DRAWING, DocumentCategory.SPECIFICATION,
                DocumentCategory.CONTRACT, DocumentCategory.APPROVAL,
                DocumentCategory.CORRESPONDENCE, DocumentCategory.AS_BUILT,
                DocumentCategory.GENERAL);
        assertThat(created).extracting(DocumentFolder::getSortOrder)
            .containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(created).allMatch(f -> f.getProjectId().equals(projectId));
        assertThat(created).allMatch(f -> f.getParentId() == null);
    }

    @Test
    @DisplayName("is idempotent — does not duplicate when root folders already exist")
    void onProjectCreated_isIdempotent_whenRootFoldersExist() {
        DocumentFolder existing = new DocumentFolder();
        existing.setProjectId(projectId);
        existing.setName("Drawings");
        existing.setCode("DRW");
        existing.setCategory(DocumentCategory.DRAWING);
        existing.setSortOrder(0);
        when(folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId))
            .thenReturn(List.of(existing));

        seeder.onProjectCreated(new ProjectCreatedEvent(projectId, "P-001", "Test Project"));

        verify(folderRepository, never()).saveAll(anyList());
        verify(folderRepository, never()).save(any(DocumentFolder.class));
    }

    @Test
    @DisplayName("does not propagate exceptions from the event listener")
    void onProjectCreated_doesNotPropagate_whenSeederFails() {
        when(folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId))
            .thenThrow(new RuntimeException("simulated DB failure"));

        assertThatCode(() ->
            seeder.onProjectCreated(new ProjectCreatedEvent(projectId, "P-001", "Test Project"))
        ).doesNotThrowAnyException();
    }
}
