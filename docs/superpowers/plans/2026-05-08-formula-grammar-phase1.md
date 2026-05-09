# Formula Grammar Phase 1 — String, Math & Constants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the ANTLR4 `Formula.g4` grammar and both visitors to support 8 string functions, 7 math functions, and 4 constants.

**Architecture:** Add new lexer tokens and parser rules to `Formula.g4`, then regenerate ANTLR classes via Maven. Extend both `BigDecimalFormulaVisitor` (numeric math + constants, zero for strings) and `ObjectFormulaVisitor` (full string + math + constants support). Add comprehensive tests to both visitor test classes.

**Tech Stack:** Java 23, ANTLR4 4.13.1, JUnit 5, Maven

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `Formula.g4` | Modify | Grammar — add 19 new tokens + parser rules |
| `BigDecimalFormulaVisitor.java` | Modify | Numeric visitor — math functions + constants |
| `ObjectFormulaVisitor.java` | Modify | Object visitor — full string + math + constants |
| `BigDecimalFormulaVisitorTest.java` | Modify | Tests for math functions and constants |
| `ObjectFormulaVisitorTest.java` | Modify | Tests for string functions, math, and constants |

---

### Task 1: Extend ANTLR4 Grammar

**Files:**
- Modify: `backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4`

- [ ] **Step 1: Add new lexer tokens after CONCAT**

Insert after line 81 (`CONCAT`):

```antlr
// String functions
LEFT       : [Ll][Ee][Ff][Tt] ;
RIGHT      : [Rr][Ii][Gg][Hh][Tt] ;
MID        : [Mm][Ii][Dd] ;
LENGTH     : [Ll][Ee][Nn][Gg][Tt][Hh] ;
UPPER      : [Uu][Pp][Pp][Ee][Rr] ;
LOWER      : [Ll][Oo][Ww][Ee][Rr] ;
TRIM       : [Tt][Rr][Ii][Mm] ;
SUBSTITUTE : [Ss][Uu][Bb][Ss][Tt][Ii][Tt][Uu][Tt][Ee] ;

// Math functions
MOD        : [Mm][Oo][Dd] ;
FLOOR      : [Ff][Ll][Oo][Oo][Rr] ;
CEILING    : [Cc][Ee][Ii][Ll][Ii][Nn][Gg] ;
LOG        : [Ll][Oo][Gg] ;
EXP        : [Ee][Xx][Pp] ;
SIN        : [Ss][Ii][Nn] ;
COS        : [Cc][Oo][Ss] ;

// Constants
PI         : [Pp][Ii] ;
EULER      : [Ee] ;
TRUE       : [Tt][Rr][Uu][Ee] ;
FALSE      : [Ff][Aa][Ll][Ss][Ee] ;
```

- [ ] **Step 2: Add new function call alternatives**

In `functionCall` rule (after `CONCAT` line), add:

```antlr
    | LEFT LPAREN expression COMMA expression RPAREN
    | RIGHT LPAREN expression COMMA expression RPAREN
    | MID LPAREN expression COMMA expression COMMA expression RPAREN
    | LENGTH LPAREN expression RPAREN
    | UPPER LPAREN expression RPAREN
    | LOWER LPAREN expression RPAREN
    | TRIM LPAREN expression RPAREN
    | SUBSTITUTE LPAREN expression COMMA expression COMMA expression RPAREN
    | MOD LPAREN expression COMMA expression RPAREN
    | FLOOR LPAREN expression RPAREN
    | CEILING LPAREN expression RPAREN
    | LOG LPAREN expression RPAREN
    | EXP LPAREN expression RPAREN
    | SIN LPAREN expression RPAREN
    | COS LPAREN expression RPAREN
```

- [ ] **Step 3: Add constants to primary rule**

In `primary` rule, add:

```antlr
    | PI
    | EULER
    | TRUE
    | FALSE
```

