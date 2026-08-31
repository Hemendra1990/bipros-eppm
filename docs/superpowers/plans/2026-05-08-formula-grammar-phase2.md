# Formula Grammar Phase 2 — Statistical Functions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add AVERAGE, COUNT, STDEV, MEDIAN, PERCENTILE functions to the ANTLR4 formula grammar and both visitors.

**Architecture:** Extend `Formula.g4` with 5 new statistical function tokens and parser rules. Implement population statistics in both `BigDecimalFormulaVisitor` (returning BigDecimal) and `ObjectFormulaVisitor` (returning Double). All functions use varargs pattern (COMMA expression)*.

**Tech Stack:** Java 23, ANTLR4 4.13.1, JUnit 5, Maven

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `Formula.g4` | Modify | Grammar — add 5 new tokens + parser rules |
| `BigDecimalFormulaVisitor.java` | Modify | Numeric visitor — AVERAGE, COUNT, STDEV, MEDIAN, PERCENTILE |
| `ObjectFormulaVisitor.java` | Modify | Object visitor — AVERAGE, COUNT, STDEV, MEDIAN, PERCENTILE |
| `BigDecimalFormulaVisitorTest.java` | Modify | Tests for statistical functions |
| `ObjectFormulaVisitorTest.java` | Modify | Tests for statistical functions |

---

### Task 1: Extend ANTLR4 Grammar

**Files:**
- Modify: `backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4`

- [ ] **Step 1: Add lexer tokens after EULER**

Insert after `EULER` token:

```antlr
AVERAGE    : [Aa][Vv][Ee][Rr][Aa][Gg][Ee] ;
COUNT      : [Cc][Oo][Uu][Nn][Tt] ;
STDEV      : [Ss][Tt][Dd][Ee][Vv] ;
MEDIAN     : [Mm][Ee][Dd][Ii][Aa][Nn] ;
PERCENTILE : [Pp][Ee][Rr][Cc][Ee][Nn][Tt][Ii][Ll][Ee] ;
```

- [ ] **Step 2: Add function call alternatives**

In `functionCall` rule, after `COS` line, add:

```antlr
    | AVERAGE LPAREN expression (COMMA expression)* RPAREN
    | COUNT LPAREN expression (COMMA expression)* RPAREN
    | STDEV LPAREN expression (COMMA expression)* RPAREN
    | MEDIAN LPAREN expression (COMMA expression)* RPAREN
    | PERCENTILE LPAREN expression (COMMA expression)* RPAREN
```

- [ ] **Step 3: Update keyword-safe variable references**

Update `variableRef` and `bracketRef` to include all 5 new tokens.

- [ ] **Step 4: Regenerate ANTLR classes**

```bash
mvn -pl bipros-udf generate-sources -q
```

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4
git commit -m "feat(udf): add statistical function tokens to Formula.g4"
```

---

### Task 2: Extend BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java`

- [ ] **Step 1: Add helper method for collecting arguments**

Add a private helper method before `visitFunctionCall`:

```java
    private java.util.List<BigDecimal> collectArguments(FormulaParser.FunctionCallContext ctx) {
        return ctx.expression().stream()
                .map(this::visit)
                .toList();
    }
```

- [ ] **Step 2: Add AVERAGE handler**

After `COS` block in `visitFunctionCall`:

```java
        if (ctx.AVERAGE() != null) {
            var args = collectArguments(ctx);
            if (args.isEmpty()) return zeroDefault;
            BigDecimal sum = args.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(BigDecimal.valueOf(args.size()), scale, roundingMode);
        }
```

- [ ] **Step 3: Add COUNT handler**

```java
        if (ctx.COUNT() != null) {
            return BigDecimal.valueOf(ctx.expression().size());
        }
```

- [ ] **Step 4: Add STDEV handler**

```java
        if (ctx.STDEV() != null) {
            var args = collectArguments(ctx);
            if (args.size() < 2) return zeroDefault;
            BigDecimal mean = args.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(args.size()), scale, roundingMode);
            BigDecimal sumSqDiff = args.stream()
                    .map(v -> v.subtract(mean).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal variance = sumSqDiff.divide(BigDecimal.valueOf(args.size()), scale, roundingMode);
            return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                    .setScale(scale, roundingMode);
        }
```

- [ ] **Step 5: Add MEDIAN handler**

