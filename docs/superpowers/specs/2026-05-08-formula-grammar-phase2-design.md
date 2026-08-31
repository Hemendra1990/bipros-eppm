# Extended Formula Grammar — Phase 2: Statistical Functions

> **Scope:** Statistical functions operating on varargs (scalar arguments)
> **Date:** 2026-05-08
> **Module:** `bipros-udf`
> **Depends on:** Phase 1 (String, Math & Constants)

---

## 1. Goal

Extend the ANTLR4 grammar and both visitors to support statistical functions:

- **AVERAGE** — arithmetic mean of all arguments
- **COUNT** — number of arguments
- **STDEV** — population standard deviation
- **MEDIAN** — median value
- **PERCENTILE** — value at a given percentile rank

All functions operate on varargs (variable number of scalar arguments), consistent with the existing `MAX`, `MIN`, `SUM` patterns. Collection/array input support is deferred to a future enhancement.

---

## 2. Grammar Extensions

### 2.1 New Lexer Tokens

```antlr
AVERAGE    : [Aa][Vv][Ee][Rr][Aa][Gg][Ee] ;
COUNT      : [Cc][Oo][Uu][Nn][Tt] ;
STDEV      : [Ss][Tt][Dd][Ee][Vv] ;
MEDIAN     : [Mm][Ee][Dd][Ii][Aa][Nn] ;
PERCENTILE : [Pp][Ee][Rr][Cc][Ee][Nn][Tt][Ii][Ll][Ee] ;
```

### 2.2 Grammar Rules

```antlr
| AVERAGE LPAREN expression (COMMA expression)* RPAREN
| COUNT LPAREN expression (COMMA expression)* RPAREN
| STDEV LPAREN expression (COMMA expression)* RPAREN
| MEDIAN LPAREN expression (COMMA expression)* RPAREN
| PERCENTILE LPAREN expression (COMMA expression)* RPAREN
```

### 2.3 Keyword-Safe Variables

Add all new tokens to `variableRef` and `bracketRef` alternative lists.

---

## 3. Visitor Implementations

### 3.1 BigDecimalFormulaVisitor

**AVERAGE** — sum of all arguments divided by count. Returns `zeroDefault` if no arguments.

**COUNT** — number of arguments as `BigDecimal`. Returns `0` if no arguments.

**STDEV** — population standard deviation:
1. Compute mean (average)
2. For each value, compute `(value - mean)^2`
3. Average those squared differences
4. Take square root
Returns `zeroDefault` if fewer than 2 arguments.

**MEDIAN** — 
1. Sort all values
2. If odd count, middle value
3. If even count, average of two middle values
Returns `zeroDefault` if no arguments.

**PERCENTILE** — last argument is the percentile rank (0.0 to 1.0). All preceding arguments are the dataset.
1. Sort dataset
2. Compute index = rank * (N - 1)
3. If integer index, return that value
4. If fractional, linearly interpolate between floor and ceiling indices
Returns `zeroDefault` if fewer than 2 arguments (need at least 1 data point + rank).

### 3.2 ObjectFormulaVisitor

Same algorithms as BigDecimal visitor but returning `Double` instead of `BigDecimal`.

---

## 4. Testing Strategy

### 4.1 BigDecimalFormulaVisitor Tests

- `AVERAGE(10, 20, 30)` = `20`
- `AVERAGE(10)` = `10`
- `AVERAGE()` = `0` (zero default)
- `COUNT(10, 20, 30)` = `3`
- `COUNT()` = `0`
- `STDEV(2, 4, 4, 4, 5, 5, 7, 9)` = `2` (population std dev)
- `STDEV(10)` = `0` (single value)
- `STDEV()` = `0`
- `MEDIAN(10, 20, 30)` = `20`
- `MEDIAN(10, 20, 30, 40)` = `25` (average of 20 and 30)
- `MEDIAN()` = `0`
- `PERCENTILE(10, 20, 30, 40, 50, 0.5)` = `30` (median)
- `PERCENTILE(10, 20, 30, 0.0)` = `10` (0th percentile)
- `PERCENTILE(10, 20, 30, 1.0)` = `30` (100th percentile)
- `PERCENTILE(10, 20, 30, 0.25)` = `15` (25th percentile)
- `PERCENTILE(10)` = `0` (no rank)

### 4.2 ObjectFormulaVisitor Tests

Same test cases, asserting `Double` values.

### 4.3 Edge Cases

- All functions with single argument
- All functions with no arguments
- `STDEV` with identical values (should be 0)
- `PERCENTILE` with rank outside [0, 1] (clamp or return boundary)
- Keyword-named variables: `$AVERAGE`, `$COUNT`, `$STDEV`, `$MEDIAN`, `$PERCENTILE`

---

## 5. Backward Compatibility

All existing formulas continue to work. Only new alternatives added to grammar. No changes to existing visitor logic.

---

## 6. Files to Modify

1. `Formula.g4` — add 5 tokens + parser rules
2. `BigDecimalFormulaVisitor.java` — implement 5 functions
3. `ObjectFormulaVisitor.java` — implement 5 functions
4. `BigDecimalFormulaVisitorTest.java` — add statistical tests
5. `ObjectFormulaVisitorTest.java` — add statistical tests

---

## 7. Future Enhancements

- Collection/array input: `AVERAGE($arrayField)` where context value is a `List`
- Sample standard deviation (currently implementing population std dev)
- Weighted average: `AVERAGE.WEIGHTED(values, weights)`