- [ ] **Step 4: Update keyword-safe variable references**

Update `variableRef` to include all new keywords:

```antlr
variableRef
    : DOLLAR (IDENTIFIER | IF | MAX | MIN | ABS | ROUND | POWER | SQRT | SUM | CONCAT | AND | OR | NOT | LEFT | RIGHT | MID | LENGTH | UPPER | LOWER | TRIM | SUBSTITUTE | MOD | FLOOR | CEILING | LOG | EXP | SIN | COS | PI | EULER | TRUE | FALSE)
    ;
```

Update `bracketRef` similarly:

```antlr
bracketRef
    : LBRACKET (IDENTIFIER | IF | MAX | MIN | ABS | ROUND | POWER | SQRT | SUM | CONCAT | AND | OR | NOT | LEFT | RIGHT | MID | LENGTH | UPPER | LOWER | TRIM | SUBSTITUTE | MOD | FLOOR | CEILING | LOG | EXP | SIN | COS | PI | EULER | TRUE | FALSE) RBRACKET
    ;
```

- [ ] **Step 5: Regenerate ANTLR classes**

Run:

```bash
mvn -pl bipros-udf generate-sources
```

Expected: BUILD SUCCESS with ANTLR4 processing the grammar.

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4
git commit -m "feat(udf): extend Formula.g4 with string, math functions and constants"
```

---

### Task 2: Extend BigDecimalFormulaVisitor — Math & Constants

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java:206-264`

- [ ] **Step 1: Add MOD handler in visitFunctionCall**

After `SUM` block, before `return BigDecimal.ZERO;`:

```java
        if (ctx.MOD() != null) {
            if (ctx.expression().size() < 2) {
                return BigDecimal.ZERO;
            }
            BigDecimal dividend = visit(ctx.expression(0));
            BigDecimal divisor = visit(ctx.expression(1));
            if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                return zeroDefault;
            }
            return dividend.remainder(divisor);
        }
        if (ctx.FLOOR() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return visit(ctx.expression(0)).setScale(0, RoundingMode.FLOOR);
        }
        if (ctx.CEILING() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return visit(ctx.expression(0)).setScale(0, RoundingMode.CEILING);
        }
        if (ctx.LOG() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(Math.log(visit(ctx.expression(0)).doubleValue()))
                    .setScale(scale, roundingMode);
        }
        if (ctx.EXP() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(Math.exp(visit(ctx.expression(0)).doubleValue()))
                    .setScale(scale, roundingMode);
        }
        if (ctx.SIN() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(Math.sin(visit(ctx.expression(0)).doubleValue()))
                    .setScale(scale, roundingMode);
        }
        if (ctx.COS() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(Math.cos(visit(ctx.expression(0)).doubleValue()))
                    .setScale(scale, roundingMode);
        }
```

- [ ] **Step 2: Add constant handlers in visitPrimary**

After `numberLiteral` check, before `return BigDecimal.ZERO;`:

```java
        if (ctx.PI() != null) {
            return BigDecimal.valueOf(Math.PI);
        }
        if (ctx.EULER() != null) {
            return BigDecimal.valueOf(Math.E);
        }
        if (ctx.TRUE() != null) {
            return BigDecimal.ONE;
        }
        if (ctx.FALSE() != null) {
            return BigDecimal.ZERO;
        }
```

