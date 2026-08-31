# Extended Formula Grammar — Phase 1: String, Math & Constants

> **Scope:** String functions, mathematical functions, and boolean/numeric constants
> **Date:** 2026-05-08
> **Module:** `bipros-udf`

---

## 1. Goal

Extend the ANTLR4 `Formula.g4` grammar and both visitors (`BigDecimalFormulaVisitor`, `ObjectFormulaVisitor`) to support:

- **String functions:** `LEFT`, `RIGHT`, `MID`, `LENGTH`, `UPPER`, `LOWER`, `TRIM`, `SUBSTITUTE`
- **Mathematical functions:** `MOD`, `FLOOR`, `CEILING`, `LOG`, `EXP`, `SIN`, `COS`
- **Constants:** `PI`, `E`, `TRUE`, `FALSE`

This is the first of four phases. It requires no external dependencies and operates entirely on scalar values.

---

## 2. Grammar Extensions

### 2.1 New Lexer Tokens

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

### 2.2 Grammar Rules

**String functions:**
```antlr
| LEFT LPAREN expression COMMA expression RPAREN
| RIGHT LPAREN expression COMMA expression RPAREN
| MID LPAREN expression COMMA expression COMMA expression RPAREN
| LENGTH LPAREN expression RPAREN
| UPPER LPAREN expression RPAREN
| LOWER LPAREN expression RPAREN
| TRIM LPAREN expression RPAREN
| SUBSTITUTE LPAREN expression COMMA expression COMMA expression RPAREN
```

**Math functions:**
```antlr
| MOD LPAREN expression COMMA expression RPAREN
| FLOOR LPAREN expression RPAREN
| CEILING LPAREN expression RPAREN
| LOG LPAREN expression RPAREN
| EXP LPAREN expression RPAREN
| SIN LPAREN expression RPAREN
| COS LPAREN expression RPAREN
```

**Constants added to `primary`:**
```antlr
| PI
| EULER
| TRUE
| FALSE
```

### 2.3 Keyword-Safe Variables

All new keywords (`LEFT`, `RIGHT`, `MID`, `LENGTH`, `UPPER`, `LOWER`, `TRIM`, `SUBSTITUTE`, `MOD`, `FLOOR`, `CEILING`, `LOG`, `EXP`, `SIN`, `COS`, `PI`, `EULER`, `TRUE`, `FALSE`) must be added to `variableRef` and `bracketRef` alternative token lists so users can reference `$LENGTH`, `$MOD`, etc.

---

## 3. Visitor Implementations

### 3.1 BigDecimalFormulaVisitor

**String functions** — return `BigDecimal.ZERO` (string results cannot be represented as BigDecimal). This preserves backward compatibility: formulas that use string functions in a numeric context will behave as if the function returned zero.

**Math functions:**
- `MOD(a, b)` — `a.remainder(b)` with zero-divisor guard
- `FLOOR(a)` — `a.setScale(0, RoundingMode.FLOOR)`
- `CEILING(a)` — `a.setScale(0, RoundingMode.CEILING)`
- `LOG(a)` — `BigDecimal.valueOf(Math.log(a.doubleValue()))`
- `EXP(a)` — `BigDecimal.valueOf(Math.exp(a.doubleValue()))`
- `SIN(a)` — `BigDecimal.valueOf(Math.sin(a.doubleValue()))`
- `COS(a)` — `BigDecimal.valueOf(Math.cos(a.doubleValue()))`

**Constants:**
- `PI` — `BigDecimal.valueOf(Math.PI)`
- `E` — `BigDecimal.valueOf(Math.E)`
- `TRUE` — `BigDecimal.ONE`
- `FALSE` — `BigDecimal.ZERO`

### 3.2 ObjectFormulaVisitor

**String functions:**
- `LEFT(text, n)` — `text.substring(0, min(n, length))`
- `RIGHT(text, n)` — `text.substring(max(0, length-n))`
- `MID(text, start, len)` — `text.substring(start-1, start-1+len)` (1-based, Excel-style)
- `LENGTH(text)` — string length as Long
- `UPPER(text)` — `text.toUpperCase()`
- `LOWER(text)` — `text.toLowerCase()`
- `TRIM(text)` — `text.trim()`
- `SUBSTITUTE(text, old, new)` — `text.replace(old, new)` (all occurrences)

