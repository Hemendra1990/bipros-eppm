# Extended Formula Grammar — Phase 3: Conditional Aggregation Functions

> **Scope:** SUMIF, COUNTIF, AVERAGEIF — equality-based conditional aggregation
> **Date:** 2026-05-08
> **Module:** `bipros-udf`
> **Depends on:** Phase 1, Phase 2

---

## 1. Goal

Add three conditional aggregation functions that operate on varargs with equality matching:

- **SUMIF(criteria, val1, val2, ...)** — sums all values equal to criteria
- **COUNTIF(criteria, val1, val2, ...)** — counts all values equal to criteria
- **AVERAGEIF(criteria, val1, val2, ...)** — averages all values equal to criteria

The first argument is the criteria value. Remaining arguments are tested for equality against the criteria. Values that match are included in the aggregation.

---

## 2. Grammar Extensions

### 2.1 New Lexer Tokens

```antlr
SUMIF     : [Ss][Uu][Mm][Ii][Ff] ;
COUNTIF   : [Cc][Oo][Uu][Nn][Tt][Ii][Ff] ;
AVERAGEIF : [Aa][Vv][Ee][Rr][Aa][Gg][Ee][Ii][Ff] ;
```

### 2.2 Grammar Rules

```antlr
| SUMIF LPAREN expression (COMMA expression)* RPAREN
| COUNTIF LPAREN expression (COMMA expression)* RPAREN
| AVERAGEIF LPAREN expression (COMMA expression)* RPAREN
```

Requires at least 2 arguments (criteria + at least one value to test).

### 2.3 Keyword-Safe Variables

Add `SUMIF`, `COUNTIF`, `AVERAGEIF` to `variableRef` and `bracketRef`.

---

## 3. Visitor Implementations

### 3.1 BigDecimalFormulaVisitor

**SUMIF(criteria, val1, val2, ...)**
1. Evaluate criteria (first expression)
2. For each remaining expression, evaluate and compare to criteria using `compareTo == 0`
3. Sum all matching values
4. Return `zeroDefault` if fewer than 2 arguments

**COUNTIF(criteria, val1, val2, ...)**
1. Evaluate criteria
2. Count how many remaining values match criteria (`compareTo == 0`)
3. Return `0` if fewer than 2 arguments

**AVERAGEIF(criteria, val1, val2, ...)**
1. Evaluate criteria
2. Collect all matching values
3. Return average of matches, or `zeroDefault` if no matches or fewer than 2 arguments

### 3.2 ObjectFormulaVisitor

Same logic but using `compareEquals()` (case-insensitive string equality). Returns `Double` for numeric results.

---

## 4. Testing Strategy

### BigDecimalFormulaVisitor
- `SUMIF(5, 5, 3, 5, 2)` = `10` (5 + 5)
- `SUMIF(5, 1, 2, 3)` = `0` (no matches)
- `COUNTIF(5, 5, 3, 5, 2)` = `2`
- `COUNTIF(5, 1, 2, 3)` = `0`
- `AVERAGEIF(10, 10, 20, 10)` = `10`
- `AVERAGEIF(5, 1, 2, 3)` = `0` (zero default, no matches)

### ObjectFormulaVisitor
- `SUMIF("A", "A", "B", "A")` = `"A" + "A"` = but string + string... actually strings in numeric context would be 0 in BigDecimal. In Object visitor, SUMIF with strings should probably just not work... 

Actually, let me reconsider. In ObjectFormulaVisitor, SUMIF should sum numeric values only. If a value is a string, `toDouble()` will convert it, returning 0 for non-numeric strings. So `SUMIF("A", "A", "B", "A")` would match "A" twice and sum `0 + 0 = 0`.

For ObjectFormulaVisitor, the matching uses `compareEquals()` which does case-insensitive string comparison. So:
- `COUNTIF("A", "A", "B", "a")` = `2` ("A" and "a" match)
- `SUMIF(5, 5, 3, 5, 2)` = `10.0`

### Edge Cases
- Single value argument: `SUMIF(5, 5)` = `5`
- No value arguments: `SUMIF(5)` → parse error (grammar requires at least 2 args via `(COMMA expression)+`)
- Criteria is a variable: `SUMIF($threshold, $a, $b, $c)`

---

## 5. Files to Modify

1. `Formula.g4` — add 3 tokens + parser rules
2. `BigDecimalFormulaVisitor.java` — implement 3 functions
3. `ObjectFormulaVisitor.java` — implement 3 functions
4. `BigDecimalFormulaVisitorTest.java` — add conditional aggregation tests
5. `ObjectFormulaVisitorTest.java` — add conditional aggregation tests

---

## 6. Grammar Note

All conditional aggregation functions require at least 2 arguments (criteria + one value). The grammar rule `(COMMA expression)*` allows zero additional args, but the visitor will handle this by returning the zero default. However, to be consistent with the requirement, we could use `(COMMA expression)+` to enforce at least one value argument at the parser level. Let's use `(COMMA expression)+` for stricter validation.
