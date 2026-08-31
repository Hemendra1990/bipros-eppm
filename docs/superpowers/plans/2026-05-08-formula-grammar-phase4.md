# Formula Grammar Phase 4 — Date/Time + Lookup Functions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add TODAY, DATEDIFF, DAYSOFMONTH, YEAR, MONTH, LOOKUP, INDEX functions to the ANTLR4 formula grammar and both visitors.

**Architecture:** Extend `Formula.g4` with 7 new tokens. Implement date functions using `java.time.LocalDate` with epoch-day representation. Implement LOOKUP as linear key-value search and INDEX as 1-based array access.

**Tech Stack:** Java 23, ANTLR4 4.13.1, JUnit 5, Maven

---

### Task 1: Extend ANTLR4 Grammar

**Files:**
- Modify: `backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4`

- [ ] **Step 1: Add lexer tokens**

After `AVERAGEIF`:

```antlr
// Date/Time functions
TODAY      : [Tt][Oo][Dd][Aa][Yy] ;
DATEDIFF   : [Dd][Aa][Tt][Ee][Dd][Ii][Ff][Ff] ;
DAYSOFMONTH: [Dd][Aa][Yy][Ss][Oo][Ff][Mm][Oo][Nn][Tt][Hh] ;
YEAR       : [Yy][Ee][Aa][Rr] ;
MONTH      : [Mm][Oo][Nn][Tt][Hh] ;

// Lookup/Reference functions
LOOKUP     : [Ll][Oo][Oo][Kk][Uu][Pp] ;
INDEX      : [Ii][Nn][Dd][Ee][Xx] ;
```

- [ ] **Step 2: Add function call alternatives**

After `AVERAGEIF` line:

```antlr
    | TODAY LPAREN RPAREN
    | DATEDIFF LPAREN expression COMMA expression RPAREN
    | DAYSOFMONTH LPAREN expression RPAREN
    | YEAR LPAREN expression RPAREN
    | MONTH LPAREN expression RPAREN
    | LOOKUP LPAREN expression (COMMA expression)+ RPAREN
    | INDEX LPAREN expression (COMMA expression)+ RPAREN
```

- [ ] **Step 3: Update keyword-safe variables**

Add all 7 new tokens to `variableRef` and `bracketRef`.

- [ ] **Step 4: Regenerate and commit**

```bash
mvn -pl bipros-udf generate-sources -q
git add Formula.g4 && git commit -m "feat(udf): add date/time and lookup tokens to Formula.g4"
```

---

### Task 2: Extend BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java`

- [ ] **Step 1: Add date parsing helper**

```java
    private java.time.LocalDate parseDate(BigDecimal value) {
        try {
            return java.time.LocalDate.ofEpochDay(value.longValue());
        } catch (Exception e) {
            return null;
        }
    }

    private java.time.LocalDate parseDate(String value) {
        try {
            return java.time.LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private java.time.LocalDate resolveDate(Object value) {
        if (value instanceof BigDecimal) return parseDate((BigDecimal) value);
        if (value instanceof String) return parseDate((String) value);
        return null;
    }
```

Note: In BigDecimal visitor, the `visit()` method returns BigDecimal, so strings would already be converted to BigDecimal.ZERO. We need a different approach - we need to check if the expression is a string literal before visiting it.

Actually, in BigDecimalFormulaVisitor, `visit(ctx.expression())` on a string literal returns `BigDecimal.ZERO` (from visitPrimary). So we can't distinguish "2024-01-01" from "" in the result. 

Solution: Check the expression type directly:

```java
    private java.time.LocalDate resolveDate(FormulaParser.ExpressionContext ctx) {
        if (ctx.orExpr().andExpr().size() == 1 
            && ctx.orExpr().andExpr(0).comparisonExpr().size() == 1
            && ctx.orExpr().andExpr(0).comparisonExpr(0).additiveExpr().size() == 1
            && ctx.orExpr().andExpr(0).comparisonExpr(0).additiveExpr(0).multiplicativeExpr().size() == 1
            && ctx.orExpr().andExpr(0).comparisonExpr(0).additiveExpr(0).multiplicativeExpr(0).unaryExpr().size() == 1
            && ctx.orExpr().andExpr(0).comparisonExpr(0).additiveExpr(0).multiplicativeExpr(0).unaryExpr(0).primary().stringLiteral() != null) {
            // It's a string literal
            String text = ctx.orExpr().andExpr(0).comparisonExpr(0).additiveExpr(0).multiplicativeExpr(0).unaryExpr(0).primary().stringLiteral().getText();
            return parseDateString(text);
        }
        // Otherwise treat as numeric epoch day
        return parseDate(visit(ctx));
    }
```