```java
        if (ctx.MEDIAN() != null) {
            var args = collectArguments(ctx);
            if (args.isEmpty()) return zeroDefault;
            var sorted = args.stream().sorted().toList();
            int n = sorted.size();
            if (n % 2 == 1) {
                return sorted.get(n / 2);
            }
            BigDecimal a = sorted.get(n / 2 - 1);
            BigDecimal b = sorted.get(n / 2);
            return a.add(b).divide(BigDecimal.valueOf(2), scale, roundingMode);
        }
```

- [ ] **Step 6: Add PERCENTILE handler**

```java
        if (ctx.PERCENTILE() != null) {
            var args = collectArguments(ctx);
            if (args.size() < 2) return zeroDefault;
            BigDecimal rank = args.get(args.size() - 1);
            var data = args.subList(0, args.size() - 1);
            if (data.isEmpty()) return zeroDefault;
            var sorted = data.stream().sorted().toList();
            int n = sorted.size();
            double idx = rank.doubleValue() * (n - 1);
            int lower = (int) Math.floor(idx);
            int upper = (int) Math.ceil(idx);
            if (lower == upper) {
                return sorted.get(lower);
            }
            double fraction = idx - lower;
            BigDecimal valLower = sorted.get(lower);
            BigDecimal valUpper = sorted.get(upper);
            return valLower.add(valUpper.subtract(valLower)
                    .multiply(BigDecimal.valueOf(fraction)))
                    .setScale(scale, roundingMode);
        }
```

- [ ] **Step 7: Commit**

```bash
git add backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java
git commit -m "feat(udf): add statistical functions to BigDecimalFormulaVisitor"
```

---

### Task 3: Extend ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java`

- [ ] **Step 1: Add helper method for collecting arguments**

```java
    private java.util.List<Double> collectDoubleArguments(FormulaParser.FunctionCallContext ctx) {
        return ctx.expression().stream()
                .map(e -> toDouble(visit(e)))
                .toList();
    }
```

- [ ] **Step 2: Add all 5 statistical function handlers**

After `COS` block in `visitFunctionCall`:

```java
        if (ctx.AVERAGE() != null) {
            var args = collectDoubleArguments(ctx);
            if (args.isEmpty()) return 0.0;
            return args.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        if (ctx.COUNT() != null) {
            return (double) ctx.expression().size();
        }
        if (ctx.STDEV() != null) {
            var args = collectDoubleArguments(ctx);
            if (args.size() < 2) return 0.0;
            double mean = args.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sumSqDiff = args.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .sum();
            double variance = sumSqDiff / args.size();
            return Math.sqrt(variance);
        }
        if (ctx.MEDIAN() != null) {
            var args = collectDoubleArguments(ctx);
            if (args.isEmpty()) return 0.0;
            var sorted = args.stream().sorted().toList();
            int n = sorted.size();
            if (n % 2 == 1) {
                return sorted.get(n / 2);
            }
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        }
        if (ctx.PERCENTILE() != null) {
            var args = collectDoubleArguments(ctx);
            if (args.size() < 2) return 0.0;
            double rank = args.get(args.size() - 1);
            var data = args.subList(0, args.size() - 1);
            if (data.isEmpty()) return 0.0;
            var sorted = data.stream().sorted().toList();
            int n = sorted.size();
            double idx = rank * (n - 1);
            int lower = (int) Math.floor(idx);
            int upper = (int) Math.ceil(idx);
            if (lower == upper) {
                return sorted.get(lower);
            }
            double fraction = idx - lower;
            return sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower));
        }
```

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java
git commit -m "feat(udf): add statistical functions to ObjectFormulaVisitor"
```

---

### Task 4: Write Tests for BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java`

- [ ] **Step 1: Add StatisticalFunctions nested test class**

Append:

