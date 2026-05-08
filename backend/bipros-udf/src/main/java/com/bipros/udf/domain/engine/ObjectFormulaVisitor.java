package com.bipros.udf.domain.engine;

import java.util.Collections;
import java.util.Map;

/**
 * ANTLR visitor that evaluates formula expressions to {@link Object}
 * (String, Double, Long, or Boolean) for backward-compatible UDF support.
 * Replaces the hand-written {@link FormulaEvaluator}.
 */
public class ObjectFormulaVisitor extends FormulaBaseVisitor<Object> {

    private final Map<String, Object> context;

    /**
     * Creates a new visitor with the given variable context.
     *
     * @param context map of variable names to their values; may be null
     */
    public ObjectFormulaVisitor(Map<String, Object> context) {
        this.context = context != null ? context : Collections.emptyMap();
    }

    /**
     * Evaluates a logical OR expression.
     *
     * @param ctx the OR expression context
     * @return the boolean result of the OR operation
     */
    @Override
    public Object visitOrExpr(FormulaParser.OrExprContext ctx) {
        Object left = visit(ctx.andExpr(0));
        for (int i = 1; i < ctx.andExpr().size(); i++) {
            Object right = visit(ctx.andExpr(i));
            left = toBoolean(left) || toBoolean(right);
        }
        return left;
    }

    /**
     * Evaluates a logical AND expression.
     *
     * @param ctx the AND expression context
     * @return the boolean result of the AND operation
     */
    @Override
    public Object visitAndExpr(FormulaParser.AndExprContext ctx) {
        Object left = visit(ctx.comparisonExpr(0));
        for (int i = 1; i < ctx.comparisonExpr().size(); i++) {
            Object right = visit(ctx.comparisonExpr(i));
            left = toBoolean(left) && toBoolean(right);
        }
        return left;
    }

    /**
     * Evaluates a comparison expression (equality, inequality, less-than, etc.).
     *
     * @param ctx the comparison expression context
     * @return the boolean result of the comparison, or the left operand if no operator
     */
    @Override
    public Object visitComparisonExpr(FormulaParser.ComparisonExprContext ctx) {
        Object left = visit(ctx.additiveExpr(0));
        if (ctx.additiveExpr().size() == 1) {
            return left;
        }
        Object right = visit(ctx.additiveExpr(1));

        if (ctx.EQ() != null) return compareEquals(left, right);
        if (ctx.NEQ() != null) return !compareEquals(left, right);
        if (ctx.LT() != null) return compareNumeric(left, right) < 0;
        if (ctx.GT() != null) return compareNumeric(left, right) > 0;
        if (ctx.LTE() != null) return compareNumeric(left, right) <= 0;
        if (ctx.GTE() != null) return compareNumeric(left, right) >= 0;
        return left;
    }

    /**
     * Evaluates an additive expression (addition or subtraction).
     *
     * @param ctx the additive expression context
     * @return the numeric result as a {@link Double}
     */
    @Override
    public Object visitAdditiveExpr(FormulaParser.AdditiveExprContext ctx) {
        if (ctx.multiplicativeExpr().size() == 1) {
            return visit(ctx.multiplicativeExpr(0));
        }
        double result = toDouble(visit(ctx.multiplicativeExpr(0)));
        for (int i = 1; i < ctx.multiplicativeExpr().size(); i++) {
            if (ctx.PLUS(i - 1) != null) {
                result += toDouble(visit(ctx.multiplicativeExpr(i)));
            } else {
                result -= toDouble(visit(ctx.multiplicativeExpr(i)));
            }
        }
        return result;
    }

    /**
     * Evaluates a multiplicative expression (multiplication or division).
     *
     * @param ctx the multiplicative expression context
     * @return the numeric result as a {@link Double}
     * @throws FormulaEvaluationException if division by zero occurs
     */
    @Override
    public Object visitMultiplicativeExpr(FormulaParser.MultiplicativeExprContext ctx) {
        if (ctx.unaryExpr().size() == 1) {
            return visit(ctx.unaryExpr(0));
        }
        double result = toDouble(visit(ctx.unaryExpr(0)));
        for (int i = 1; i < ctx.unaryExpr().size(); i++) {
            if (ctx.MUL(i - 1) != null) {
                result *= toDouble(visit(ctx.unaryExpr(i)));
            } else {
                double divisor = toDouble(visit(ctx.unaryExpr(i)));
                if (divisor == 0) {
                    throw new FormulaEvaluationException("Division by zero");
                }
                result /= divisor;
            }
        }
        return result;
    }

    /**
     * Evaluates a unary expression (negation, plus, or logical NOT).
     *
     * @param ctx the unary expression context
     * @return the result of the unary operation
     */
    @Override
    public Object visitUnaryExpr(FormulaParser.UnaryExprContext ctx) {
        if (ctx.MINUS() != null) {
            return -toDouble(visit(ctx.unaryExpr()));
        }
        if (ctx.PLUS() != null) {
            return visit(ctx.unaryExpr());
        }
        if (ctx.NOT() != null) {
            return !toBoolean(visit(ctx.unaryExpr()));
        }
        return visit(ctx.primary());
    }

