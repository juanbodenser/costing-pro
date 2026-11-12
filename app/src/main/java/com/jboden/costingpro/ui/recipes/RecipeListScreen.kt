package com.jboden.costingpro.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jboden.costingpro.R
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.common.costingFieldColors
import com.jboden.costingpro.ui.theme.color
import java.io.File

@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    photoFile: (String) -> File,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CostingTopBar(title = stringResource(R.string.tab_recipes)) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.recipe_new))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.listEmpty) {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            when {
                state.loading -> Unit
                state.listEmpty -> EmptyMessage(stringResource(R.string.recipes_empty))
                state.rows.isEmpty() -> EmptyMessage(stringResource(R.string.recipes_no_results, state.query.trim()))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // The bottom padding keeps the last card clear of the floating button.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, photoFile = photoFile, onClick = { onOpen(recipe.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.recipes_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(12.dp),
        colors = costingFieldColors(),
    )
}

@Composable
private fun RecipeCard(recipe: RecipeSummary, photoFile: (String) -> File, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(photoPath = recipe.photoPath, photoFile = photoFile)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = costAndPrice(recipe),
                    style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Margin(recipe)
        }
    }
}

@Composable
private fun costAndPrice(recipe: RecipeSummary): String {
    val unitCost = recipe.cost.unitCost ?: return stringResource(R.string.recipe_row_no_cost)
    val netPrice = recipe.price?.netPrice
    return if (netPrice == null) {
        stringResource(R.string.recipe_row_cost_only, Formats.money(unitCost))
    } else {
        stringResource(R.string.recipe_row_cost_price, Formats.money(unitCost), Formats.money(netPrice))
    }
}

/** The margin in its state colour, with the state word under it. */
@Composable
private fun Margin(recipe: RecipeSummary) {
    val margin = recipe.price?.marginPct ?: return
    val status = recipe.status
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = Formats.percent(margin),
            style = MaterialTheme.typography.titleMedium.merge(TabularFigures),
            fontWeight = FontWeight.SemiBold,
            color = status?.color() ?: MaterialTheme.colorScheme.onSurface,
        )
        if (status != null) {
            Text(
                text = stringResource(status.wordRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The photo of a recipe as a small rounded square, or a neutral square with the recipe icon. */
@Composable
private fun Thumbnail(photoPath: String?, photoFile: (String) -> File) {
    val shape = RoundedCornerShape(8.dp)
    if (photoPath != null) {
        AsyncImage(
            model = photoFile(photoPath),
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tab_recipes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