Note: String functions (`LEFT`, `RIGHT`, `MID`, `LENGTH`, `UPPER`, `LOWER`, `TRIM`, `SUBSTITUTE`) will fall through to `return BigDecimal.ZERO` at the end of `visitFunctionCall` because they have no handler — this is the intended backward-compatible behavior.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java
git commit -m "feat(udf): add math functions and constants to BigDecimalFormulaVisitor"
```

---

### Task 3: Extend ObjectFormulaVisitor — Full String, Math & Constants

**Files:**
- Modify: `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java:184-246`

- [ ] **Step 1: Add string function handlers in visitFunctionCall**

After `CONCAT` block, before `return 0;`:

```java
        if (ctx.LEFT() != null) {
            if (ctx.expression().size() < 2) return "";
            String text = String.valueOf(visit(ctx.expression(0)));
            int n = (int) toDouble(visit(ctx.expression(1)));
            if (n <= 0) return "";
            return text.substring(0, Math.min(n, text.length()));
        }
        if (ctx.RIGHT() != null) {
            if (ctx.expression().size() < 2) return "";
            String text = String.valueOf(visit(ctx.expression(0)));
            int n = (int) toDouble(visit(ctx.expression(1)));
            if (n <= 0) return "";
            return text.substring(Math.max(0, text.length() - n));
        }
        if (ctx.MID() != null) {
            if (ctx.expression().size() < 3) return "";
            String text = String.valueOf(visit(ctx.expression(0)));
            int start = (int) toDouble(visit(ctx.expression(1))) - 1; // 1-based to 0-based
            int len = (int) toDouble(visit(ctx.expression(2)));
            if (start < 0) start = 0;
            if (start >= text.length() || len <= 0) return "";
            return text.substring(start, Math.min(start + len, text.length()));
        }
        if (ctx.LENGTH() != null) {
            if (ctx.expression().isEmpty()) return 0L;
            return (long) String.valueOf(visit(ctx.expression(0))).length();
        }
        if (ctx.UPPER() != null) {
            if (ctx.expression().isEmpty()) return "";
            return String.valueOf(visit(ctx.expression(0))).toUpperCase();
        }
        if (ctx.LOWER() != null) {
            if (ctx.expression().isEmpty()) return "";
            return String.valueOf(visit(ctx.expression(0))).toLowerCase();
        }
        if (ctx.TRIM() != null) {
            if (ctx.expression().isEmpty()) return "";
            return String.valueOf(visit(ctx.expression(0))).trim();
        }
        if (ctx.SUBSTITUTE() != null) {
            if (ctx.expression().size() < 3) return "";
            String text = String.valueOf(visit(ctx.expression(0)));
            String oldStr = String.valueOf(visit(ctx.expression(1)));
            String newStr = String.valueOf(visit(ctx.expression(2)));
            return text.replace(oldStr, newStr);
        }