    /**
     * Evaluates a primary expression (function call, sub-expression, variable, etc.).
     *
     * @param ctx the primary expression context
     * @return the evaluated result
     */
    @Override
    public Object visitPrimary(FormulaParser.PrimaryContext ctx) {
        if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        }
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        if (ctx.variableRef() != null) {
            return visit(ctx.variableRef());
        }
        if (ctx.bracketRef() != null) {
            return visit(ctx.bracketRef());
        }
        if (ctx.stringLiteral() != null) {
            return visit(ctx.stringLiteral());
        }
        if (ctx.numberLiteral() != null) {
            return visit(ctx.numberLiteral());
        }
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
        return 0;
    }

    /**
     * Evaluates a built-in function call (IF, MAX, MIN, ABS, ROUND, POWER, SQRT, SUM, CONCAT).
     *
     * @param ctx the function call context
     * @return the function result
     */
    @Override
    public Object visitFunctionCall(FormulaParser.FunctionCallContext ctx) {
        if (ctx.IF() != null) {
            if (ctx.expression().size() < 3) {
                return "";
            }
            return toBoolean(visit(ctx.expression(0)))
                    ? visit(ctx.expression(1))
                    : visit(ctx.expression(2));
        }
        if (ctx.MAX() != null) {
            return ctx.expression().stream()
                    .map(e -> toDouble(visit(e)))
                    .max(Double::compare)
                    .orElse(0.0);
        }
        if (ctx.MIN() != null) {
            return ctx.expression().stream()
                    .map(e -> toDouble(visit(e)))
                    .min(Double::compare)
                    .orElse(0.0);
        }
        if (ctx.ABS() != null) {
            if (ctx.expression().isEmpty()) {
                return 0.0;
            }
            return Math.abs(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.ROUND() != null) {
            if (ctx.expression().size() < 2) {
                return 0.0;
            }
            double val = toDouble(visit(ctx.expression(0)));
            int places = (int) toDouble(visit(ctx.expression(1)));
            double multiplier = Math.pow(10, places);
            return Math.round(val * multiplier) / multiplier;
        }
        if (ctx.POWER() != null) {
            if (ctx.expression().size() < 2) {
                return 0.0;
            }
            return Math.pow(toDouble(visit(ctx.expression(0))),
                    toDouble(visit(ctx.expression(1))));
        }
        if (ctx.SQRT() != null) {
            if (ctx.expression().isEmpty()) {
                return 0.0;
            }
            return Math.sqrt(toDouble(visit(ctx.expression(0))));
        }
        if (ctx.SUM() != null) {
            return ctx.expression().stream()
                    .mapToDouble(e -> toDouble(visit(e)))
                    .sum();
        }
        if (ctx.CONCAT() != null) {
            StringBuilder sb = new StringBuilder();
            for (FormulaParser.ExpressionContext expr : ctx.expression()) {
                sb.append(visit(expr));
            }
            return sb.toString();
        }
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
        return 0;
    }

    private java.util.List<Double> collectDoubleArguments(FormulaParser.FunctionCallContext ctx) {
        return ctx.expression().stream()
                .map(e -> toDouble(visit(e)))
                .toList();
    }

    /**
     * Resolves a variable reference (prefixed with '$') from the context.
     *
     * @param ctx the variable reference context
     * @return the variable value, or an empty string if not found
     */
    @Override
    public Object visitVariableRef(FormulaParser.VariableRefContext ctx) {
        String name = ctx.getText();
        if (name.startsWith("$")) {
            name = name.substring(1);
        }
        Object value = context.get(name);
        return value != null ? value : "";
    }

    /**
     * Resolves a bracketed variable reference from the context.
     *
     * @param ctx the bracket reference context
     * @return the variable value, or an empty string if not found
     */
    @Override
    public Object visitBracketRef(FormulaParser.BracketRefContext ctx) {
        String name = ctx.getText();
        if (name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1);
        }
        Object value = context.get(name);
        return value != null ? value : "";
    }

    /**
     * Evaluates a string literal, stripping surrounding quotes if present.
     *
     * @param ctx the string literal context
     * @return the unquoted string value
     */
    @Override
    public Object visitStringLiteral(FormulaParser.StringLiteralContext ctx) {
        String text = ctx.getText();
        if (text.length() >= 2) {
            if ((text.startsWith("\"") && text.endsWith("\"")) ||
                (text.startsWith("'") && text.endsWith("'"))) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    /**
     * Evaluates a number literal, parsing as {@link Long} or {@link Double}
     * depending on presence of a decimal point.
     *
     * @param ctx the number literal context
     * @return the parsed numeric value ({@link Long} or {@link Double})
     */
    @Override
    public Object visitNumberLiteral(FormulaParser.NumberLiteralContext ctx) {
        String text = ctx.getText();
        if (text.contains(".")) {
            return Double.parseDouble(text);
        }
        if (text.startsWith("-")) {
            return Double.parseDouble(text);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return Double.parseDouble(text);
        }
    }

    /**
     * Compares two objects for equality using case-insensitive string comparison.
     *
     * @param left  the left operand
     * @param right the right operand
     * @return true if both are null or equal ignoring case, false otherwise
     */
    private boolean compareEquals(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return String.valueOf(left).equalsIgnoreCase(String.valueOf(right));
    }

    /**
     * Compares two objects numerically.
     *
     * @param left  the left operand
     * @param right the right operand
     * @return negative, zero, or positive as left is less than, equal to, or greater than right
     */
    private int compareNumeric(Object left, Object right) {
        return Double.compare(toDouble(left), toDouble(right));
    }

    /**
     * Converts an object to a boolean value.
     *
     * @param value the value to convert
     * @return the boolean representation; non-zero numbers and non-empty strings are true
     */
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        String str = String.valueOf(value).toLowerCase();
        return !str.isEmpty() && !str.equals("0") && !str.equals("false") && !str.equals("null");
    }

    /**
     * Converts an object to a double value.
     *
     * @param value the value to convert
     * @return the double representation, or 0.0 if parsing fails
     */
    private double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        String str = String.valueOf(value).trim();
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
