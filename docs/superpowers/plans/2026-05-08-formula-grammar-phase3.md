# Formula Grammar Phase 3 — Conditional Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SUMIF, COUNTIF, AVERAGEIF conditional aggregation functions to the ANTLR4 formula grammar and both visitors.

**Architecture:** Extend `Formula.g4` with 3 new tokens and parser rules requiring at least 2 arguments (criteria + one value). Implement equality-based matching in both visitors. BigDecimal visitor uses `compareTo == 0`. Object visitor uses existing `compareEquals()` (case-insensitive).

**Tech Stack:** Java 23, ANTLR4 4.13.1, JUnit 5, Maven

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `Formula.g4` | Modify | Grammar — add 3 tokens + parser rules |
| `BigDecimalFormulaVisitor.java` | Modify | Numeric visitor — SUMIF, COUNTIF, AVERAGEIF |
| `ObjectFormulaVisitor.java` | Modify | Object visitor — SUMIF, COUNTIF, AVERAGEIF |
| `BigDecimalFormulaVisitorTest.java` | Modify | Tests for conditional aggregation |
| `ObjectFormulaVisitorTest.java` | Modify | Tests for conditional aggregation |

---

### Task 1: Extend ANTLR4 Grammar

**Files:**
- Modify: `backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4`

- [ ] **Step 1: Add lexer tokens**

Insert after `PERCENTILE` token:

```antlr
SUMIF     : [Ss][Uu][Mm][Ii][Ff] ;
COUNTIF   : [Cc][Oo][Uu][Nn][Tt][Ii][Ff] ;
AVERAGEIF : [Aa][Vv][Ee][Rr][Aa][Gg][Ee][Ii][Ff] ;
```

- [ ] **Step 2: Add function call alternatives**

After `PERCENTILE` line in `functionCall`:

```antlr
    | SUMIF LPAREN expression (COMMA expression)+ RPAREN
    | COUNTIF LPAREN expression (COMMA expression)+ RPAREN
    | AVERAGEIF LPAREN expression (COMMA expression)+ RPAREN
```

Note: `(COMMA expression)+` requires at least one value argument after criteria.

- [ ] **Step 3: Update keyword-safe variable references**

Add `SUMIF`, `COUNTIF`, `AVERAGEIF` to `variableRef` and `bracketRef`.

- [ ] **Step 4: Regenerate ANTLR classes**

```bash
mvn -pl bipros-udf generate-sources -q
```

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4
git commit -m "feat(udf): add SUMIF, COUNTIF, AVERAGEIF tokens to Formula.g4"
```

---

### Task 2: Extend BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java`

- [ ] **Step 1: Add SUMIF, COUNTIF, AVERAGEIF handlers**

After `PERCENTILE` block in `visitFunctionCall`:

```java
        if (ctx.SUMIF() != null) {
            if (ctx.expression().size() < 2) return zeroDefault;
            BigDecimal criteria = visit(ctx.expression(0));
            return ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> v.compareTo(criteria) == 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (ctx.COUNTIF() != null) {
            if (ctx.expression().size() < 2) return BigDecimal.ZERO;
            BigDecimal criteria = visit(ctx.expression(0));
            long count = ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> v.compareTo(criteria) == 0)
                    .count();
            return BigDecimal.valueOf(count);
        }
        if (ctx.AVERAGEIF() != null) {
            if (ctx.expression().size() < 2) return zeroDefault;
            BigDecimal criteria = visit(ctx.expression(0));
            var matches = ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> v.compareTo(criteria) == 0)
                    .toList();
            if (matches.isEmpty()) return zeroDefault;
            BigDecimal sum = matches.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(BigDecimal.valueOf(matches.size()), scale, roundingMode);
        }
```

- [ ] **Step 2: Commit**

```bash
git add backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java
git commit -m "feat(udf): add SUMIF, COUNTIF, AVERAGEIF to BigDecimalFormulaVisitor"
```

---

### Task 3: Extend ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java`

- [ ] **Step 1: Add SUMIF, COUNTIF, AVERAGEIF handlers**

After `PERCENTILE` block in `visitFunctionCall`:

