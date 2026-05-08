package com.bipros.udf.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

/**
 * ANTLR visitor that evaluates formula expressions to {@link BigDecimal}.
 * Replaces the hand-written {@link BigDecimalFormulaEvaluator}.
 */
public class BigDecimalFormulaVisitor extends FormulaBaseVisitor<BigDecimal> {

    private final Map<String, BigDecimal> context;
    private final int scale;
    private final RoundingMode roundingMode;
    private final BigDecimal zeroDefault;

    /**
     * Creates a new visitor with the given evaluation context and arithmetic settings.
     *
     * @param context     map of variable names to their {@link BigDecimal} values;
     *                    may be {@code null}, in which case an empty map is used
     * @param scale       the scale to use for division and power results
     * @param roundingMode the rounding mode to apply for division and power results
     * @param zeroDefault the value to return on division by zero;
     *                    may be {@code null}, in which case {@link BigDecimal#ZERO} is used
     */
    public BigDecimalFormulaVisitor(Map<String, BigDecimal> context,
                                     int scale, RoundingMode roundingMode,
                                     BigDecimal zeroDefault) {
        this.context = context != null ? context : Collections.emptyMap();
        this.scale = scale;
        this.roundingMode = roundingMode;
        this.zeroDefault = zeroDefault != null ? zeroDefault : BigDecimal.ZERO;
    }

