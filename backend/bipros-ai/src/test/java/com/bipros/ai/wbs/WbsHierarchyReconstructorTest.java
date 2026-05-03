package com.bipros.ai.wbs;

import com.bipros.ai.wbs.dto.WbsAiNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WbsHierarchyReconstructorTest {

    private static WbsAiNode leaf(String code, String name) {
        return new WbsAiNode(code, name, null, null, List.of());
    }

    private static WbsAiNode leafWithParent(String code, String name, String parentCode) {
        return new WbsAiNode(code, name, null, parentCode, List.of());
    }

    private static WbsAiNode branch(String code, String name, List<WbsAiNode> children) {
        return new WbsAiNode(code, name, null, null, children);
    }

    @Test
    void emptyOrNullInputReturnsEmpty() {
        assertThat(WbsHierarchyReconstructor.rehydrate(null)).isEmpty();
        assertThat(WbsHierarchyReconstructor.rehydrate(List.of())).isEmpty();
    }

    @Test
    void alreadyNestedTreeIsIdempotent() {
        // Input: 1 → (1.1, 1.2)
        WbsAiNode input = branch("1", "Pre-construction", List.of(
                leaf("1.1", "Statutory clearances"),
                leaf("1.2", "Design submission")
        ));
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(List.of(input));

        assertThat(out).hasSize(1);
        WbsAiNode root = out.get(0);
        assertThat(root.code()).isEqualTo("1");
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0).code()).isEqualTo("1.1");
        assertThat(root.children().get(1).code()).isEqualTo("1.2");
    }

    @Test
    void flatListWithDottedCodesIsRebuiltIntoTree() {
        // The bug case: model returned "1, 1.1, 1.2, 2, 2.1" all flat.
        List<WbsAiNode> flat = List.of(
                leaf("1",   "Pre-construction"),
                leaf("1.1", "Statutory clearances"),
                leaf("1.2", "Design submission"),
                leaf("2",   "Site preparation"),
                leaf("2.1", "Site clearance"),
                leaf("2.2", "Demolition")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);

        assertThat(out).hasSize(2);
        WbsAiNode one = out.get(0);
        WbsAiNode two = out.get(1);
        assertThat(one.code()).isEqualTo("1");
        assertThat(one.children()).extracting(WbsAiNode::code).containsExactly("1.1", "1.2");
        assertThat(two.code()).isEqualTo("2");
        assertThat(two.children()).extracting(WbsAiNode::code).containsExactly("2.1", "2.2");
    }

    @Test
    void deeplyNestedFlatInputIsRebuilt() {
        // 1, 1.1, 1.1.1, 1.1.1.1 — leaves only would test missing-intermediates;
        // here we have all intermediates so the tree should rebuild fully.
        List<WbsAiNode> flat = List.of(
                leaf("1",       "Phase 1"),
                leaf("1.1",     "Sub 1.1"),
                leaf("1.1.1",   "Pkg 1.1.1"),
                leaf("1.1.1.1", "Activity 1.1.1.1")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);

        assertThat(out).hasSize(1);
        WbsAiNode r = out.get(0);
        assertThat(r.code()).isEqualTo("1");
        assertThat(r.children()).hasSize(1);
        assertThat(r.children().get(0).code()).isEqualTo("1.1");
        assertThat(r.children().get(0).children()).hasSize(1);
        assertThat(r.children().get(0).children().get(0).code()).isEqualTo("1.1.1");
        assertThat(r.children().get(0).children().get(0).children().get(0).code()).isEqualTo("1.1.1.1");
    }

    @Test
    void prefixedCodesNestCorrectly() {
        // Real case from production: codes like RES-0012.3.1.1
        List<WbsAiNode> flat = List.of(
                leaf("RES-0012",         "Project root"),
                leaf("RES-0012.3",       "Earthworks"),
                leaf("RES-0012.3.1",     "Excavation"),
                leaf("RES-0012.3.1.1",   "Excavation in soil and ordinary rock"),
                leaf("RES-0012.4",       "Pavement"),
                leaf("RES-0012.4.1",     "GSB"),
                leaf("RES-0012.4.1.1",   "GSB 200 mm layer")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);

        assertThat(out).hasSize(1);
        WbsAiNode root = out.get(0);
        assertThat(root.code()).isEqualTo("RES-0012");
        assertThat(root.children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3", "RES-0012.4");
        assertThat(root.children().get(0).children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3.1");
        assertThat(root.children().get(0).children().get(0).children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3.1.1");
    }

    @Test
    void leavesWithMissingDirectIntermediateSynthesizesFullChain() {
        // Model emitted "1" and "1.1.1.1" but not "1.1" or "1.1.1". The
        // reconstructor synthesizes "1.1" and "1.1.1" so the leaf nests properly.
        List<WbsAiNode> flat = List.of(
                leaf("1",         "Phase 1"),
                leaf("1.1.1.1",   "Deep activity")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo("1");
        assertThat(out.get(0).children()).extracting(WbsAiNode::code).containsExactly("1.1");
        assertThat(out.get(0).children().get(0).children()).extracting(WbsAiNode::code)
                .containsExactly("1.1.1");
        assertThat(out.get(0).children().get(0).children().get(0).children())
                .extracting(WbsAiNode::code).containsExactly("1.1.1.1");
    }

    @Test
    void leavesWithNoBatchAncestorSynthesizeRoots() {
        // No "1" or "2" in the batch — synthesis creates them so leaves nest.
        List<WbsAiNode> flat = List.of(
                leaf("1.1", "Sub 1.1"),
                leaf("2.1", "Sub 2.1")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);
        assertThat(out).extracting(WbsAiNode::code).containsExactly("1", "2");
        assertThat(out.get(0).children()).extracting(WbsAiNode::code).containsExactly("1.1");
        assertThat(out.get(1).children()).extracting(WbsAiNode::code).containsExactly("2.1");
    }

    @Test
    void parentCodeReferenceTakesPriorityOverPrefixWalk() {
        // "PRJ-A.1" code, but parentCode points to "Other" — parentCode wins
        // (it resolves to a batch node).
        List<WbsAiNode> flat = List.of(
                leaf("Other",  "Other branch"),
                leafWithParent("PRJ-A.1", "Item", "Other")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo("Other");
        assertThat(out.get(0).children()).extracting(WbsAiNode::code).containsExactly("PRJ-A.1");
    }

    @Test
    void parentCodeReferenceToNonBatchNodeFallsThroughToPrefix() {
        // parentCode = "ZZZ" doesn't resolve, but "1" does via prefix walk.
        List<WbsAiNode> flat = List.of(
                leaf("1", "Root"),
                leafWithParent("1.1", "Child", "ZZZ")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).children()).extracting(WbsAiNode::code).containsExactly("1.1");
    }

    @Test
    void parentCodeToExistingProjectNodeIsPreservedAtRoot() {
        // "EXISTING-7" is not in the batch — assume it's an existing project node.
        // The reconstructor must keep parentCode on the root-level output so the
        // apply step can graft under that existing node.
        WbsAiNode n = leafWithParent("NEW-1", "New item", "EXISTING-7");
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(List.of(n));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo("NEW-1");
        assertThat(out.get(0).parentCode()).isEqualTo("EXISTING-7");
    }

    @Test
    void parentCodeIsNulledOutOnNonRootNodes() {
        // Even if AI sets parentCode AND nests via children, the nested node's
        // parentCode should be nulled out on output (children link is the truth).
        WbsAiNode child = leafWithParent("1.1", "Child", "1");
        WbsAiNode root = branch("1", "Root", List.of(child));
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(List.of(root));
        assertThat(out.get(0).children()).hasSize(1);
        assertThat(out.get(0).children().get(0).parentCode()).isNull();
    }

    @Test
    void duplicateCodesKeepFirstOccurrence() {
        // If model emits two nodes with same code, keep the first; the apply
        // path will surface the second as a collision separately.
        List<WbsAiNode> flat = List.of(
                leaf("1", "First"),
                leaf("1", "Second")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("First");
    }

    @Test
    void siblingsAreSortedByNaturalCodeOrder() {
        // 1.10 must come AFTER 1.2, not lexicographically before.
        List<WbsAiNode> flat = List.of(
                leaf("1",     "Root"),
                leaf("1.10",  "Tenth"),
                leaf("1.2",   "Second"),
                leaf("1.1",   "First")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);
        assertThat(out.get(0).children()).extracting(WbsAiNode::code)
                .containsExactly("1.1", "1.2", "1.10");
    }

    @Test
    void selfReferentialParentCodeIsIgnored() {
        WbsAiNode n = leafWithParent("1", "Root", "1");
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(List.of(n));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo("1");
    }

    // ---- Synthesis of missing intermediates ----

    @Test
    void leavesOnlyOutputSynthesizesAllIntermediateParents() {
        // The AI emitted only deep leaves with hierarchical codes — exact
        // failure mode reproduced from production. Reconstructor must
        // synthesize the missing intermediates so the tree isn't flat.
        List<WbsAiNode> flat = List.of(
                new WbsAiNode("RES-0012.3.1.1", "Excavation in soil and ordinary rock", null, "RES-0012.3.1", List.of()),
                new WbsAiNode("RES-0012.3.2.1", "Earth and gravel embankment placement", null, "RES-0012.3.2", List.of()),
                new WbsAiNode("RES-0012.4.1.1", "GSB 200 mm layer", null, "RES-0012.4.1", List.of())
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);

        assertThat(out).hasSize(1);
        WbsAiNode root = out.get(0);
        assertThat(root.code()).isEqualTo("RES-0012");
        assertThat(root.children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3", "RES-0012.4");

        WbsAiNode three = root.children().get(0);
        assertThat(three.children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3.1", "RES-0012.3.2");
        assertThat(three.children().get(0).children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3.1.1");

        WbsAiNode four = root.children().get(1);
        assertThat(four.children().get(0).children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.4.1.1");
    }

    @Test
    void synthesizedIntermediateNamesAreReadable() {
        List<WbsAiNode> flat = List.of(
                new WbsAiNode("RES-0012.3.1.1", "Activity", null, null, List.of())
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat);

        // RES-0012 (no dot) → name = the code itself
        // RES-0012.3 → "Section 3"
        // RES-0012.3.1 → "Section 3.1"
        WbsAiNode root = out.get(0);
        assertThat(root.code()).isEqualTo("RES-0012");
        assertThat(root.name()).isEqualTo("RES-0012");

        WbsAiNode l1 = root.children().get(0);
        assertThat(l1.code()).isEqualTo("RES-0012.3");
        assertThat(l1.name()).isEqualTo("Section 3");

        WbsAiNode l2 = l1.children().get(0);
        assertThat(l2.code()).isEqualTo("RES-0012.3.1");
        assertThat(l2.name()).isEqualTo("Section 3.1");

        WbsAiNode leaf = l2.children().get(0);
        assertThat(leaf.code()).isEqualTo("RES-0012.3.1.1");
        assertThat(leaf.name()).isEqualTo("Activity");
    }

    @Test
    void synthesisDoesNotDuplicateExistingProjectCodes() {
        // Project already has RES-0012 and RES-0012.3 — synthesizing them
        // would create renamed duplicates on apply. Reconstructor must skip.
        List<WbsAiNode> flat = List.of(
                new WbsAiNode("RES-0012.3.1.1", "Activity", null, null, List.of())
        );
        Set<String> existing = Set.of("RES-0012", "RES-0012.3");
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat, existing);

        // Only RES-0012.3.1 should be synthesized (the others exist already);
        // the chain plants under existing RES-0012.3 (top-level w/ parentCode set).
        assertThat(out).hasSize(1);
        WbsAiNode top = out.get(0);
        assertThat(top.code()).isEqualTo("RES-0012.3.1");
        assertThat(top.parentCode()).isEqualTo("RES-0012.3");  // graft under existing
        assertThat(top.children()).extracting(WbsAiNode::code)
                .containsExactly("RES-0012.3.1.1");
    }

    @Test
    void leafGraftsDirectlyUnderExistingProjectNodeWithoutSynthesis() {
        // Project has HIG-0001.5; AI emits HIG-0001.5.NEW with no parent.
        // No synthesis needed; node grafts under HIG-0001.5 via parentCode.
        List<WbsAiNode> flat = List.of(
                new WbsAiNode("HIG-0001.5.NEW", "New package", null, null, List.of())
        );
        Set<String> existing = Set.of("HIG-0001", "HIG-0001.5");
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(flat, existing);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo("HIG-0001.5.NEW");
        assertThat(out.get(0).parentCode()).isEqualTo("HIG-0001.5");
        assertThat(out.get(0).children()).isEmpty();
    }

    @Test
    void mixedNestedAndFlatProducesUnifiedTree() {
        // Model returned 1 nested with [1.1] but also 1.2 flat at root, and 2 flat.
        List<WbsAiNode> input = List.of(
                branch("1", "Pre-construction", List.of(leaf("1.1", "Clearances"))),
                leaf("1.2", "Design"),
                leaf("2",   "Site prep"),
                leaf("2.1", "Clearing")
        );
        List<WbsAiNode> out = WbsHierarchyReconstructor.rehydrate(input);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).children()).extracting(WbsAiNode::code).containsExactly("1.1", "1.2");
        assertThat(out.get(1).children()).extracting(WbsAiNode::code).containsExactly("2.1");
    }
}
