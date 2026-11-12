package com.jboden.costingpro.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.MeasureUnit
import java.math.BigDecimal

/** Tabular figures, so amounts line up in a column. Merge it into the text style of any amount. */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

/** Top bar of every screen. Shows a back arrow when [onBack] is given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostingTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Outlined fields filled with the surface colour, so they read as white boxes over the ivory background. */
@Composable
fun costingFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
)

@get:StringRes
val MeasureUnit.labelRes: Int
    get() = when (this) {
        MeasureUnit.KG -> R.string.unit_kg
        MeasureUnit.G -> R.string.unit_g
        MeasureUnit.L -> R.string.unit_l
        MeasureUnit.ML -> R.string.unit_ml
        MeasureUnit.UD -> R.string.unit_ud
        MeasureUnit.RACION -> R.string.unit_racion
    }

/** "0,90 €/kg". */
@Composable
fun unitPriceText(unitPrice: BigDecimal, unit: MeasureUnit): String =
    stringResource(R.string.unit_price_format, Formats.money(unitPrice), stringResource(unit.labelRes))
