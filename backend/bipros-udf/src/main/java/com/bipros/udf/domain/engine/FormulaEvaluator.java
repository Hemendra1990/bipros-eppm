package com.bipros.udf.domain.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FormulaEvaluator {

  private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
  private static final Pattern FIELD_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

  private String expression;
  private int pos;
  private Map<String, Object> context;

  public FormulaEvaluator(String expression, Map<String, Object> context) {
    this.expression = expression != null ? expression.trim() : "";
    this.context = context != null ? context : new HashMap<>();
    this.pos = 0;
  }

  /**
   * Evaluates the expression against the context and returns the result as a string.
   */
  public String evaluate() {
    if (expression.isEmpty()) {
      return "";
    }

    try {
      Object result = parseExpression();
      return result != null ? String.valueOf(result) : "";
    } catch (Exception e) {
      log.warn("Error evaluating formula: {}", expression, e);
      return "";
    }
  }

  private Object parseExpression() {
    return parseOr();
  }

  private Object parseOr() {
    Object left = parseAnd();

    while (pos < expression.length() && matchKeyword("OR")) {
      skipWhitespace();
      Object right = parseAnd();
      left = toBoolean(left) || toBoolean(right);
    }

    return left;
  }

  private Object parseAnd() {
    Object left = parseComparison();

    while (pos < expression.length() && matchKeyword("AND")) {
      skipWhitespace();
      Object right = parseComparison();
      left = toBoolean(left) && toBoolean(right);
    }

    return left;
  }

  private Object parseComparison() {
    Object left = parseAdditive();

    skipWhitespace();
    if (pos < expression.length()) {
      if (match("!=")) {
        Object right = parseAdditive();
        return !compareEquals(left, right);
      } else if (match("=") || match("==")) {
        Object right = parseAdditive();
        return compareEquals(left, right);
      } else if (match("<=")) {
        Object right = parseAdditive();
        return compareNumeric(left, right) <= 0;
      } else if (match(">=")) {
        Object right = parseAdditive();
        return compareNumeric(left, right) >= 0;
      } else if (match("<")) {
        Object right = parseAdditive();
        return compareNumeric(left, right) < 0;
      } else if (match(">")) {
        Object right = parseAdditive();
        return compareNumeric(left, right) > 0;
      }
    }

    return left;
  }

  private Object parseAdditive() {
    Object result = parseMultiplicative();

    while (pos < expression.length()) {
      skipWhitespace();
      if (peek() == '+') {
        pos++;
        Object right = parseMultiplicative();
        result = toDouble(result) + toDouble(right);
      } else if (peek() == '-' && !isNegative()) {
        pos++;
        Object right = parseMultiplicative();
        result = toDouble(result) - toDouble(right);
      } else {
        break;
      }
    }

    return result;
  }

  private Object parseMultiplicative() {
    Object result = parseUnary();

    while (pos < expression.length()) {
      skipWhitespace();
      if (peek() == '*') {
        pos++;
        Object right = parseUnary();
        result = toDouble(result) * toDouble(right);
      } else if (peek() == '/') {
        pos++;
        Object right = parseUnary();
        double rightVal = toDouble(right);
        if (rightVal == 0) {
          throw new IllegalArgumentException("Division by zero");
        }
        result = toDouble(result) / rightVal;
      } else {
        break;
      }
    }

    return result;
  }

  private Object parseUnary() {
    skipWhitespace();

    if (peek() == '-') {
      pos++;
      return -toDouble(parseUnary());
    } else if (peek() == '+') {
      pos++;
      return parseUnary();
    } else if (matchKeyword("NOT")) {
      skipWhitespace();
      return !toBoolean(parseUnary());
    }

    return parsePrimary();
  }

  private Object parsePrimary() {
    skipWhitespace();

    if (pos >= expression.length()) {
      return 0;
    }

    if (matchKeyword("IF")) {
      return parseIf();
    } else if (matchKeyword("CONCAT")) {
      return parseConcat();
    } else if (matchKeyword("ABS")) {
      return parseAbs();
    } else if (matchKeyword("ROUND")) {
      return parseRound();
    } else if (peek() == '(') {
      pos++;
      Object result = parseExpression();
      skipWhitespace();
      if (peek() == ')') {
        pos++;
      }
      return result;
    } else if (peek() == '[') {
      return parseFieldReference();
    } else if (peek() == '"' || peek() == '\'') {
      return parseString();
    } else {
      return parseNumber();
    }
  }

  private Object parseIf() {
    skipWhitespace();
    if (peek() != '(') {
      throw new IllegalArgumentException("Expected '(' after IF");
    }
    pos++;

    Object condition = parseExpression();
    skipWhitespace();

    if (peek() != ',') {
      throw new IllegalArgumentException("Expected ',' in IF expression");
    }
    pos++;

    Object trueValue = parseExpression();
    skipWhitespace();

    if (peek() != ',') {
      throw new IllegalArgumentException("Expected ',' in IF expression");
    }
    pos++;

    Object falseValue = parseExpression();
    skipWhitespace();

    if (peek() != ')') {
      throw new IllegalArgumentException("Expected ')' to close IF");
    }
    pos++;

    return toBoolean(condition) ? trueValue : falseValue;
  }

  private Object parseConcat() {
    skipWhitespace();
    if (peek() != '(') {
      throw new IllegalArgumentException("Expected '(' after CONCAT");
    }
    pos++;

    StringBuilder sb = new StringBuilder();
    Object first = parseExpression();
    sb.append(first);

    while (pos < expression.length()) {
      skipWhitespace();
      if (peek() == ',') {
        pos++;
        Object next = parseExpression();
        sb.append(next);
      } else if (peek() == ')') {
        pos++;
        break;
      } else {
        throw new IllegalArgumentException("Expected ',' or ')' in CONCAT");
      }
    }

    return sb.toString();
  }

  private Object parseAbs() {
    skipWhitespace();
    if (peek() != '(') {
      throw new IllegalArgumentException("Expected '(' after ABS");
    }
    pos++;

    Object value = parseExpression();
    skipWhitespace();

    if (peek() != ')') {
      throw new IllegalArgumentException("Expected ')' to close ABS");
    }
    pos++;

    return Math.abs(toDouble(value));
  }

  private Object parseRound() {
    skipWhitespace();
    if (peek() != '(') {
      throw new IllegalArgumentException("Expected '(' after ROUND");
    }
    pos++;

    Object value = parseExpression();
    skipWhitespace();

    if (peek() != ',') {
      throw new IllegalArgumentException("Expected ',' after value in ROUND");
    }
    pos++;

    Object decimals = parseExpression();
    skipWhitespace();

    if (peek() != ')') {
      throw new IllegalArgumentException("Expected ')' to close ROUND");
    }
    pos++;

    int decimalPlaces = (int) toDouble(decimals);
    double val = toDouble(value);
    double multiplier = Math.pow(10, decimalPlaces);
    return Math.round(val * multiplier) / multiplier;
  }

  private Object parseFieldReference() {
    if (peek() != '[') {
      throw new IllegalArgumentException("Expected '[' for field reference");
    }
    pos++;

    StringBuilder fieldName = new StringBuilder();
    while (pos < expression.length() && peek() != ']') {
      fieldName.append(expression.charAt(pos));
      pos++;
    }

    if (peek() != ']') {
      throw new IllegalArgumentException("Expected ']' to close field reference");
    }
    pos++;

    String name = fieldName.toString().trim();
    Object value = context.get(name);
    return value != null ? value : "";
  }

  private Object parseString() {
    char quote = peek();
    pos++;

    StringBuilder sb = new StringBuilder();
    while (pos < expression.length() && peek() != quote) {
      sb.append(expression.charAt(pos));
      pos++;
    }

    if (peek() == quote) {
      pos++;
    }

    return sb.toString();
  }

  private Object parseNumber() {
    StringBuilder sb = new StringBuilder();

    if (peek() == '-') {
      sb.append('-');
      pos++;
    }

    while (pos < expression.length() && (Character.isDigit(peek()) || peek() == '.')) {
      sb.append(expression.charAt(pos));
      pos++;
    }

    String numStr = sb.toString();
    if (numStr.isEmpty() || numStr.equals("-")) {
      return 0;
    }

    try {
      if (numStr.contains(".")) {
        return Double.parseDouble(numStr);
      } else {
        return Long.parseLong(numStr);
      }
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private boolean match(String s) {
    if (pos + s.length() > expression.length()) {
      return false;
    }
    String substr = expression.substring(pos, pos + s.length());
    if (substr.equals(s)) {
      pos += s.length();
      return true;
    }
    return false;
  }

  private boolean matchKeyword(String keyword) {
    if (pos + keyword.length() > expression.length()) {
      return false;
    }

    String substr = expression.substring(pos, pos + keyword.length()).toUpperCase();
    if (!substr.equals(keyword)) {
      return false;
    }

    int nextPos = pos + keyword.length();
    if (nextPos < expression.length() && (Character.isLetterOrDigit(expression.charAt(nextPos)) || expression.charAt(nextPos) == '_')) {
      return false;
    }

    pos = nextPos;
    return true;
  }

  private char peek() {
    if (pos >= expression.length()) {
      return '\0';
    }
    return expression.charAt(pos);
  }

  private void skipWhitespace() {
    while (pos < expression.length() && Character.isWhitespace(peek())) {
      pos++;
    }
  }

  private boolean isNegative() {
    int lookAhead = pos + 1;
    if (lookAhead >= expression.length()) {
      return true;
    }

    char next = expression.charAt(lookAhead);
    return !Character.isDigit(next) && next != '[' && next != '"' && next != '\'';
  }

  private boolean compareEquals(Object left, Object right) {
    if (left == null && right == null) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }

    String leftStr = String.valueOf(left).toLowerCase();
    String rightStr = String.valueOf(right).toLowerCase();
    return leftStr.equals(rightStr);
  }

  private int compareNumeric(Object left, Object right) {
    double leftVal = toDouble(left);
    double rightVal = toDouble(right);
    return Double.compare(leftVal, rightVal);
  }

  private boolean toBoolean(Object value) {
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue() != 0;
    }
    String str = String.valueOf(value).toLowerCase();
    return !str.isEmpty() && !str.equals("0") && !str.equals("false") && !str.equals("null");
  }

  private double toDouble(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    String str = String.valueOf(value).trim();
    try {
      return Double.parseDouble(str);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
