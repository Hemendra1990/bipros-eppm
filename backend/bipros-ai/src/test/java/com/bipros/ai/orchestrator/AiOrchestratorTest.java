package com.bipros.ai.orchestrator;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.persona.RolePersonaProvider;
import com.bipros.ai.tool.DataGraphCatalog;
import com.bipros.ai.tool.ToolRegistry;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@link AiOrchestrator#buildSystemPrompt} branching:
 *
 * <ul>
 *   <li><b>Portfolio mode</b> — {@code ctx.projectId() == null} and a non-empty
 *       {@code scopedProjectIds()}: prompt must declare PORTFOLIO MODE and
 *       enumerate the accessible-project roster (code — name).</li>
 *   <li><b>Project-scoped</b> — {@code ctx.projectId() != null}: prompt
 *       keeps the original strict per-project copy and does NOT mention
 *       portfolio mode.</li>
 * </ul>
 */
class AiOrchestratorTest {

    private ToolRegistry toolRegistry;
    private DataGraphCatalog catalog;
    private RolePersonaProvider personaProvider;
    private ProjectRepository projectRepository;
    private AiOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        toolRegistry = Mockito.mock(ToolRegistry.class);
        catalog = Mockito.mock(DataGraphCatalog.class);
        personaProvider = Mockito.mock(RolePersonaProvider.class);
        projectRepository = Mockito.mock(ProjectRepository.class);

        when(catalog.compact()).thenReturn("(graph)");
        when(personaProvider.forProfile(any())).thenReturn(null);

        orchestrator = new AiOrchestrator(toolRegistry, catalog, personaProvider,
                projectRepository, 12, 10);
    }

    private static String invokeBuildSystemPrompt(AiOrchestrator orch, AiContext ctx) throws Exception {
        Method m = AiOrchestrator.class.getDeclaredMethod("buildSystemPrompt", AiContext.class);
        m.setAccessible(true);
        return (String) m.invoke(orch, ctx);
    }

    private static Project project(UUID id, String code, String name) {
        Project p = new Project();
        p.setId(id);
        p.setCode(code);
        p.setName(name);
        return p;
    }

    @Test
    void portfolioModeSystemPromptContainsAccessibleProjectRoster() throws Exception {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        AiContext ctx = new AiContext(UUID.randomUUID(), null, "general", "USER", "PM",
                List.of(p1, p2));

        when(projectRepository.findAllById(anyIterable())).thenReturn(List.of(
                project(p1, "ROAD-001", "Highway expansion"),
                project(p2, "6155", "Dualization of Barka Nakhal Road")
        ));

        String prompt = invokeBuildSystemPrompt(orchestrator, ctx);

        assertTrue(prompt.contains("PORTFOLIO MODE"),
                "Portfolio session prompt must declare PORTFOLIO MODE");
        assertTrue(prompt.contains("ROAD-001"),
                "Portfolio prompt must inline project code ROAD-001");
        assertTrue(prompt.contains("6155"),
                "Portfolio prompt must inline project code 6155");
        assertTrue(prompt.contains("Highway expansion"),
                "Portfolio prompt must inline project name");
        // The single-project lockdown copy must NOT leak into portfolio mode.
        assertFalse(prompt.contains("non-negotiable"),
                "Strict per-project copy leaked into portfolio mode prompt");
    }

    @Test
    void projectScopedSystemPromptIsUnchanged() throws Exception {
        UUID p1 = UUID.randomUUID();
        AiContext ctx = new AiContext(UUID.randomUUID(), p1, "cost", "USER", "PM",
                List.of(p1));

        when(projectRepository.findById(p1)).thenReturn(Optional.of(
                project(p1, "ROAD-001", "Highway expansion")));

        String prompt = invokeBuildSystemPrompt(orchestrator, ctx);

        // Strict project-scope copy is preserved verbatim.
        assertTrue(prompt.contains("PROJECT SCOPE (read this carefully — it is non-negotiable)"),
                "Project-scoped prompt must keep the strict non-negotiable header");
        assertTrue(prompt.contains("ONLY project you may"),
                "Project-scoped prompt must keep the single-project lockdown copy");
        assertFalse(prompt.contains("PORTFOLIO MODE"),
                "Project-scoped prompt must NOT mention portfolio mode");
        // The Current-project line still names the in-scope project.
        assertTrue(prompt.contains("ROAD-001"),
                "Project-scoped prompt must reference the current project code");
    }

    @Test
    void adminUnpinnedSessionEntersAdminPortfolioModeAndDoesNotAskUserToSwitch() throws Exception {
        // Admin with no pinned project + empty scopedProjectIds (admins are
        // unrestricted, not enumerated). They must land in ADMIN PORTFOLIO
        // MODE, not the strict non-negotiable single-project block.
        AiContext ctx = new AiContext(UUID.randomUUID(), null, "general", "ADMIN", "ADMIN",
                java.util.Collections.emptyList());

        String prompt = invokeBuildSystemPrompt(orchestrator, ctx);

        assertTrue(prompt.contains("ADMIN PORTFOLIO MODE"),
                "Admin unpinned prompt must declare ADMIN PORTFOLIO MODE");
        assertTrue(prompt.contains("silently adopt"),
                "Admin portfolio prompt must instruct silent entity adoption");
        assertFalse(prompt.contains("non-negotiable"),
                "Admin must NOT see the strict single-project lockdown copy");
        assertFalse(prompt.contains("please open"),
                "Admin must NOT be told to ask the user to open another project page");
    }

    @Test
    void systemPromptRoutesRosterQuestionsToListSupervisors() throws Exception {
        UUID pid = UUID.randomUUID();
        AiContext ctx = new AiContext(UUID.randomUUID(), pid, "general", "ADMIN", "ADMIN",
                java.util.Collections.emptyList());
        when(projectRepository.findById(pid)).thenReturn(Optional.of(
                project(pid, "ROAD-001", "Road Construction")));

        String prompt = invokeBuildSystemPrompt(orchestrator, ctx);

        assertTrue(prompt.contains("list_supervisors"),
                "system prompt must mention list_supervisors for roster questions");
        assertTrue(prompt.contains("how many supervisors"),
                "system prompt must include the discovery cue for roster questions");
    }

    @Test
    void portfolioModeWithOver50ProjectsEmitsCountFallback() throws Exception {
        // 51 scoped projects → fall back to a count-only message rather than
        // blowing up the token budget with a 51-line roster.
        java.util.List<UUID> manyIds = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) manyIds.add(UUID.randomUUID());
        AiContext ctx = new AiContext(UUID.randomUUID(), null, "general", "USER", "PM", manyIds);

        String prompt = invokeBuildSystemPrompt(orchestrator, ctx);

        assertTrue(prompt.contains("PORTFOLIO MODE"));
        assertTrue(prompt.contains("51 total"),
                "Large portfolios must fall back to a count-only message");
        assertTrue(prompt.contains("call list_projects to enumerate"));
    }
}
