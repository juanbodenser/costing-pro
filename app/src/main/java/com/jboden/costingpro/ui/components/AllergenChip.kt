package com.jboden.costingpro.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.Allergen
import com.jboden.costingpro.ui.common.labelRes

@get:DrawableRes
val Allergen.iconRes: Int
    get() = when (this) {
        Allergen.GLUTEN -> R.drawable.ic_allergen_gluten
        Allergen.CRUSTACEANS -> R.drawable.ic_allergen_crustaceans
        Allergen.EGGS -> R.drawable.ic_allergen_eggs
        Allergen.FISH -> R.drawable.ic_allergen_fish
        Allergen.PEANUTS -> R.drawable.ic_allergen_peanuts
        Allergen.SOY -> R.drawable.ic_allergen_soy
        Allergen.MILK -> R.drawable.ic_allergen_milk
        Allergen.TREE_NUTS -> R.drawable.ic_allergen_tree_nuts
        Allergen.CELERY -> R.drawable.ic_allergen_celery
        Allergen.MUSTARD -> R.drawable.ic_allergen_mustard
        Allergen.SESAME -> R.drawable.ic_allergen_sesame
        Allergen.SULPHITES -> R.drawable.ic_allergen_sulphites
        Allergen.LUPIN -> R.drawable.ic_allergen_lupin
        Allergen.MOLLUSCS -> R.drawable.ic_allergen_molluscs
    }

/** An allergen as a filled chip in soft plum, with its icon. */
@Composable
fun AllergenChip(allergen: Allergen, modifier: Modifier = Modifier) {
    val plum = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = modifier
            .background(plum.copy(alpha = 0.14f), CircleShape)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(allergen.iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = plum,
        )
        Text(
            text = stringResource(allergen.labelRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
