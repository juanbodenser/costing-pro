package com.jboden.costingpro.domain

import java.math.BigDecimal

/** Physical magnitude of a unit. Quantities only convert within the same magnitude. */
enum class Magnitude { MASS, VOLUME, COUNT, PORTION }

/**
 * Units used for purchase formats, recipe lines and recipe yields.
 *
 * [toBase] converts a quantity in this unit to the base unit of its magnitude
 * (kg, l, ud or ración). Purchase formats use every unit except [RACION].
 */
enum class MeasureUnit(val magnitude: Magnitude, val toBase: BigDecimal) {
    KG(Magnitude.MASS, BigDecimal.ONE),
    G(Magnitude.MASS, BigDecimal("0.001")),
    L(Magnitude.VOLUME, BigDecimal.ONE),
    ML(Magnitude.VOLUME, BigDecimal("0.001")),
    UD(Magnitude.COUNT, BigDecimal.ONE),
    RACION(Magnitude.PORTION, BigDecimal.ONE);

    /** The unit that unit prices are expressed in: kg, l, ud or ración. */
    val baseUnit: MeasureUnit
        get() = when (magnitude) {
            Magnitude.MASS -> KG
            Magnitude.VOLUME -> L
            Magnitude.COUNT -> UD
            Magnitude.PORTION -> RACION
        }
}