```java
        if (ctx.SUMIF() != null) {
            if (ctx.expression().size() < 2) return 0.0;
            Object criteria = visit(ctx.expression(0));
            return ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> compareEquals(v, criteria))
                    .mapToDouble(this::toDouble)
                    .sum();
        }
        if (ctx.COUNTIF() != null) {
            if (ctx.expression().size() < 2) return 0.0;
            Object criteria = visit(ctx.expression(0));
            long count = ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> compareEquals(v, criteria))
                    .count();
            return (double) count;
        }
        if (ctx.AVERAGEIF() != null) {
            if (ctx.expression().size() < 2) return 0.0;
            Object criteria = visit(ctx.expression(0));
            var matches = ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> compareEquals(v, criteria))
                    .toList();
            if (matches.isEmpty()) return 0.0;
            return matches.stream().mapToDouble(this::toDouble).average().orElse(0.0);
        }
```

- [ ] **Step 2: Commit**

```bash
git add backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java
git commit -m "feat(udf): add SUMIF, COUNTIF, AVERAGEIF to ObjectFormulaVisitor"
```

---

### Task 4: Write Tests for BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java`

- [ ] **Step 1: Add ConditionalAggregation nested test class**

Append before the `bd()` helpers:

```java
    @Nested
    @DisplayName("Conditional Aggregation")
    class ConditionalAggregationTests {

        @Test
        @DisplayName("SUMIF(5, 5, 3, 5, 2) = 10")
        void sumif() {
            assertThat(eval("SUMIF(5, 5, 3, 5, 2)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("SUMIF(5, 1, 2, 3) = 0")
        void sumifNoMatches() {
            assertThat(eval("SUMIF(5, 1, 2, 3)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COUNTIF(5, 5, 3, 5, 2) = 2")
        void countif() {
            assertThat(eval("COUNTIF(5, 5, 3, 5, 2)", Map.of()))
                    .isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("COUNTIF(5, 1, 2, 3) = 0")
        void countifNoMatches() {
            assertThat(eval("COUNTIF(5, 1, 2, 3)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("AVERAGEIF(10, 10, 20, 10) = 10")
        void averageif() {
            assertThat(eval("AVERAGEIF(10, 10, 20, 10)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("AVERAGEIF(5, 1, 2, 3) returns zero default")
        void averageifNoMatches() {
            assertThat(eval("AVERAGEIF(5, 1, 2, 3)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("SUMIF with variable criteria")
        void sumifWithVariable() {
            Map<String, BigDecimal> ctx = Map.of("threshold", bd(5), "a", bd(5), "b", bd(3), "c", bd(5));
            assertThat(eval("SUMIF($threshold, $a, $b, $c)", ctx))
                    .isEqualByComparingTo(bd(10));
        }
    }
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl bipros-udf -Dtest=BigDecimalFormulaVisitorTest -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java
git commit -m "test(udf): add SUMIF, COUNTIF, AVERAGEIF tests for BigDecimalFormulaVisitor"
```

---

### Task 5: Write Tests for ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java`

- [ ] **Step 1: Add ConditionalAggregation nested test class**

Append:

```java
    @Nested
    @DisplayName("Conditional Aggregation")
    class ConditionalAggregationTests {

        @Test
        @DisplayName("SUMIF(5, 5, 3, 5, 2) = 10.0")
        void sumif() {
            assertThat(eval("SUMIF(5, 5, 3, 5, 2)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("SUMIF(5, 1, 2, 3) = 0.0")
        void sumifNoMatches() {
            assertThat(eval("SUMIF(5, 1, 2, 3)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("COUNTIF(5, 5, 3, 5, 2) = 2.0")
        void countif() {
            assertThat(eval("COUNTIF(5, 5, 3, 5, 2)")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("COUNTIF(5, 1, 2, 3) = 0.0")
        void countifNoMatches() {
            assertThat(eval("COUNTIF(5, 1, 2, 3)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("AVERAGEIF(10, 10, 20, 10) = 10.0")
        void averageif() {
            assertThat(eval("AVERAGEIF(10, 10, 20, 10)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("AVERAGEIF(5, 1, 2, 3) = 0.0")
        void averageifNoMatches() {
            assertThat(eval("AVERAGEIF(5, 1, 2, 3)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("COUNTIF with string criteria (case-insensitive)")
        void countifString() {
            assertThat(eval("COUNTIF(\"A\", \"A\", \"B\", \"a\")")).isEqualTo("2.0");
        }
    }
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl bipros-udf -Dtest=ObjectFormulaVisitorTest -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java
git commit -m "test(udf): add SUMIF, COUNTIF, AVERAGEIF tests for ObjectFormulaVisitor"
```

---

### Task 6: Regression Test

- [ ] **Step 1: Run all bipros-udf tests**

```bash
mvn test -pl bipros-udf -q
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl bipros-api -am -q
```
