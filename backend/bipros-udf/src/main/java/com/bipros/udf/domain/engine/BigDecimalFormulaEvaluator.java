package com.bipros.udf.domain.engine;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * @deprecated Use {@link BigDecimalFormulaVisitor} with {@link FormulaAstCache} instead.
 */
@Deprecated(since = "0.1.0-SNAPSHOT", forRemoval = true)
/**
 * BigDecimal-aware formula evaluator for financial and project-management calculations.
 * <p>
 * Supports:
 * <ul>
 *   <li>Arithmetic: {@code + - * /}</li>
 *   <li>Comparisons: {@code = != < > <= >=}</li>
 *   <li>Boolean: {@code AND OR NOT}</li>
 *   <li>Functions: {@code IF, MAX, MIN, ABS, ROUND, POWER, SQRT, SUM}</li>
 *   <li>Variable references: {@code $VAR} or {@code [FieldName]}</li>
 *   <li>Null-safe division with configurable zero-default</li>
 * </ul>
 *
 * <p>All numeric operations use {@link BigDecimal}. The result of {@link #evaluate()}
 * is a {@link FormulaResult} that carries the raw {@link BigDecimal} as well as a
 * formatted string representation.</p>
 */
@Slf4j
public class BigDecimalFormulaEvaluator {

    private static final int DEFAULT_SCALE = 10;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private final String expression;
    private final Map<String, BigDecimal> context;
    private final int scale;
    private final RoundingMode roundingMode;
    private final BigDecimal zeroDefault;

    private int pos;

    public BigDecimalFormulaEvaluator(String expression, Map<String, BigDecimal> context,
                                       int scale, RoundingMode roundingMode, BigDecimal zeroDefault) {
        this.expression = expression != null ? expression.trim() : "";
        this.context = context != null ? context : Collections.emptyMap();
        this.scale = scale;
        this.roundingMode = roundingMode;
        this.zeroDefault = zeroDefault != null ? zeroDefault : BigDecimal.ZERO;
        this.pos = 0;
    }

    public BigDecimalFormulaEvaluator(String expression, Map<String, BigDecimal> context) {
        this(expression, context, DEFAULT_SCALE, DEFAULT_ROUNDING, BigDecimal.ZERO);
    }

    /**
     * Evaluates the expression and returns a typed result.
     */
    public FormulaResult evaluate() {
        if (expression.isEmpty()) {
            return FormulaResult.empty();
        }
        try {
            BigDecimal result = parseExpression();
            return new FormulaResult(result, result.setScale(scale, roundingMode).toPlainString());
        } catch (Exception e) {
            log.warn("Error evaluating formula: {}", expression, e);
            return FormulaResult.error(e.getMessage());
        }
    }

    // ---- Recursive-descent parser ----

    private BigDecimal parseExpression() {
        return parseOr();
    }

