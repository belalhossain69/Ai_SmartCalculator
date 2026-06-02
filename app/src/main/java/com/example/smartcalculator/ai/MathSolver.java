package com.example.smartcalculator.ai;

import android.util.Log; // Add this import
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.interfaces.IExpr;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MathSolver {
    // 1. Change from 'final' to a regular static variable
    private static ExprEvaluator util;

    // 2. Add a helper to ensure 'util' is initialized safely
    private static synchronized ExprEvaluator getUtil() {
        if (util == null) {
            try {
                util = new ExprEvaluator();
            } catch (Throwable t) {
                Log.e("MATH_ERROR", "Failed to init Symja: " + t.getMessage());
                return null;
            }
        }
        return util;
    }

    public static String solve(String expression) {
        if (expression == null || expression.trim().isEmpty()) return "";

        try {
            // Check for initialization
            ExprEvaluator evaluator = getUtil();
            if (evaluator == null) return "❌ Math Engine not ready.";

            String input = expression.replace("×", "*").replace("÷", "/").trim();

            // 1️⃣ ARITHMETIC CHECK
            if (!input.contains("=") && !input.matches(".*[a-zA-Z].*")) {
                double calc = new ExpressionBuilder(input).build().evaluate();
                return "🧮 Result: " + formatDecimal(calc);
            }

            input = input.replaceAll("(\\d)([a-zA-Z])", "$1*$2");

            if (input.contains("=")) {
                return solveEquation(input);
            } else {
                // 3. Use the local 'evaluator' variable instead of 'util'
                IExpr result = evaluator.eval(input);
                return "💡 " + result.toString();
            }

        } catch (Exception e) {
            return "❌ Math Error: " + e.getMessage();
        }
    }

    private static String solveEquation(String input) {
        ExprEvaluator evaluator = getUtil();
        if (evaluator == null) return "❌ Engine error.";

        List<String> equations = new ArrayList<>();
        Set<String> variablesSet = new HashSet<>();

        String[] parts = input.split(",");
        for (String p : parts) {
            if (p.contains("=")) {
                String formatted = p.replace("=", "==");
                equations.add(formatted);

                Matcher varMatcher = Pattern.compile("(?<![a-z])[a-z](?![a-z])").matcher(p.toLowerCase());
                while (varMatcher.find()) {
                    variablesSet.add(varMatcher.group());
                }
            }
        }

        String symjaInput;
        if (variablesSet.size() > 1) {
            symjaInput = "Solve({" + String.join(",", equations) + "}, {" + String.join(",", variablesSet) + "})";
        } else {
            symjaInput = "Solve(" + equations.get(0) + ")";
        }

        IExpr result = evaluator.eval(symjaInput);
        if (result.toString().equals("{}")) return "🤔 No solution found.";

        return "💡 Solution:\n" + cleanSymjaOutput(result.toString());
    }

    private static String formatDecimal(double d) {
        if (d == (long) d) return String.format("%d", (long) d);
        return String.format("%s", d);
    }

    private static String cleanSymjaOutput(String out) {
        return out.replaceAll("[{}]", "").replace("->", " = ").replace(",", "\n");
    }
}