```

- [ ] **Step 2: Add math function handlers**

After string functions, add:

```java
        if (ctx.MOD() != null) {
            if (ctx.expression().size() < 2) return 0.0;
            double dividend = toDouble(visit(ctx.expression(0)));
            double divisor = toDouble(visit(ctx.expression(1)));
            if (divisor == 0) return 0.0;
            return dividend % divisor;
        }
        if (ctx.FLOOR() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            return Math.floor(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.CEILING() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            return Math.ceil(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.LOG() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            return Math.log(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.EXP() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            return Math.exp(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.SIN() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            return Math.sin(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.COS() != null) {
            if (ctx.expression().isEmpty()) return 0.0;
            return Math.cos(toDouble(visit(ctx.expression(0))));
        }
```

- [ ] **Step 3: Add constant handlers in visitPrimary**

After `numberLiteral` check, before `return 0;`:

```java
        if (ctx.PI() != null) {
            return Math.PI;
        }
        if (ctx.EULER() != null) {
            return Math.E;
        }
        if (ctx.TRUE() != null) {
            return Boolean.TRUE;
        }
        if (ctx.FALSE() != null) {
            return Boolean.FALSE;
        }
```

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java
git commit -m "feat(udf): add string functions, math functions, and constants to ObjectFormulaVisitor"
```

---

### Task 4: Write Tests for BigDecimalFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java`

- [ ] **Step 1: Add MathFunctions nested test class**

Append to the test file:

```java
    @Nested
    @DisplayName("Math Functions")
    class MathFunctionsTests {

        @Test
        @DisplayName("MOD(17, 5) = 2")
        void mod() {
            assertThat(eval("MOD(17, 5)", emptyMap()))
                    .isEqualByComparingTo(bd(2));
        }

        @Test
        @DisplayName("MOD(10, 0) returns zero default")
        void modByZero() {
            assertThat(eval("MOD(10, 0)", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("FLOOR(3.7) = 3")
        void floor() {
            assertThat(eval("FLOOR(3.7)", emptyMap()))
                    .isEqualByComparingTo(bd(3));
        }

        @Test
        @DisplayName("FLOOR(-3.7) = -4")
        void floorNegative() {
            assertThat(eval("FLOOR(-3.7)", emptyMap()))
                    .isEqualByComparingTo(bd(-4));
        }

        @Test
        @DisplayName("CEILING(3.2) = 4")
        void ceiling() {
            assertThat(eval("CEILING(3.2)", emptyMap()))
                    .isEqualByComparingTo(bd(4));
        }

        @Test
        @DisplayName("CEILING(-3.2) = -3")
        void ceilingNegative() {
            assertThat(eval("CEILING(-3.2)", emptyMap()))
                    .isEqualByComparingTo(bd(-3));
        }

        @Test
        @DisplayName("LOG(1) = 0")
        void log() {
            assertThat(eval("LOG(1)", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("EXP(0) = 1")
        void exp() {
            assertThat(eval("EXP(0)", emptyMap()))
                    .isEqualByComparingTo(bd(1));
        }

        @Test
        @DisplayName("SIN(0) = 0")
        void sin() {
            assertThat(eval("SIN(0)", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("COS(0) = 1")
        void cos() {
            assertThat(eval("COS(0)", emptyMap()))
                    .isEqualByComparingTo(bd(1));
        }
    }
```

- [ ] **Step 2: Add Constants nested test class**

Append:

```java
    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("PI ≈ 3.14159...")
        void pi() {
            assertThat(eval("PI", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.valueOf(Math.PI));
        }

        @Test
        @DisplayName("E ≈ 2.71828...")
        void euler() {
            assertThat(eval("E", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.valueOf(Math.E));
        }

        @Test
        @DisplayName("TRUE = 1")
        void trueConstant() {
            assertThat(eval("TRUE", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("FALSE = 0")
        void falseConstant() {
            assertThat(eval("FALSE", emptyMap()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("IF(TRUE, 10, 20) = 10")
        void ifWithTrue() {
            assertThat(eval("IF(TRUE, 10, 20)", emptyMap()))
                    .isEqualByComparingTo(bd(10));
        }

        @Test
        @DisplayName("IF(FALSE, 10, 20) = 20")
        void ifWithFalse() {
            assertThat(eval("IF(FALSE, 10, 20)", emptyMap()))
                    .isEqualByComparingTo(bd(20));
        }
    }
```

- [ ] **Step 3: Add StringFunctionsZeroTest**

Append:

```java
    @Nested
    @DisplayName("String Functions in Numeric Context")
    class StringFunctionsZeroTests {

        @Test
        @DisplayName("String functions return 0 in BigDecimal context")
        void stringFunctionsReturnZero() {
            assertThat(eval("LEFT(\"hello\", 2)", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("RIGHT(\"hello\", 2)", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("MID(\"hello\", 2, 2)", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("LENGTH(\"hello\")", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("UPPER(\"hello\")", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("LOWER(\"HELLO\")", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("TRIM(\" hello \")", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(eval("SUBSTITUTE(\"a\", \"a\", \"b\")", emptyMap())).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl bipros-udf -Dtest=BigDecimalFormulaVisitorTest -q
```

Expected: All tests pass (existing + new).

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java
git commit -m "test(udf): add tests for math functions and constants in BigDecimalFormulaVisitor"
```

---

### Task 5: Write Tests for ObjectFormulaVisitor

**Files:**
- Modify: `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java`

- [ ] **Step 1: Add StringFunctions nested test class**

Append:

```java
    @Nested
    @DisplayName("String Functions")
    class StringFunctionsTests {

        @Test
        @DisplayName("LEFT(\"hello\", 2) = \"he\"")
        void left() {
            assertThat(eval("LEFT(\"hello\", 2)", emptyMap())).isEqualTo("he");
        }

        @Test
        @DisplayName("LEFT(\"hi\", 10) = \"hi\"")
        void leftOverflow() {
            assertThat(eval("LEFT(\"hi\", 10)", emptyMap())).isEqualTo("hi");
        }

        @Test
        @DisplayName("RIGHT(\"hello\", 2) = \"lo\"")
        void right() {
            assertThat(eval("RIGHT(\"hello\", 2)", emptyMap())).isEqualTo("lo");
        }

        @Test
        @DisplayName("RIGHT(\"hi\", 10) = \"hi\"")
        void rightOverflow() {
            assertThat(eval("RIGHT(\"hi\", 10)", emptyMap())).isEqualTo("hi");
        }

        @Test
        @DisplayName("MID(\"hello\", 2, 2) = \"el\"")
        void mid() {
            assertThat(eval("MID(\"hello\", 2, 2)", emptyMap())).isEqualTo("el");
        }

        @Test
        @DisplayName("MID(\"hi\", 5, 2) = \"\"")
        void midOutOfRange() {
            assertThat(eval("MID(\"hi\", 5, 2)", emptyMap())).isEqualTo("");
        }

        @Test
        @DisplayName("LENGTH(\"hello\") = 5")
        void length() {
            assertThat(eval("LENGTH(\"hello\")", emptyMap())).isEqualTo(5L);
        }

        @Test
        @DisplayName("UPPER(\"hello\") = \"HELLO\"")
        void upper() {
            assertThat(eval("UPPER(\"hello\")", emptyMap())).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("LOWER(\"HELLO\") = \"hello\"")
        void lower() {
            assertThat(eval("LOWER(\"HELLO\")", emptyMap())).isEqualTo("hello");
        }

        @Test
        @DisplayName("TRIM(\"  hello  \") = \"hello\"")
        void trim() {
            assertThat(eval("TRIM(\"  hello  \")", emptyMap())).isEqualTo("hello");
        }

        @Test
        @DisplayName("SUBSTITUTE(\"a,b,c\", \",\", \"-\") = \"a-b-c\"")
        void substitute() {
            assertThat(eval("SUBSTITUTE(\"a,b,c\", \",\", \"-\")", emptyMap())).isEqualTo("a-b-c");
        }

        @Test
        @DisplayName("SUBSTITUTE(\"abc\", \"x\", \"y\") = \"abc\"")
        void substituteNotFound() {
            assertThat(eval("SUBSTITUTE(\"abc\", \"x\", \"y\")", emptyMap())).isEqualTo("abc");
        }
    }
```

- [ ] **Step 2: Add MathFunctions nested test class**

Append:

```java
    @Nested
    @DisplayName("Math Functions")
    class MathFunctionsTests {

        @Test
        @DisplayName("MOD(17, 5) = 2.0")
        void mod() {
            assertThat(eval("MOD(17, 5)", emptyMap())).isEqualTo(2.0);
        }

        @Test
        @DisplayName("MOD(10, 0) = 0.0")
        void modByZero() {
            assertThat(eval("MOD(10, 0)", emptyMap())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("FLOOR(3.7) = 3.0")
        void floor() {
            assertThat(eval("FLOOR(3.7)", emptyMap())).isEqualTo(3.0);
        }

        @Test
        @DisplayName("CEILING(3.2) = 4.0")
        void ceiling() {
            assertThat(eval("CEILING(3.2)", emptyMap())).isEqualTo(4.0);
        }

        @Test
        @DisplayName("LOG(1) = 0.0")
        void log() {
            assertThat(eval("LOG(1)", emptyMap())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("EXP(0) = 1.0")
        void exp() {
            assertThat(eval("EXP(0)", emptyMap())).isEqualTo(1.0);
        }

        @Test
        @DisplayName("SIN(0) = 0.0")
        void sin() {
            assertThat(eval("SIN(0)", emptyMap())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("COS(0) = 1.0")
        void cos() {
            assertThat(eval("COS(0)", emptyMap())).isEqualTo(1.0);
        }
    }
```

- [ ] **Step 3: Add Constants nested test class**

Append:

```java
    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("PI = Math.PI")
        void pi() {
            assertThat(eval("PI", emptyMap())).isEqualTo(Math.PI);
        }

        @Test
        @DisplayName("E = Math.E")
        void euler() {
            assertThat(eval("E", emptyMap())).isEqualTo(Math.E);
        }

        @Test
        @DisplayName("TRUE = Boolean.TRUE")
        void trueConstant() {
            assertThat(eval("TRUE", emptyMap())).isEqualTo(Boolean.TRUE);
        }

        @Test
        @DisplayName("FALSE = Boolean.FALSE")
        void falseConstant() {
            assertThat(eval("FALSE", emptyMap())).isEqualTo(Boolean.FALSE);
        }

        @Test
        @DisplayName("IF(TRUE, \"yes\", \"no\") = \"yes\"")
        void ifWithTrue() {
            assertThat(eval("IF(TRUE, \"yes\", \"no\")", emptyMap())).isEqualTo("yes");
        }

        @Test
        @DisplayName("IF(FALSE, \"yes\", \"no\") = \"no\"")
        void ifWithFalse() {
            assertThat(eval("IF(FALSE, \"yes\", \"no\")", emptyMap())).isEqualTo("no");
        }
    }
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl bipros-udf -Dtest=ObjectFormulaVisitorTest -q
```

Expected: All tests pass (existing + new).

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java
git commit -m "test(udf): add tests for string functions, math, and constants in ObjectFormulaVisitor"
```

---

### Task 6: Regression Test Full Module

**Files:**
- (no file changes)

- [ ] **Step 1: Run all bipros-udf tests**

```bash
mvn test -pl bipros-udf -q
```

Expected: All tests pass (should be ~280+ tests including the new ones).

- [ ] **Step 2: Verify no compilation errors in dependent modules**

```bash
mvn compile -pl bipros-api -am -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit (if clean)**

```bash
git status
```

If working tree is clean (all changes committed), no action needed. If any uncommitted changes remain, commit them.

---

## Spec Coverage Checklist

- [x] Grammar: all 19 new tokens added
- [x] Grammar: all new parser rules added
- [x] Grammar: keyword-safe variables updated
- [x] BigDecimalFormulaVisitor: MOD, FLOOR, CEILING, LOG, EXP, SIN, COS
- [x] BigDecimalFormulaVisitor: PI, E, TRUE, FALSE
- [x] BigDecimalFormulaVisitor: string functions return 0 (implicit)
- [x] ObjectFormulaVisitor: LEFT, RIGHT, MID, LENGTH, UPPER, LOWER, TRIM, SUBSTITUTE
- [x] ObjectFormulaVisitor: MOD, FLOOR, CEILING, LOG, EXP, SIN, COS
- [x] ObjectFormulaVisitor: PI, E, TRUE, FALSE
- [x] Tests: BigDecimal math functions
- [x] Tests: BigDecimal constants
- [x] Tests: BigDecimal string functions return 0
- [x] Tests: Object string functions
- [x] Tests: Object math functions
- [x] Tests: Object constants
- [x] Regression: full module test suite
- [x] Regression: dependent module compilation

---

## Execution Options

**Plan saved to:** `docs/superpowers/plans/2026-05-08-formula-grammar-phase1.md`

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — I execute tasks in this session using batch execution with checkpoints for review.

Please indicate which execution mode you prefer.
