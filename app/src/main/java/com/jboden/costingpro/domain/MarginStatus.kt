package com.jboden.costingpro.domain

import java.math.BigDecimal

/**
 * How healthy a recipe's margin is, judged by its food cost (PLAN.md, "Diseño visual").
 * The two thresholds live here and nowhere else.
 */
enum class MarginStatus {
    /** Food cost up to [GOOD_MAX_FOOD_COST_PCT]. */
    GOOD,

    /** Food cost above the good limit and up to [FAIR_MAX_FOOD_COST_PCT]. */
    FAIR,

    /** Food cost above [FAIR_MAX_FOOD_COST_PCT]. */
    LOW;

    companion object {
        val GOOD_MAX_FOOD_COST_PCT: BigDecimal = BigDecimal(30)
        val FAIR_MAX_FOOD_COST_PCT: BigDecimal = BigDecimal(35)

        fun fromFoodCost(foodCostPct: BigDecimal): MarginStatus = when {
            foodCostPct <= GOOD_MAX_FOOD_COST_PCT -> GOOD
            foodCostPct <= FAIR_MAX_FOOD_COST_PCT -> FAIR
            else -> LOW
        }
    }
}
