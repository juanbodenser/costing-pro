package com.jboden.costingpro.domain

/**
 * The 14 allergens of mandatory declaration in the EU (Regulation 1169/2011, Annex II).
 *
 * The declaration order is persisted: an ingredient stores its allergens as a bit mask
 * where each allergen uses the bit of its ordinal. Never reorder or remove entries.
 */
enum class Allergen {
    GLUTEN,
    CRUSTACEANS,
    EGGS,
    FISH,
    PEANUTS,
    SOY,
    MILK,
    TREE_NUTS,
    CELERY,
    MUSTARD,
    SESAME,
    SULPHITES,
    LUPIN,
    MOLLUSCS;

    val bit: Int get() = 1 shl ordinal

    companion object {
        fun maskOf(vararg allergens: Allergen): Int = allergens.fold(0) { mask, allergen -> mask or allergen.bit }

        fun fromMask(mask: Int): Set<Allergen> = entries.filterTo(mutableSetOf()) { mask and it.bit != 0 }
    }
}