    /**
     * Evaluates a logical OR expression.
     *
     * @param ctx the parse tree context
     * @return {@link BigDecimal#ONE} if any operand is true, otherwise {@link BigDecimal#ZERO}
     */
    @Override
    public BigDecimal visitOrExpr(FormulaParser.OrExprContext ctx) {
        BigDecimal left = visit(ctx.andExpr(0));
        for (int i = 1; i < ctx.andExpr().size(); i++) {
            BigDecimal right = visit(ctx.andExpr(i));
            left = toBoolean(left) || toBoolean(right) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return left;
    }

    /**
     * Evaluates a logical AND expression.
     *
     * @param ctx the parse tree context
     * @return {@link BigDecimal#ONE} if all operands are true, otherwise {@link BigDecimal#ZERO}
     */
    @Override
    public BigDecimal visitAndExpr(FormulaParser.AndExprContext ctx) {
        BigDecimal left = visit(ctx.comparisonExpr(0));
        for (int i = 1; i < ctx.comparisonExpr().size(); i++) {
            BigDecimal right = visit(ctx.comparisonExpr(i));
            left = toBoolean(left) && toBoolean(right) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return left;
    }

    /**
     * Evaluates a comparison expression (==, !=, &lt;, &gt;, &lt;=, &gt;=).
     *
     * @param ctx the parse tree context
     * @return {@link BigDecimal#ONE} if the comparison holds, otherwise {@link BigDecimal#ZERO}
     */
    @Override
    public BigDecimal visitComparisonExpr(FormulaParser.ComparisonExprContext ctx) {
        if (ctx.EQ() != null) {
            return visit(ctx.additiveExpr(0)).compareTo(visit(ctx.additiveExpr(1))) == 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (ctx.NEQ() != null) {
            return visit(ctx.additiveExpr(0)).compareTo(visit(ctx.additiveExpr(1))) != 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (ctx.LT() != null) {
            return visit(ctx.additiveExpr(0)).compareTo(visit(ctx.additiveExpr(1))) < 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (ctx.GT() != null) {
            return visit(ctx.additiveExpr(0)).compareTo(visit(ctx.additiveExpr(1))) > 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (ctx.LTE() != null) {
            return visit(ctx.additiveExpr(0)).compareTo(visit(ctx.additiveExpr(1))) <= 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (ctx.GTE() != null) {
            return visit(ctx.additiveExpr(0)).compareTo(visit(ctx.additiveExpr(1))) >= 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return visit(ctx.additiveExpr(0));
    }

    /**
     * Evaluates an additive expression (+ or -).
     *
     * @param ctx the parse tree context
     * @return the result of the addition or subtraction
     */
    @Override
    public BigDecimal visitAdditiveExpr(FormulaParser.AdditiveExprContext ctx) {
        BigDecimal result = visit(ctx.multiplicativeExpr(0));
        for (int i = 1; i < ctx.multiplicativeExpr().size(); i++) {
            if (ctx.PLUS(i - 1) != null) {
                result = result.add(visit(ctx.multiplicativeExpr(i)));
            } else {
                result = result.subtract(visit(ctx.multiplicativeExpr(i)));
            }
        }
        return result;
    }

    /**
     * Evaluates a multiplicative expression (* or /).
     *
     * @param ctx the parse tree context
     * @return the result of the multiplication or division
     */
    @Override
    public BigDecimal visitMultiplicativeExpr(FormulaParser.MultiplicativeExprContext ctx) {
        BigDecimal result = visit(ctx.unaryExpr(0));
        for (int i = 1; i < ctx.unaryExpr().size(); i++) {
            if (ctx.MUL(i - 1) != null) {
                result = result.multiply(visit(ctx.unaryExpr(i)))
                        .setScale(scale, roundingMode);
            } else {
                BigDecimal divisor = visit(ctx.unaryExpr(i));
                if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                    result = zeroDefault;
                } else {
                    result = result.divide(divisor, scale, roundingMode);
                }
            }
        }
        return result;
    }

    /**
     * Evaluates a unary expression (+, -, or NOT).
     *
     * @param ctx the parse tree context
     * @return the negated, identity, or boolean-inverted value
     */
    @Override
    public BigDecimal visitUnaryExpr(FormulaParser.UnaryExprContext ctx) {
        if (ctx.MINUS() != null) {
            return visit(ctx.unaryExpr()).negate();
        }
        if (ctx.PLUS() != null) {
            return visit(ctx.unaryExpr());
        }
        if (ctx.NOT() != null) {
            return toBoolean(visit(ctx.unaryExpr())) ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        return visit(ctx.primary());
    }

    /**
     * Evaluates a primary expression (function call, nested expression, variable,
     * bracket reference, string literal, or number literal).
     *
     * @param ctx the parse tree context
     * @return the evaluated {@link BigDecimal} value
     */
    @Override
    public BigDecimal visitPrimary(FormulaParser.PrimaryContext ctx) {
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
            return BigDecimal.ZERO;
        }
        if (ctx.numberLiteral() != null) {
            return visit(ctx.numberLiteral());
        }
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
        return BigDecimal.ZERO;
    }

    /**
     * Evaluates a built-in function call (IF, MAX, MIN, ABS, ROUND, POWER, SQRT, SUM).
     *
     * @param ctx the parse tree context
     * @return the function result, or {@link BigDecimal#ZERO} if arguments are missing
     */
    @Override
    public BigDecimal visitFunctionCall(FormulaParser.FunctionCallContext ctx) {
        if (ctx.IF() != null) {
            if (ctx.expression().size() < 3) {
                return BigDecimal.ZERO;
            }
            return toBoolean(visit(ctx.expression(0)))
                    ? visit(ctx.expression(1))
                    : visit(ctx.expression(2));
        }
        if (ctx.MAX() != null) {
            return ctx.expression().stream()
                    .map(this::visit)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }
        if (ctx.MIN() != null) {
            return ctx.expression().stream()
                    .map(this::visit)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }
        if (ctx.ABS() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return visit(ctx.expression(0)).abs();
        }
        if (ctx.ROUND() != null) {
            if (ctx.expression().size() < 2) {
                return BigDecimal.ZERO;
            }
            BigDecimal val = visit(ctx.expression(0));
            int places = visit(ctx.expression(1)).intValue();
            return val.setScale(places, roundingMode);
        }
        if (ctx.POWER() != null) {
            if (ctx.expression().size() < 2) {
                return BigDecimal.ZERO;
            }
            BigDecimal base = visit(ctx.expression(0));
            BigDecimal exp = visit(ctx.expression(1));
            return BigDecimal.valueOf(Math.pow(base.doubleValue(), exp.doubleValue()))
                    .setScale(scale, roundingMode);
        }
        if (ctx.SQRT() != null) {
            if (ctx.expression().isEmpty()) {
                return BigDecimal.ZERO;
            }
            BigDecimal val = visit(ctx.expression(0));
            return BigDecimal.valueOf(Math.sqrt(val.doubleValue()))
                    .setScale(scale, roundingMode);
        }
        if (ctx.SUM() != null) {
            return ctx.expression().stream()
                    .map(this::visit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
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
        if (ctx.AVERAGE() != null) {
            var args = collectArguments(ctx);
            if (args.isEmpty()) return zeroDefault;
            BigDecimal sum = args.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(BigDecimal.valueOf(args.size()), scale, roundingMode);
        }
        if (ctx.COUNT() != null) {
            return BigDecimal.valueOf(ctx.expression().size());
        }
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
        if (ctx.SUMIF() != null) {
            if (ctx.expression().size() < 2) return zeroDefault;
            BigDecimal criteria = visit(ctx.expression(0));
            return ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> v.compareTo(criteria) == 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (ctx.COUNTIF() != null) {
            if (ctx.expression().size() < 2) return BigDecimal.ZERO;
            BigDecimal criteria = visit(ctx.expression(0));
            long count = ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> v.compareTo(criteria) == 0)
                    .count();
            return BigDecimal.valueOf(count);
        }
        if (ctx.AVERAGEIF() != null) {
            if (ctx.expression().size() < 2) return zeroDefault;
            BigDecimal criteria = visit(ctx.expression(0));
            var matches = ctx.expression().stream()
                    .skip(1)
                    .map(this::visit)
                    .filter(v -> v.compareTo(criteria) == 0)
                    .toList();
            if (matches.isEmpty()) return zeroDefault;
            BigDecimal sum = matches.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(BigDecimal.valueOf(matches.size()), scale, roundingMode);
        }
        return BigDecimal.ZERO;
    }

    private java.util.List<BigDecimal> collectArguments(FormulaParser.FunctionCallContext ctx) {
        return ctx.expression().stream()
                .map(this::visit)
                .toList();
    }

    /**
     * Resolves a variable reference (e.g. {@code $var}).
     *
     * @param ctx the parse tree context
     * @return the variable value, or {@link BigDecimal#ZERO} if not found
     */
    @Override
    public BigDecimal visitVariableRef(FormulaParser.VariableRefContext ctx) {
        String name = ctx.getText(); // This includes the $ prefix
        if (name.startsWith("$")) {
            name = name.substring(1);
        }
        BigDecimal value = context.get(name);
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Resolves a bracket reference (e.g. {@code [var]}).
     *
     * @param ctx the parse tree context
     * @return the referenced value, or {@link BigDecimal#ZERO} if not found
     */
    @Override
    public BigDecimal visitBracketRef(FormulaParser.BracketRefContext ctx) {
        String name = ctx.getText();
        if (name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1);
        }
        BigDecimal value = context.get(name);
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Parses a numeric literal.
     *
     * @param ctx the parse tree context
     * @return the {@link BigDecimal} value of the literal, or {@link BigDecimal#ZERO}
     *         if parsing fails
     */
    @Override
    public BigDecimal visitNumberLiteral(FormulaParser.NumberLiteralContext ctx) {
        String text = ctx.getText();
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Converts a {@link BigDecimal} to a boolean.
     *
     * @param value the value to convert; may be {@code null}
     * @return {@code true} if the value is non-null and non-zero, otherwise {@code false}
     */
    private boolean toBoolean(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) != 0;
    }
}