```java
    @Nested
    @DisplayName("Statistical Functions")
    class StatisticalFunctionsTests {

        @Test
        @DisplayName("AVERAGE(10, 20, 30) = 20")
        void average() {
            assertThat(eval("AVERAGE(10, 20, 30)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("AVERAGE(10) = 10")
        void averageSingle() {
            assertThat(eval("AVERAGE(10)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("AVERAGE() returns zero default")
        void averageEmpty() {
            assertThat(eval("AVERAGE()", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COUNT(10, 20, 30) = 3")
        void count() {
            assertThat(eval("COUNT(10, 20, 30)", Map.of()))
                    .isEqualByComparingTo(bd(3));
        }

        @Test
        @DisplayName("COUNT() = 0")
        void countEmpty() {
            assertThat(eval("COUNT()", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("STDEV(2, 4, 4, 4, 5, 5, 7, 9) = 2")
        void stdev() {
            assertThat(eval("STDEV(2, 4, 4, 4, 5, 5, 7, 9)", Map.of()))
                    .isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("STDEV(10) = 0")
        void stdevSingle() {
            assertThat(eval("STDEV(10)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("STDEV() returns zero default")
        void stdevEmpty() {
            assertThat(eval("STDEV()", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30) = 20")
        void medianOdd() {
            assertThat(eval("MEDIAN(10, 20, 30)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30, 40) = 25")
        void medianEven() {
            assertThat(eval("MEDIAN(10, 20, 30, 40)", Map.of()))
                    .isEqualByComparingTo(bd(25));
        }

        @Test
        @DisplayName("MEDIAN() returns zero default")
        void medianEmpty() {
            assertThat(eval("MEDIAN()", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 40, 50, 0.5) = 30")
        void percentileMedian() {
            assertThat(eval("PERCENTILE(10, 20, 30, 40, 50, 0.5)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.0) = 10")
        void percentileZero() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.0)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 1.0) = 30")
        void percentileHundred() {
            assertThat(eval("PERCENTILE(10, 20, 30, 1.0)", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.25) = 15")
        void percentileQuarter() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.25)", Map.of()))
                    .isEqualByComparingTo(bd(15));
        }

        @Test
        @DisplayName("PERCENTILE(10) returns zero default")
        void percentileNoRank() {
            assertThat(eval("PERCENTILE(10)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
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
git commit -m "test(udf): add statistical function tests for BigDecimalFormulaVisitor"
```

---

### Task 5: Write Tests for ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java`

- [ ] **Step 1: Add StatisticalFunctions nested test class**

Append:

```java
    @Nested
    @DisplayName("Statistical Functions")
    class StatisticalFunctionsTests {

        @Test
        @DisplayName("AVERAGE(10, 20, 30) = 20.0")
        void average() {
            assertThat(eval("AVERAGE(10, 20, 30)")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("AVERAGE(10) = 10.0")
        void averageSingle() {
            assertThat(eval("AVERAGE(10)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("AVERAGE() = 0.0")
        void averageEmpty() {
            assertThat(eval("AVERAGE()")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("COUNT(10, 20, 30) = 3.0")
        void count() {
            assertThat(eval("COUNT(10, 20, 30)")).isEqualTo("3.0");
        }

        @Test
        @DisplayName("COUNT() = 0.0")
        void countEmpty() {
            assertThat(eval("COUNT()")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("STDEV(2, 4, 4, 4, 5, 5, 7, 9) = 2.0")
        void stdev() {
            assertThat(eval("STDEV(2, 4, 4, 4, 5, 5, 7, 9)")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("STDEV(10) = 0.0")
        void stdevSingle() {
            assertThat(eval("STDEV(10)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30) = 20.0")
        void medianOdd() {
            assertThat(eval("MEDIAN(10, 20, 30)")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("MEDIAN(10, 20, 30, 40) = 25.0")
        void medianEven() {
            assertThat(eval("MEDIAN(10, 20, 30, 40)")).isEqualTo("25.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 40, 50, 0.5) = 30.0")
        void percentileMedian() {
            assertThat(eval("PERCENTILE(10, 20, 30, 40, 50, 0.5)")).isEqualTo("30.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.0) = 10.0")
        void percentileZero() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.0)")).isEqualTo("10.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 1.0) = 30.0")
        void percentileHundred() {
            assertThat(eval("PERCENTILE(10, 20, 30, 1.0)")).isEqualTo("30.0");
        }

        @Test
        @DisplayName("PERCENTILE(10, 20, 30, 0.25) = 15.0")
        void percentileQuarter() {
            assertThat(eval("PERCENTILE(10, 20, 30, 0.25)")).isEqualTo("15.0");
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
git commit -m "test(udf): add statistical function tests for ObjectFormulaVisitor"
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

---

## Spec Coverage Checklist

- [x] Grammar: 5 new tokens added
- [x] Grammar: parser rules for all 5 functions
- [x] Grammar: keyword-safe variables updated
- [x] BigDecimalFormulaVisitor: AVERAGE, COUNT, STDEV, MEDIAN, PERCENTILE
- [x] ObjectFormulaVisitor: AVERAGE, COUNT, STDEV, MEDIAN, PERCENTILE
- [x] Tests: BigDecimal statistical functions
- [x] Tests: Object statistical functions
- [x] Regression: full module test suite
