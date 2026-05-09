# Extended Formula Grammar — Phase 4: Date/Time Functions

> **Scope:** TODAY, DATEDIFF, DAYSOFMONTH, YEAR, MONTH, LOOKUP, INDEX
> **Date:** 2026-05-08
> **Module:** `bipros-udf`
> **Depends on:** Phase 1, 2, 3

---

## 1. Goal

Extend the ANTLR4 grammar and both visitors to support:

- **Date/Time functions:** `TODAY`, `DATEDIFF`, `DAYSOFMONTH`, `YEAR`, `MONTH`
- **Lookup/Reference functions:** `LOOKUP`, `INDEX`

---

## 2. Grammar Extensions

### 2.1 New Lexer Tokens

```antlr
TODAY      : [Tt][Oo][Dd][Aa][Yy] ;
DATEDIFF   : [Dd][Aa][Tt][Ee][Dd][Ii][Ff][Ff] ;
DAYSOFMONTH: [Dd][Aa][Yy][Ss][Oo][Ff][Mm][Oo][Nn][Tt][Hh] ;
YEAR       : [Yy][Ee][Aa][Rr] ;
MONTH      : [Mm][Oo][Nn][Tt][Hh] ;
LOOKUP     : [Ll][Oo][Oo][Kk][Uu][Pp] ;
INDEX      : [Ii][Nn][Dd][Ee][Xx] ;
```

### 2.2 Grammar Rules

```antlr
| TODAY LPAREN RPAREN
| DATEDIFF LPAREN expression COMMA expression RPAREN
| DAYSOFMONTH LPAREN expression RPAREN
| YEAR LPAREN expression RPAREN
| MONTH LPAREN expression RPAREN
| LOOKUP LPAREN expression (COMMA expression)+ RPAREN
| INDEX LPAREN expression (COMMA expression)+ RPAREN
```

### 2.3 Keyword-Safe Variables

Add all new tokens to `variableRef` and `bracketRef`.

---

## 3. Visitor Implementations

### 3.1 Date/Time Functions

**Date representation:** All dates are represented as **days since Unix epoch** (1970-01-01). This is a numeric representation compatible with BigDecimal arithmetic.

**TODAY()**
- Returns current date as days since epoch (system timezone)
- `TODAY()` → `BigDecimal` (BigDecimal visitor) / `Double` (Object visitor)

**DATEDIFF(start, end)**
- Computes `end - start` in days
- Both arguments can be numeric (days since epoch) or date strings (YYYY-MM-DD format)
- String parsing uses `java.time.LocalDate.parse()` with ISO format
- Returns `zeroDefault` if parsing fails or fewer than 2 arguments

**DAYSOFMONTH(date)**
- Takes a date (days since epoch or string)
- Returns number of days in that month (28-31)
- Returns `zeroDefault` if parsing fails

**YEAR(date)**
- Takes a date (days since epoch or string)
- Returns the year as a number (e.g., 2026)
- Returns `zeroDefault` if parsing fails

**MONTH(date)**
- Takes a date (days since epoch or string)
- Returns the month as 1-12
- Returns `zeroDefault` if parsing fails

### 3.2 Lookup/Reference Functions

**LOOKUP(criteria, lookup1, result1, lookup2, result2, ...)**
- Searches through key-value pairs (lookup_i, result_i)
- First argument is the criteria to match
- Remaining arguments are paired as (lookup, result)
- Returns the result value where lookup equals criteria
- Returns zero/empty if not found or odd number of value arguments
- Matching uses exact equality (BigDecimal `compareTo == 0`, Object `compareEquals()`)

**INDEX(value1, value2, ..., position)**
- Last argument is the 1-based position index
- Preceding arguments form the array
- Returns the value at the given position
- Returns zero/empty if position is out of bounds or fewer than 2 arguments

---

## 4. Testing Strategy

### BigDecimalFormulaVisitor
- `TODAY()` returns a positive BigDecimal (> 20000 since we're past 2024)
- `DATEDIFF("2024-01-01", "2024-01-10")` = `9`
- `DATEDIFF(19723, 19732)` = `9` (same dates as epoch days)
- `DAYSOFMONTH("2024-02-15")` = `29` (leap year)
- `DAYSOFMONTH("2023-02-15")` = `28`
- `DAYSOFMONTH("2024-04-15")` = `30`
- `YEAR("2024-06-15")` = `2024`
- `MONTH("2024-06-15")` = `6`
- `LOOKUP(5, 1, 10, 5, 50, 3, 30)` = `50`
- `LOOKUP(99, 1, 10, 5, 50)` = `0`
- `INDEX(10, 20, 30, 2)` = `20` (1-based index)
- `INDEX(10, 20, 30, 5)` = `0` (out of bounds)

### ObjectFormulaVisitor
Same test cases, returning appropriate types.

---

## 5. Files to Modify

1. `Formula.g4` — add 7 tokens + parser rules
2. `BigDecimalFormulaVisitor.java` — implement 7 functions
3. `ObjectFormulaVisitor.java` — implement 7 functions
4. `BigDecimalFormulaVisitorTest.java` — add date/lookup tests
5. `ObjectFormulaVisitorTest.java` — add date/lookup tests

---

## 6. Notes

- Date functions use `java.time.LocalDate` for parsing and calculations
- String dates must be in ISO format `YYYY-MM-DD`
- `TODAY()` uses system default timezone (no timezone parameter)
- `LOOKUP` expects an even number of value arguments (pairs). If odd, the last lookup has no result and is ignored.
- `INDEX` uses 1-based indexing (position 1 = first value), consistent with Excel
