package dev.kid.core.agent.capabilities

import dev.kid.core.agent.AgentCapability
import dev.kid.core.agent.ToolParameter
import dev.kid.core.common.KidResult
import dev.kid.core.common.runCatchingKid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Math and calculation agent — evaluates mathematical expressions.
 * Runs entirely locally, no external dependencies.
 */
@Singleton
class CalculatorCapability @Inject constructor() : AgentCapability {

    override val name = "calculator"

    override val description = "Evaluate mathematical expressions. Supports: " +
        "+, -, *, /, ^, sqrt, abs, min, max. Returns numeric result."

    override val parameters = listOf(
        ToolParameter("expression", "Math expression to evaluate (e.g., '2 + 3 * 4')"),
    )

    override suspend fun execute(input: Map<String, String>): KidResult<String> {
        val expression = input["expression"]
            ?: return KidResult.Error("Missing parameter: expression")

        return runCatchingKid {
            val result = evaluate(expression)
            result.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    private fun evaluate(expr: String): Double {
        val cleaned = expr.replace(" ", "")
        return parseExpression(cleaned, 0).first
    }

    private fun parseExpression(expr: String, pos: Int): Pair<Double, Int> {
        var (left, p) = parseTerm(expr, pos)
        while (p < expr.length && (expr[p] == '+' || expr[p] == '-')) {
            val op = expr[p]
            val (right, newPos) = parseTerm(expr, p + 1)
            left = if (op == '+') left + right else left - right
            p = newPos
        }
        return left to p
    }

    private fun parseTerm(expr: String, pos: Int): Pair<Double, Int> {
        var (left, p) = parsePower(expr, pos)
        while (p < expr.length && (expr[p] == '*' || expr[p] == '/')) {
            val op = expr[p]
            val (right, newPos) = parsePower(expr, p + 1)
            left = if (op == '*') left * right else left / right
            p = newPos
        }
        return left to p
    }

    private fun parsePower(expr: String, pos: Int): Pair<Double, Int> {
        var (left, p) = parseFactor(expr, pos)
        while (p < expr.length && expr[p] == '^') {
            val (right, newPos) = parseFactor(expr, p + 1)
            left = Math.pow(left, right)
            p = newPos
        }
        return left to p
    }

    private fun parseFactor(expr: String, pos: Int): Pair<Double, Int> {
        var p = pos

        // Unary minus
        if (p < expr.length && expr[p] == '-') {
            val (value, newPos) = parseFactor(expr, p + 1)
            return -value to newPos
        }

        // Parentheses
        if (p < expr.length && expr[p] == '(') {
            val (value, newPos) = parseExpression(expr, p + 1)
            return value to (newPos + 1) // skip ')'
        }

        // Functions
        for (func in FUNCTIONS.keys) {
            if (expr.startsWith(func, p)) {
                p += func.length
                require(p < expr.length && expr[p] == '(') { "Expected '(' after $func" }
                val (arg, newPos) = parseExpression(expr, p + 1)
                return FUNCTIONS[func]!!(arg) to (newPos + 1) // skip ')'
            }
        }

        // Number
        val start = p
        while (p < expr.length && (expr[p].isDigit() || expr[p] == '.')) p++
        require(p > start) { "Expected number at position $start" }
        return expr.substring(start, p).toDouble() to p
    }

    companion object {
        private val FUNCTIONS = mapOf<String, (Double) -> Double>(
            "sqrt" to { Math.sqrt(it) },
            "abs" to { Math.abs(it) },
            "sin" to { Math.sin(it) },
            "cos" to { Math.cos(it) },
            "tan" to { Math.tan(it) },
            "log" to { Math.log10(it) },
            "ln" to { Math.log(it) },
        )
    }
}