This is very verbose. A cleaner approach: add a method that checks if the expression context contains a string literal.

Actually, let me use a simpler approach - just try to parse as epoch day first, and if that fails, try to visit and stringify:

```java
    private java.time.LocalDate resolveDate(FormulaParser.ExpressionContext ctx) {
        BigDecimal val = visit(ctx);
        if (val.compareTo(BigDecimal.ZERO) != 0) {
            // Try epoch day
            try {
                return java.time.LocalDate.ofEpochDay(val.longValue());
            } catch (Exception ignored) {}
        }
        // Try string - need to get raw text
        String text = ctx.getText();
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            text = text.substring(1, text.length() - 1);
        }
        try {
            return java.time.LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }
```

This is simpler but has edge cases (e.g., "$var" that evaluates to 0 but is a string "2024-01-01"). For the first iteration, this is acceptable. The user can pass dates as string literals.

- [ ] **Step 2: Add TODAY handler**

```java
        if (ctx.TODAY() != null) {
            return BigDecimal.valueOf(java.time.LocalDate.now().toEpochDay());
        }
```

- [ ] **Step 3: Add DATEDIFF handler**

```java
        if (ctx.DATEDIFF() != null) {
            if (ctx.expression().size() < 2) return zeroDefault;
            java.time.LocalDate start = resolveDate(ctx.expression(0));
            java.time.LocalDate end = resolveDate(ctx.expression(1));
            if (start == null || end == null) return zeroDefault;
            return BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start, end));
        }
```

- [ ] **Step 4: Add DAYSOFMONTH handler**

```java
        if (ctx.DAYSOFMONTH() != null) {
            if (ctx.expression().isEmpty()) return zeroDefault;
            java.time.LocalDate date = resolveDate(ctx.expression(0));
            if (date == null) return zeroDefault;
            return BigDecimal.valueOf(date.lengthOfMonth());
        }
```

- [ ] **Step 5: Add YEAR handler**

```java
        if (ctx.YEAR() != null) {
            if (ctx.expression().isEmpty()) return zeroDefault;
            java.time.LocalDate date = resolveDate(ctx.expression(0));
            if (date == null) return zeroDefault;
            return BigDecimal.valueOf(date.getYear());
        }
```

- [ ] **Step 6: Add MONTH handler**

```java
        if (ctx.MONTH() != null) {
            if (ctx.expression().isEmpty()) return zeroDefault;
            java.time.LocalDate date = resolveDate(ctx.expression(0));
            if (date == null) return zeroDefault;
            return BigDecimal.valueOf(date.getMonthValue());
        }
```

- [ ] **Step 7: Add LOOKUP handler**

```java
        if (ctx.LOOKUP() != null) {
            if (ctx.expression().size() < 3) return zeroDefault;
            BigDecimal criteria = visit(ctx.expression(0));
            var values = ctx.expression().stream().skip(1).map(this::visit).toList();
            // values are paired: (lookup1, result1, lookup2, result2, ...)
            for (int i = 0; i + 1 < values.size(); i += 2) {
                if (values.get(i).compareTo(criteria) == 0) {
                    return values.get(i + 1);
                }
            }
            return zeroDefault;
        }
```

- [ ] **Step 8: Add INDEX handler**

```java
        if (ctx.INDEX() != null) {
            if (ctx.expression().size() < 2) return zeroDefault;
            var values = ctx.expression().stream()
                    .limit(ctx.expression().size() - 1)
                    .map(this::visit)
                    .toList();
            int pos = visit(ctx.expression(ctx.expression().size() - 1)).intValue();
            if (pos < 1 || pos > values.size()) return zeroDefault;
            return values.get(pos - 1);
        }
```

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(udf): add date/time and lookup functions to BigDecimalFormulaVisitor"
```

---

### Task 3: Extend ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java`