    private BigDecimal parseOr() {
        BigDecimal left = parseAnd();
        while (pos < expression.length() && matchKeyword("OR")) {
            skipWhitespace();
            BigDecimal right = parseAnd();
            left = toBoolean(left) || toBoolean(right) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return left;
    }

    private BigDecimal parseAnd() {
        BigDecimal left = parseComparison();
        while (pos < expression.length() && matchKeyword("AND")) {
            skipWhitespace();
            BigDecimal right = parseComparison();
            left = toBoolean(left) && toBoolean(right) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return left;
    }

    private BigDecimal parseComparison() {
        BigDecimal left = parseAdditive();
        skipWhitespace();
        if (pos < expression.length()) {
            if (match("!=")) {
                BigDecimal right = parseAdditive();
                return left.compareTo(right) != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (match("=") || match("==")) {
                BigDecimal right = parseAdditive();
                return left.compareTo(right) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (match("<=")) {
                BigDecimal right = parseAdditive();
                return left.compareTo(right) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (match(">=")) {
                BigDecimal right = parseAdditive();
                return left.compareTo(right) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (match("<")) {
                BigDecimal right = parseAdditive();
                return left.compareTo(right) < 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            } else if (match(">")) {
                BigDecimal right = parseAdditive();
                return left.compareTo(right) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            }
        }
        return left;
    }

    private BigDecimal parseAdditive() {
        BigDecimal result = parseMultiplicative();
        while (pos < expression.length()) {
            skipWhitespace();
            if (peek() == '+') {
                pos++;
                BigDecimal right = parseMultiplicative();
                result = result.add(right);
            } else if (peek() == '-' && !isNegativeNumber()) {
                pos++;
                BigDecimal right = parseMultiplicative();
                result = result.subtract(right);
            } else {
                break;
            }
        }
        return result;
    }

    private BigDecimal parseMultiplicative() {
        BigDecimal result = parseUnary();
        while (pos < expression.length()) {
            skipWhitespace();
            if (peek() == '*') {
                pos++;
                BigDecimal right = parseUnary();
                result = result.multiply(right);
            } else if (peek() == '/') {
                pos++;
                BigDecimal right = parseUnary();
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    result = zeroDefault;
                } else {
                    result = result.divide(right, scale, roundingMode);
                }
            } else {
                break;
            }
        }
        return result;
    }

    private BigDecimal parseUnary() {
        skipWhitespace();
        if (peek() == '-') {
            pos++;
            return parseUnary().negate();
        } else if (peek() == '+') {
            pos++;
            return parseUnary();
        } else if (matchKeyword("NOT")) {
            skipWhitespace();
            return toBoolean(parseUnary()) ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        return parsePrimary();
    }

    private BigDecimal parsePrimary() {
        skipWhitespace();
        if (pos >= expression.length()) {
            return BigDecimal.ZERO;
        }
        if (matchKeyword("IF")) {
            return parseIf();
        } else if (matchKeyword("MAX")) {
            return parseMax();
        } else if (matchKeyword("MIN")) {
            return parseMin();
        } else if (matchKeyword("ABS")) {
            return parseAbs();
        } else if (matchKeyword("ROUND")) {
            return parseRound();
        } else if (matchKeyword("POWER")) {
            return parsePower();
        } else if (matchKeyword("SQRT")) {
            return parseSqrt();
        } else if (matchKeyword("SUM")) {
            return parseSum();
        } else if (peek() == '(') {
            pos++;
            BigDecimal result = parseExpression();
            skipWhitespace();
            if (peek() == ')') pos++;
            return result;
        } else if (peek() == '$') {
            return parseVariableReference();
        } else if (peek() == '[') {
            return parseBracketFieldReference();
        } else if (peek() == '"' || peek() == '\'') {
            // String literal — skip and return zero (formulas are numeric-only here)
            parseString();
            return BigDecimal.ZERO;
        } else {
            return parseNumber();
        }
    }

    // ---- Functions ----

    private BigDecimal parseIf() {
        expect('(');
        BigDecimal condition = parseExpression();
        expect(',');
        BigDecimal trueValue = parseExpression();
        expect(',');
        BigDecimal falseValue = parseExpression();
        expect(')');
        return toBoolean(condition) ? trueValue : falseValue;
    }

    private BigDecimal parseMax() {
        List<BigDecimal> args = parseArgList();
        return args.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal parseMin() {
        List<BigDecimal> args = parseArgList();
        return args.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal parseAbs() {
        expect('(');
        BigDecimal val = parseExpression();
        expect(')');
        return val.abs();
    }

    private BigDecimal parseRound() {
        expect('(');
        BigDecimal val = parseExpression();
        expect(',');
        BigDecimal places = parseExpression();
        expect(')');
        return val.setScale(places.intValue(), roundingMode);
    }

    private BigDecimal parsePower() {
        expect('(');
        BigDecimal base = parseExpression();
        expect(',');
        BigDecimal exponent = parseExpression();
        expect(')');
        // BigDecimal.pow only supports integer exponents
        return BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue()))
                .setScale(scale, roundingMode);
    }

    private BigDecimal parseSqrt() {
        expect('(');
        BigDecimal val = parseExpression();
        expect(')');
        return BigDecimal.valueOf(Math.sqrt(val.doubleValue()))
                .setScale(scale, roundingMode);
    }

    private BigDecimal parseSum() {
        List<BigDecimal> args = parseArgList();
        return args.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<BigDecimal> parseArgList() {
        List<BigDecimal> args = new ArrayList<>();
        expect('(');
        if (peek() != ')') {
            args.add(parseExpression());
            while (peek() == ',') {
                pos++;
                args.add(parseExpression());
            }
        }
        expect(')');
        return args;
    }

    // ---- References & Literals ----

    private BigDecimal parseVariableReference() {
        if (peek() != '$') return BigDecimal.ZERO;
        pos++;
        StringBuilder name = new StringBuilder();
        while (pos < expression.length()
                && (Character.isLetterOrDigit(expression.charAt(pos)) || expression.charAt(pos) == '_')) {
            name.append(expression.charAt(pos));
            pos++;
        }
        String varName = name.toString().trim();
        BigDecimal value = context.get(varName);
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal parseBracketFieldReference() {
        if (peek() != '[') return BigDecimal.ZERO;
        pos++;
        StringBuilder name = new StringBuilder();
        while (pos < expression.length() && peek() != ']') {
            name.append(expression.charAt(pos));
            pos++;
        }
        if (peek() == ']') pos++;
        String fieldName = name.toString().trim();
        BigDecimal value = context.get(fieldName);
        return value != null ? value : BigDecimal.ZERO;
    }

    private String parseString() {
        char quote = peek();
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < expression.length() && peek() != quote) {
            sb.append(expression.charAt(pos));
            pos++;
        }
        if (peek() == quote) pos++;
        return sb.toString();
    }

    private BigDecimal parseNumber() {
        StringBuilder sb = new StringBuilder();
        if (peek() == '-') {
            sb.append('-');
            pos++;
        }
        while (pos < expression.length()
                && (Character.isDigit(expression.charAt(pos)) || expression.charAt(pos) == '.')) {
            sb.append(expression.charAt(pos));
            pos++;
        }
        String numStr = sb.toString();
        if (numStr.isEmpty() || numStr.equals("-")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(numStr);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // ---- Helpers ----

    private void expect(char c) {
        skipWhitespace();
        if (pos < expression.length() && expression.charAt(pos) == c) {
            pos++;
        } else {
            throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
        }
    }

    private boolean match(String s) {
        if (pos + s.length() > expression.length()) return false;
        if (!expression.substring(pos, pos + s.length()).equals(s)) return false;
        pos += s.length();
        return true;
    }

    private boolean matchKeyword(String keyword) {
        if (pos + keyword.length() > expression.length()) return false;
        String substr = expression.substring(pos, pos + keyword.length()).toUpperCase();
        if (!substr.equals(keyword)) return false;
        int nextPos = pos + keyword.length();
        if (nextPos < expression.length()
                && (Character.isLetterOrDigit(expression.charAt(nextPos)) || expression.charAt(nextPos) == '_')) {
            return false;
        }
        pos = nextPos;
        return true;
    }

    private char peek() {
        return pos < expression.length() ? expression.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < expression.length() && Character.isWhitespace(expression.charAt(pos))) pos++;
    }

    private boolean isNegativeNumber() {
        int lookAhead = pos + 1;
        // Skip whitespace after the minus sign
        while (lookAhead < expression.length() && Character.isWhitespace(expression.charAt(lookAhead))) {
            lookAhead++;
        }
        if (lookAhead >= expression.length()) return true;
        char next = expression.charAt(lookAhead);
        // A minus is unary (negative number) only if what follows is NOT a valid operand start
        return !Character.isDigit(next) && next != '$' && next != '[' && next != '(' && !Character.isLetter(next);
    }

    private boolean toBoolean(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) != 0;
    }

    // ---- Result type ----

    public record FormulaResult(BigDecimal value, String formatted, boolean error, String errorMessage) {
        public FormulaResult {
            value = value != null ? value : BigDecimal.ZERO;
            formatted = formatted != null ? formatted : "0";
        }

        public FormulaResult(BigDecimal value, String formatted) {
            this(value, formatted, false, null);
        }

        public static FormulaResult empty() {
            return new FormulaResult(BigDecimal.ZERO, "0", false, null);
        }

        public static FormulaResult error(String message) {
            return new FormulaResult(BigDecimal.ZERO, "0", true, message);
        }

        public Double asDouble() {
            return value.doubleValue();
        }

        public BigDecimal asBigDecimal(int scale, RoundingMode mode) {
            return value.setScale(scale, mode);
        }
    }
}
