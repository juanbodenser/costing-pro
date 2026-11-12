package com.jboden.costingpro.ui.ingredients

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.common.costingFieldColors
import com.jboden.costingpro.ui.common.labelRes
import com.jboden.costingpro.ui.common.unitPriceText

/** Units an ingredient can be bought in: all but the portion. */
private val purchaseUnits = MeasureUnit.entries.filter { it != MeasureUnit.RACION }

@Composable
fun IngredientEditScreen(
    viewModel: IngredientEditViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(state.finished) {
        if (state.finished) currentOnDone()
    }

    Scaffold(
        topBar = {
            CostingTopBar(
                title = stringResource(if (state.isNew) R.string.ingredient_new else R.string.ingredient_edit),
                onBack = onDone,
                actions = {
                    if (!state.isNew && !state.loading) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = R.string.ingredient_name,
                error = state.nameError,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
            FormField(
                value = state.price,
                onValueChange = viewModel::onPriceChange,
                label = R.string.ingredient_purchase_price,
                error = state.priceError,
                hint = R.string.ingredient_purchase_price_hint,
                suffix = stringResource(R.string.currency_symbol),
                keyboardOptions = decimalKeyboard(ImeAction.Next),
            )
            FormField(
                value = state.quantity,
                onValueChange = viewModel::onQuantityChange,
                label = R.string.ingredient_purchase_quantity,
                error = state.quantityError,
                hint = R.string.ingredient_purchase_quantity_hint,
                keyboardOptions = decimalKeyboard(ImeAction.Next),
            )
            UnitSelector(selected = state.unit, onSelect = viewModel::onUnitChange)
            FormField(
                value = state.waste,
                onValueChange = viewModel::onWasteChange,
                label = R.string.ingredient_waste,
                error = state.wasteError,
                hint = R.string.ingredient_waste_hint,
                suffix = stringResource(R.string.percent_symbol),
                keyboardOptions = decimalKeyboard(ImeAction.Done),
            )

            UnitPriceCard(state)

            if (!state.isNew) UsedIn(state.usedIn)

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.ingredient_delete_title)) },
            text = { Text(stringResource(R.string.ingredient_delete_message, state.name.trim())) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.deleteBlockedBy?.let { recipes ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.delete_blocked_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ingredient_delete_blocked_message))
                    recipes.forEach { Text(stringResource(R.string.bullet_item, it)) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    keyboardOptions: KeyboardOptions,
    @StringRes hint: Int? = null,
    suffix: String? = null,
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        isError = error != null,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = costingFieldColors(),
    )
}

private fun decimalKeyboard(imeAction: ImeAction) =
    KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction)

@Composable
private fun UnitSelector(selected: MeasureUnit, onSelect: (MeasureUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.ingredient_purchase_unit),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            purchaseUnits.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = unit == selected,
                    onClick = { onSelect(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = purchaseUnits.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    // No check mark: five buttons leave no room for it on a narrow phone.
                    icon = {},
                ) {
                    Text(stringResource(unit.labelRes))
                }
            }
        }
    }
}

@Composable
private fun UnitPriceCard(state: IngredientEditUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ingredient_unit_price),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.unitPrice?.let { unitPriceText(it, state.unit.baseUnit) }
                    ?: stringResource(R.string.value_missing),
                style = MaterialTheme.typography.titleLarge.merge(TabularFigures),
            )
        }
    }
}

@Composable
private fun UsedIn(recipes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (recipes.isEmpty()) {
                stringResource(R.string.ingredient_used_in_none)
            } else {
                pluralStringResource(R.plurals.ingredient_used_in, recipes.size, recipes.size)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recipes.forEach {
            Text(text = stringResource(R.string.bullet_item, it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