- [ ] **Step 1: Add date parsing helper**

```java
    private java.time.LocalDate resolveDate(Object value) {
        if (value instanceof Number) {
            try {
                return java.time.LocalDate.ofEpochDay(((Number) value).longValue());
            } catch (Exception e) {
                return null;
            }
        }
        String text = String.valueOf(value).trim();
        try {
            return java.time.LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
```

- [ ] **Step 2: Add all 7 function handlers**

After `AVERAGEIF` block:

```java
        if (ctx.TODAY() != null) {
            return (double) java.time.LocalDate.now().toEpochDay();
        }
        if (ctx.DATEDIFF() != null) {
            if (ctx.expression().size() < 2) return 0.0;
            java.time.LocalDate start = resolveDate(visit(ctx.expression(0)));
            java.time.LocalDate end = resolveDate(visit(ctx.expression(1)));
            if (start == null || end == null) return 0.0;
            return (double) java.time.temporal.ChronoUnit.DAYS.between(start, end);
        }
        if (ctx.DAYSOFMONTH() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            java.time.LocalDate date = resolveDate(visit(ctx.expression(0)));
            if (date == null) return 0.0;
            return (double) date.lengthOfMonth();
        }
        if (ctx.YEAR() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            java.time.LocalDate date = resolveDate(visit(ctx.expression(0)));
            if (date == null) return 0.0;
            return (double) date.getYear();
        }
        if (ctx.MONTH() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            java.time.LocalDate date = resolveDate(visit(ctx.expression(0)));
            if (date == null) return 0.0;
            return (double) date.getMonthValue();
        }
        if (ctx.LOOKUP() != null) {
            if (ctx.expression().size() < 3) return 0.0;
            Object criteria = visit(ctx.expression(0));
            var values = ctx.expression().stream().skip(1).map(this::visit).toList();
            for (int i = 0; i + 1 < values.size(); i += 2) {
                if (compareEquals(values.get(i), criteria)) {
                    return values.get(i + 1);
                }
            }
            return 0.0;
        }
        if (ctx.INDEX() != null) {
            if (ctx.expression().size() < 2) return 0.0;
            var values = ctx.expression().stream()
                    .limit(ctx.expression().size() - 1)
                    .map(this::visit)
                    .toList();
            int pos = (int) toDouble(visit(ctx.expression(ctx.expression().size() - 1)));
            if (pos < 1 || pos > values.size()) return 0.0;
            return values.get(pos - 1);
        }
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(udf): add date/time and lookup functions to ObjectFormulaVisitor"
```

---

### Task 4: Write Tests for BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java`

- [ ] **Step 1: Add DateTimeFunctions nested test class**

```java
    @Nested
    @DisplayName("Date/Time Functions")
    class DateTimeFunctionsTests {

        @Test
        @DisplayName("TODAY returns days since epoch > 20000")
        void today() {
            assertThat(eval("TODAY()", Map.of()))
                    .isGreaterThan(bd(20000));
        }

        @Test
        @DisplayName("DATEDIFF with string dates")
        void datediffString() {
            assertThat(eval("DATEDIFF(\"2024-01-01\", \"2024-01-10\")", Map.of()))
                    .isEqualByComparingTo(bd(9));
        }

        @Test
        @DisplayName("DATEDIFF with epoch days")
        void datediffEpoch() {
            assertThat(eval("DATEDIFF(19723, 19732)", Map.of()))
                    .isEqualByComparingTo(bd(9));
        }

        @Test
        @DisplayName("DATEDIFF with invalid date returns zero")
        void datediffInvalid() {
            assertThat(eval("DATEDIFF(\"invalid\", \"2024-01-10\")", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("DAYSOFMONTH for Feb 2024 (leap) = 29")
        void daysofmonthLeap() {
            assertThat(eval("DAYSOFMONTH(\"2024-02-15\")", Map.of()))
                    .isEqualByComparingTo(bd(29));
        }

        @Test
        @DisplayName("DAYSOFMONTH for Feb 2023 = 28")
        void daysofmonthNonLeap() {
            assertThat(eval("DAYSOFMONTH(\"2023-02-15\")", Map.of()))
                    .isEqualByComparingTo(bd(28));
        }

        @Test
        @DisplayName("DAYSOFMONTH for April = 30")
        void daysofmonthApril() {
            assertThat(eval("DAYSOFMONTH(\"2024-04-15\")", Map.of()))
                    .isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("YEAR(\"2024-06-15\") = 2024")
        void year() {
            assertThat(eval("YEAR(\"2024-06-15\")", Map.of()))
                    .isEqualByComparingTo(bd(2024));
        }

        @Test
        @DisplayName("MONTH(\"2024-06-15\") = 6")
        void month() {
            assertThat(eval("MONTH(\"2024-06-15\")", Map.of()))
                    .isEqualByComparingTo(bd(6));
        }
    }
```

