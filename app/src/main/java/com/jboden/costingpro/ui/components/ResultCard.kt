package com.jboden.costingpro.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.theme.FigureLabelStyle
import com.jboden.costingpro.ui.theme.MarginFigureStyle
import com.jboden.costingpro.ui.theme.pillColor
import java.math.BigDecimal

/**
 * The result block of a recipe, shared by the editor and the detail screen: the margin as the
 * biggest figure with its state pill on the same line, then cost, net price, VAT price and
 * profit per unit in a 2×2 grid of equal cells (PLAN.md, "Tarjeta de resultado").
 *
 * @param footer extra lines under the grid, such as the profit per batch in the editor.
 */
@Composable
fun ResultCard(
    marginPct: BigDecimal?,
    status: MarginStatus?,
    unitCost: BigDecimal?,
    netPrice: BigDecimal?,
    priceWithVat: BigDecimal?,
    profitPerUnit: BigDecimal?,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    // Told apart by its background colour, not by elevation.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                FigureLabel(R.string.result_margin)
                Text(
                    text = marginPct?.let { Formats.percent(it) } ?: stringResource(R.string.value_missing),
                    style = MarginFigureStyle,
                    color = status?.pillColor() ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            if (status != null) MarginPill(status)
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(R.string.detail_unit_cost, unitCost, Modifier.weight(1f))
            StatCell(R.string.detail_net_price, netPrice, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(R.string.detail_price_with_vat, priceWithVat, Modifier.weight(1f))
            StatCell(R.string.detail_profit_unit, profitPerUnit, Modifier.weight(1f))
        }
        footer?.invoke()
    }
}

/** A label over a figure, in small upper case and the secondary text colour. */
@Composable
fun FigureLabel(@StringRes label: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(label).uppercase(SpanishLocale),
        modifier = modifier,
        style = FigureLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One cell of the grid: a small white card with the label on top and the amount under it. */
@Composable
private fun StatCell(@StringRes label: Int, amount: BigDecimal?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FigureLabel(label)
        Text(
            text = amount?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
            style = MaterialTheme.typography.titleLarge.merge(TabularFigures),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