**Math functions:**
- Same calculations as BigDecimal visitor but return Double

**Constants:**
- `PI` — `Math.PI`
- `E` — `Math.E`
- `TRUE` — `Boolean.TRUE`
- `FALSE` — `Boolean.FALSE`

---

## 4. Testing Strategy

### 4.1 Grammar Parsing Tests
For each new function/constant, verify the grammar produces a valid parse tree:
- `LEFT("hello", 2)`
- `RIGHT("hello", 2)`
- `MID("hello", 2, 2)`
- `LENGTH("hello")`
- `UPPER("hello")`
- `LOWER("HELLO")`
- `TRIM("  hello  ")`
- `SUBSTITUTE("hello world", "world", "java")`
- `MOD(17, 5)`
- `FLOOR(3.7)`
- `CEILING(3.2)`
- `LOG(100)`
- `EXP(1)`
- `SIN(0)`
- `COS(0)`
- `PI * 2`
- `E + 1`
- `IF(TRUE, 1, 0)`
- `IF(FALSE, 1, 0)`

### 4.2 BigDecimalFormulaVisitor Tests
For each math function and constant, verify numeric accuracy:
- `MOD(17, 5) = 2`
- `FLOOR(3.7) = 3`
- `CEILING(3.2) = 4`
- `LOG(1) = 0`
- `EXP(0) = 1`
- `SIN(0) = 0`
- `COS(0) = 1`
- `PI = 3.1416...`
- `TRUE = 1`
- `FALSE = 0`
- String functions all return `0`

### 4.3 ObjectFormulaVisitor Tests
For each function, verify correct type and value:
- `LEFT("hello", 2) = "he"`
- `RIGHT("hello", 2) = "lo"`
- `MID("hello", 2, 2) = "el"`
- `LENGTH("hello") = 5L`
- `UPPER("hello") = "HELLO"`
- `LOWER("HELLO") = "hello"`
- `TRIM("  hello  ") = "hello"`
- `SUBSTITUTE("a,b,c", ",", "-") = "a-b-c"`
- Math functions return Double
- Constants return correct types

### 4.4 Edge Cases
- `LEFT("hi", 10)` — n > length
- `RIGHT("hi", 10)` — n > length
- `MID("hi", 5, 2)` — start > length
- `SUBSTITUTE("abc", "x", "y")` — old not found
- `MOD(10, 0)` — division by zero guard
- `FLOOR(-3.7)` — negative numbers
- `CEILING(-3.2)` — negative numbers
- Keyword-named variables: `$LENGTH`, `$MOD`, `$TRUE`

---

## 5. Backward Compatibility

All existing formulas continue to work unchanged. The grammar only adds new alternatives — no existing rules are modified. The `FormulaAstCache` remains valid for all existing expressions.

---

## 6. Files to Modify

1. `backend/bipros-udf/src/main/antlr4/com/bipros/udf/domain/engine/Formula.g4` — add tokens and rules
2. `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitor.java` — implement math functions and constants
3. `backend/bipros-udf/src/main/java/com/bipros/udf/domain/engine/ObjectFormulaVisitor.java` — implement all new functions and constants
4. `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/BigDecimalFormulaVisitorTest.java` — add math/constant tests
5. `backend/bipros-udf/src/test/java/com/bipros/udf/domain/engine/ObjectFormulaVisitorTest.java` — add string/math/constant tests

---

## 7. Future Phases

| Phase | Scope | Key Complexity |
|-------|-------|----------------|
| **2** | Statistical: `AVERAGE`, `MEDIAN`, `COUNT`, `STDEV`, `PERCENTILE` | Collection/array input handling |
| **3** | Conditional aggregation: `SUMIF`, `COUNTIF`, `AVERAGEIF` | Predicate evaluation per element |
| **4** | Date/Time + Lookup: `TODAY`, `DATEDIFF`, `LOOKUP`, `INDEX` | Timezone, locale, data layer integration |
