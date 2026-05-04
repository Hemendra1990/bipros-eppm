package com.bipros.ai.wbs.context;

import com.bipros.project.domain.model.WbsNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingWbsContextBuilderTest {

    @Test
    void emptyInputReturnsEmptyString() {
        assertThat(ExistingWbsContextBuilder.format(List.of(), 3, 200)).isEqualTo("");
        assertThat(ExistingWbsContextBuilder.format(null, 3, 200)).isEqualTo("");
    }

    @Test
    void rendersHierarchyWithIndentation() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        WbsNode root = node(rootId, null, "1", "Site Preparation");
        WbsNode child = node(childId, rootId, "1.1", "Excavation");
        WbsNode grandchild = node(UUID.randomUUID(), childId, "1.1.1", "Bulk excavation");

        String out = ExistingWbsContextBuilder.format(List.of(root, child, grandchild), 5, 50);

        assertThat(out).contains("1  Site Preparation");
        assertThat(out).contains("  1.1  Excavation");
        assertThat(out).contains("    1.1.1  Bulk excavation");
    }

    @Test
    void honorsMaxLevels() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        WbsNode n1 = node(a, null, "1", "L1");
        WbsNode n2 = node(b, a, "1.1", "L2");
        WbsNode n3 = node(UUID.randomUUID(), b, "1.1.1", "L3");

        String out = ExistingWbsContextBuilder.format(List.of(n1, n2, n3), 2, 50);

        assertThat(out).contains("1  L1");
        assertThat(out).contains("1.1  L2");
        assertThat(out).doesNotContain("1.1.1");
    }

    @Test
    void honorsMaxNodesAndAddsTruncationMarker() {
        UUID a = UUID.randomUUID();
        WbsNode n1 = node(a, null, "1", "First");
        WbsNode n2 = node(UUID.randomUUID(), null, "2", "Second");
        WbsNode n3 = node(UUID.randomUUID(), null, "3", "Third");

        String out = ExistingWbsContextBuilder.format(List.of(n1, n2, n3), 3, 2);

        assertThat(out).contains("First");
        assertThat(out).contains("Second");
        assertThat(out).contains("Truncated:");
    }

    @Test
    void scrubsControlCharsInNamesToProtectPromptShape() {
        // Control chars in names (\n, \r, \t) would otherwise corrupt the per-node
        // line shape and could be a vector for prompt-injection through node names.
        WbsNode n = node(UUID.randomUUID(), null, "1", "Bad\nname\rwith\tcontrol");
        String out = ExistingWbsContextBuilder.format(List.of(n), 2, 10);
        assertThat(out).contains("1  Bad name with control");
        // The node's own line must not contain raw control chars.
        for (String line : out.split("\\R")) {
            if (line.contains("Bad")) {
                assertThat(line.codePoints()).noneMatch(Character::isISOControl);
            }
        }
    }

    private static WbsNode node(UUID id, UUID parentId, String code, String name) {
        WbsNode n = new WbsNode();
        ReflectionTestUtils.setField(n, "id", id);
        n.setProjectId(UUID.randomUUID());
        n.setParentId(parentId);
        n.setCode(code);
        n.setName(name);
        n.setSortOrder(0);
        return n;
    }
}
