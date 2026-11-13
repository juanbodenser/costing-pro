package com.jboden.costingpro.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.theme.onPillColor
import com.jboden.costingpro.ui.theme.pillColor
import java.math.BigDecimal
import java.util.Locale

/** The word that goes with a margin state: "Bueno", "Justo" or "Bajo". */
@StringRes
fun MarginStatus.wordRes(): Int = when (this) {
    MarginStatus.GOOD -> R.string.margin_good
    MarginStatus.FAIR -> R.string.margin_fair
    MarginStatus.LOW -> R.string.margin_low
}

/** Locale for upper-casing the figure labels. */
internal val SpanishLocale: Locale = Locale.forLanguageTag("es-ES")

/**
 * The margin state as a filled pill in its colour, with the state word, so the state never
 * depends on colour alone. With [marginPct], the percentage goes in the same pill; [stacked]
 * puts it over the word, for the narrow room of a list row.
 */
@Composable
fun MarginPill(
    status: MarginStatus,
    modifier: Modifier = Modifier,
    marginPct: BigDecimal? = null,
    stacked: Boolean = false,
) {
    val textColor = onPillColor()
    if (stacked && marginPct != null) {
        Column(
            modifier = modifier
                .background(status.pillColor(), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Formats.percent(marginPct),
                style = MaterialTheme.typography.labelLarge.merge(TabularFigures),
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Text(
                text = stringResource(status.wordRes()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
        return
    }
    Row(
        modifier = modifier
            .background(status.pillColor(), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (marginPct != null) {
            Text(
                text = Formats.percent(marginPct),
                style = MaterialTheme.typography.labelLarge.merge(TabularFigures),
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
        Text(
            text = stringResource(status.wordRes()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}
