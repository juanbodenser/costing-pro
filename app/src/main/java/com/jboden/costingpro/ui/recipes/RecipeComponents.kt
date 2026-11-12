package com.jboden.costingpro.ui.recipes

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.theme.color
import java.math.BigDecimal

// Pieces shared by the recipe editor, the list and the detail screen.

/** The word that goes with a margin state: "Bueno", "Justo" or "Bajo". */
@StringRes
fun MarginStatus.wordRes(): Int = when (this) {
    MarginStatus.GOOD -> R.string.margin_good
    MarginStatus.FAIR -> R.string.margin_fair
    MarginStatus.LOW -> R.string.margin_low
}

/** The word and the dot go with the colour, so the state does not depend on colour alone. */
@Composable
internal fun StatusLabel(status: MarginStatus) {
    val word = status.wordRes()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(status.color()),
        )
        Text(
            text = stringResource(word),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ResultFigure(@StringRes label: Int, amount: BigDecimal?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
            style = MaterialTheme.typography.titleLarge.merge(TabularFigures),
        )
    }
}

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}