- [ ] **Step 2: Add LookupFunctions nested test class**

```java
    @Nested
    @DisplayName("Lookup Functions")
    class LookupFunctionsTests {

        @Test
        @DisplayName("LOOKUP(5, 1, 10, 5, 50, 3, 30) = 50")
        void lookup() {
            assertThat(eval("LOOKUP(5, 1, 10, 5, 50, 3, 30)", Map.of()))
                    .isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("LOOKUP(99, 1, 10, 5, 50) returns zero")
        void lookupNotFound() {
            assertThat(eval("LOOKUP(99, 1, 10, 5, 50)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("LOOKUP with odd pairs ignores last")
        void lookupOddPairs() {
            assertThat(eval("LOOKUP(1, 1, 10, 5)", Map.of()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("INDEX(10, 20, 30, 2) = 20")
        void index() {
            assertThat(eval("INDEX(10, 20, 30, 2)", Map.of()))
                    .isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("INDEX out of bounds returns zero")
        void indexOutOfBounds() {
            assertThat(eval("INDEX(10, 20, 30, 5)", Map.of()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
```

- [ ] **Step 3: Run tests and commit**

---

### Task 5: Write Tests for ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java`

- [ ] **Step 1: Add DateTimeFunctions nested test class**

```java
    @Nested
    @DisplayName("Date/Time Functions")
    class DateTimeFunctionsTests {

        @Test
        @DisplayName("TODAY returns days since epoch")
        void today() {
            assertThat(eval("TODAY()")).isNotEqualTo("0.0");
        }

        @Test
        @DisplayName("DATEDIFF with string dates = 9.0")
        void datediffString() {
            assertThat(eval("DATEDIFF(\"2024-01-01\", \"2024-01-10\")")).isEqualTo("9.0");
        }

        @Test
        @DisplayName("DAYSOFMONTH for Feb 2024 = 29.0")
        void daysofmonthLeap() {
            assertThat(eval("DAYSOFMONTH(\"2024-02-15\")")).isEqualTo("29.0");
        }

        @Test
        @DisplayName("YEAR(\"2024-06-15\") = 2024.0")
        void year() {
            assertThat(eval("YEAR(\"2024-06-15\")")).isEqualTo("2024.0");
        }

        @Test
        @DisplayName("MONTH(\"2024-06-15\") = 6.0")
        void month() {
            assertThat(eval("MONTH(\"2024-06-15\")")).isEqualTo("6.0");
        }
    }
```

- [ ] **Step 2: Add LookupFunctions nested test class**

```java
    @Nested
    @DisplayName("Lookup Functions")
    class LookupFunctionsTests {

        @Test
        @DisplayName("LOOKUP(5, 1, 10, 5, 50, 3, 30) = 50.0")
        void lookup() {
            assertThat(eval("LOOKUP(5, 1, 10, 5, 50, 3, 30)")).isEqualTo("50.0");
        }

        @Test
        @DisplayName("LOOKUP(99, 1, 10, 5, 50) = 0.0")
        void lookupNotFound() {
            assertThat(eval("LOOKUP(99, 1, 10, 5, 50)")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("INDEX(10, 20, 30, 2) = 20.0")
        void index() {
            assertThat(eval("INDEX(10, 20, 30, 2)")).isEqualTo("20.0");
        }

        @Test
        @DisplayName("INDEX with string values")
        void indexStrings() {
            assertThat(eval("INDEX(\"A\", \"B\", \"C\", 2)")).isEqualTo("B");
        }
    }
```

- [ ] **Step 3: Run tests and commit**

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
