package dev.lino.blocksregenerator.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class DelayExpression {
    private final String input;
    private final Map<String, Double> variables;
    private final RandomGenerator random;
    private int position;
    private int depth;

    private DelayExpression(String input, Map<String, Double> variables, RandomGenerator random) {
        if (input == null || input.isBlank() || input.length() > 1024) {
            throw new IllegalArgumentException("Expression must contain 1-1024 characters");
        }
        this.input = input;
        this.variables = variables;
        this.random = random;
    }

    public static double evaluate(String input, Map<String, Double> variables, RandomGenerator random) {
        DelayExpression parser = new DelayExpression(input, variables, random);
        double value = parser.sum();
        parser.whitespace();
        if (parser.position != input.length() || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid or non-finite expression");
        }
        return value;
    }

    private double sum() {
        double result = product();
        while (true) {
            if (take('+')) {
                result += product();
            } else if (take('-')) {
                result -= product();
            } else {
                return result;
            }
        }
    }

    private double product() {
        double result = unary();
        while (true) {
            if (take('*')) {
                result *= unary();
            } else if (take('/')) {
                result /= unary();
            } else {
                return result;
            }
        }
    }

    private double unary() {
        enter();
        try {
            if (take('+')) {
                return unary();
            }
            if (take('-')) {
                return -unary();
            }
            double base = atom();
            return take('^') ? Math.pow(base, unary()) : base;
        } finally {
            depth--;
        }
    }

    private double atom() {
        whitespace();
        if (take('(')) {
            double value = sum();
            require(')');
            return value;
        }
        if (take('%')) {
            int start = position;
            while (position < input.length() && input.charAt(position) != '%') {
                position++;
            }
            String name = input.substring(start, position);
            require('%');
            Double value = variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Unknown variable: " + name);
            }
            return value;
        }
        if (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
            int start = position;
            while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
                position++;
            }
            if (position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                position++;
                if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    position++;
                }
            }
            return Double.parseDouble(input.substring(start, position));
        }
        int start = position;
        while (position < input.length() && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
            position++;
        }
        if (start == position) {
            throw new IllegalArgumentException("Expected a number or function at position " + position);
        }
        String name = input.substring(start, position).toLowerCase(Locale.ROOT);
        if (!take('(')) {
            if (name.equals("pi")) {
                return Math.PI;
            }
            if (name.equals("e")) {
                return Math.E;
            }
            Double value = variables.get(name);
            if (value != null) {
                return value;
            }
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
        List<Double> args = new ArrayList<>();
        if (!take(')')) {
            do {
                args.add(sum());
            } while (take(','));
            require(')');
        }
        return function(name, args);
    }

    private double function(String name, List<Double> args) {
        int count = name.equals("min") || name.equals("max") || name.equals("pow") || name.equals("runi") ? 2 : 1;
        if (args.size() != count) {
            throw new IllegalArgumentException("Wrong number of arguments for " + name);
        }
        double a = args.get(0);
        double b = count == 2 ? args.get(1) : 0;
        return switch (name) {
            case "min" -> Math.min(a, b);
            case "max" -> Math.max(a, b);
            case "pow" -> Math.pow(a, b);
            case "abs" -> Math.abs(a);
            case "sqrt" -> Math.sqrt(a);
            case "floor" -> Math.floor(a);
            case "ceil" -> Math.ceil(a);
            case "round" -> Math.round(a);
            case "sin" -> Math.sin(a);
            case "cos" -> Math.cos(a);
            case "ln" -> Math.log(a);
            case "log" -> Math.log10(a);
            case "runi" -> {
                if (!Double.isFinite(a) || !Double.isFinite(b) || b < a) {
                    throw new IllegalArgumentException("Invalid random range");
                }
                yield a == b ? a : random.nextDouble(a, b);
            }
            default -> throw new IllegalArgumentException("Unknown function: " + name);
        };
    }

    private void enter() {
        if (++depth > 48) {
            throw new IllegalArgumentException("Expression nesting is too deep");
        }
    }

    private boolean take(char expected) {
        whitespace();
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!take(expected)) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + position);
        }
    }

    private void whitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }
}
