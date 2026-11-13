package com.jboden.costingpro.ui.recipes

import com.jboden.costingpro.ui.components.ResultCard
import com.jboden.costingpro.ui.components.SpanishLocale
import com.jboden.costingpro.ui.theme.FigureLabelStyle
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jboden.costingpro.R
import com.jboden.costingpro.domain.ExtraCostPer
import com.jboden.costingpro.domain.LineIssue
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.domain.MeasureUnit
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.common.costingFieldColors
import com.jboden.costingpro.ui.common.labelRes
import com.jboden.costingpro.ui.common.unitPriceText
import com.jboden.costingpro.ui.ingredients.searchKey
import com.jboden.costingpro.ui.theme.color
import java.math.BigDecimal

/** How many suggestions the autocomplete shows at most. */
private const val MAX_SUGGESTIONS = 5

@Composable
fun RecipeEditScreen(
    viewModel: RecipeEditViewModel,
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
                title = stringResource(if (state.isNew) R.string.recipe_new else R.string.recipe_edit),
                onBack = onDone,
            )
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold

        val form = state.form
        val errors = state.errors

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
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.recipe_name)) },
                supportingText = errors?.nameError?.let { { Text(stringResource(it.messageRes())) } },
                isError = errors?.nameError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                colors = costingFieldColors(),
            )

            YieldRow(
                quantity = form.yieldQuantity,
                unit = form.yieldUnit,
                onQuantityChange = viewModel::onYieldQuantityChange,
                onUnitChange = viewModel::onYieldUnitChange,
            )

            SectionTitle(R.string.recipe_ingredients)
            form.lines.forEachIndexed { row, line ->
                key(line.key) {
                    LineCard(
                        line = line,
                        result = state.result.lines[row],
                        info = state.rows[row],
                        errors = errors?.rowErrors?.get(line.key),
                        catalogue = state.catalogue,
                        onQueryChange = { viewModel.onQueryChange(line.key, it) },
                        onPick = { viewModel.onItemPicked(line.key, it) },
                        onQuantityChange = { viewModel.onLineQuantityChange(line.key, it) },
                        onUnitChange = { viewModel.onLineUnitChange(line.key, it) },
                        onWasteChange = { viewModel.onLineWasteChange(line.key, it) },
                        onRemove = { viewModel.removeLine(line.key) },
                    )
                }
            }
            if (errors?.noLines == true) {
                Text(
                    text = stringResource(R.string.error_no_lines),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = viewModel::addLine, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.recipe_add_ingredient),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            SectionTitle(R.string.recipe_extras)
            Text(
                text = stringResource(R.string.extras_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.extras.forEach { extra ->
                key(extra.key) {
                    ExtraCard(
                        extra = extra,
                        errors = errors?.extraErrors?.get(extra.key),
                        onNameChange = { viewModel.onExtraNameChange(extra.key, it) },
                        onAmountChange = { viewModel.onExtraAmountChange(extra.key, it) },
                        onPerChange = { viewModel.onExtraPerChange(extra.key, it) },
                        onRemove = { viewModel.removeExtra(extra.key) },
                    )
                }
            }
            OutlinedButton(onClick = viewModel::addExtra, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.recipe_add_extra),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            TotalsCard(state.result)

            PricingSection(
                fields = state.result.fields,
                vat = form.vatPct,
                vatError = errors?.vatError,
                onPriceChange = viewModel::onPriceChange,
                onMarkupChange = viewModel::onMarkupChange,
                onFoodCostChange = viewModel::onFoodCostChange,
                onVatChange = viewModel::onVatChange,
                onPriceWithVatChange = viewModel::onPriceWithVatChange,
            )

            EditorResultCard(state.result)

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun YieldRow(
    quantity: String,
    unit: MeasureUnit,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (MeasureUnit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        NumberField(
            value = quantity,
            onValueChange = onQuantityChange,
            label = R.string.recipe_yield,
            error = null,
            modifier = Modifier.weight(1f),
        )
        UnitDropdown(
            selected = unit,
            units = MeasureUnit.entries,
            onSelect = onUnitChange,
            modifier = Modifier.weight(1f),
        )
    }
}

private val CardShape = RoundedCornerShape(12.dp)

/** Lets the user delete a card by swiping it to the left. */
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        },
    ) {
        content()
    }
}

/** One extra cost: a name, an amount without VAT, and whether it is paid once per batch or per unit. */
@Composable
private fun ExtraCard(
    extra: ExtraForm,
    errors: ExtraErrors?,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onPerChange: (ExtraCostPer) -> Unit,
    onRemove: () -> Unit,
) {
    SwipeToRemove(onRemove = onRemove) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = extra.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.extra_name)) },
                    supportingText = errors?.name?.let { { Text(stringResource(it.messageRes())) } },
                    isError = errors?.name != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    colors = costingFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    NumberField(
                        value = extra.amount,
                        onValueChange = onAmountChange,
                        label = R.string.extra_amount,
                        error = errors?.amount,
                        suffix = stringResource(R.string.currency_symbol),
                        modifier = Modifier.weight(1f),
                    )
                    OptionDropdown(
                        selected = extra.per,
                        options = ExtraCostPer.entries,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelect = onPerChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@StringRes
private fun ExtraCostPer.labelRes(): Int = when (this) {
    ExtraCostPer.BATCH -> R.string.extra_per_batch
    ExtraCostPer.UNIT -> R.string.extra_per_unit
}

@Composable
private fun LineCard(
    line: LineForm,
    result: LineResult,
    info: RowInfo,
    errors: RowErrors?,
    catalogue: List<CatalogueItem>,
    onQueryChange: (String) -> Unit,
    onPick: (CatalogueItem) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (MeasureUnit) -> Unit,
    onWasteChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    SwipeToRemove(onRemove = onRemove) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IngredientField(
                    line = line,
                    error = errors?.ingredient,
                    catalogue = catalogue,
                    onQueryChange = onQueryChange,
                    isRecipe = info.isRecipe,
                    onPick = onPick,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    NumberField(
                        value = line.quantity,
                        onValueChange = onQuantityChange,
                        label = R.string.line_quantity,
                        error = errors?.quantity,
                        modifier = Modifier.weight(1.2f),
                    )
                    UnitDropdown(
                        selected = line.unit,
                        units = info.units,
                        onSelect = onUnitChange,
                        modifier = Modifier.weight(0.8f),
                    )
                    NumberField(
                        value = line.waste,
                        onValueChange = onWasteChange,
                        label = R.string.line_waste,
                        error = errors?.waste,
                        suffix = stringResource(R.string.percent_symbol),
                        modifier = Modifier.weight(1f),
                    )
                }
                LineCostRow(result)
                if (errors?.unit != null || result.issue == LineIssue.UNIT_MISMATCH) {
                    Text(
                        text = stringResource(R.string.error_unit_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The ingredient field with its suggestions below, shown while the field has focus and no ingredient is chosen. */
@Composable
private fun IngredientField(
    line: LineForm,
    error: FieldError?,
    catalogue: List<CatalogueItem>,
    isRecipe: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (CatalogueItem) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val suggestions = remember(catalogue, line.query) {
        val needle = line.query.searchKey()
        catalogue.filter { needle in it.name.searchKey() }.take(MAX_SUGGESTIONS)
    }

    Column {
        OutlinedTextField(
            value = line.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            label = { Text(stringResource(R.string.line_ingredient)) },
            supportingText = when {
                error != null -> {
                    { Text(stringResource(error.messageRes())) }
                }
                // A row that uses another recipe says so, in the colour of sub-recipes.
                isRecipe -> {
                    { Text(stringResource(R.string.line_sub_recipe), color = MaterialTheme.colorScheme.tertiary) }
                }
                else -> null
            },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            colors = costingFieldColors(),
        )
        if (focused && !line.hasSource && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                suggestions.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                        if (item.isRecipe) {
                            Text(
                                text = stringResource(R.string.suggestion_recipe),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineCostRow(result: LineResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (result.unitPrice != null && result.baseUnit != null) {
                unitPriceText(result.unitPrice, result.baseUnit)
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = result.cost?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
            style = MaterialTheme.typography.titleMedium.merge(TabularFigures),
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: FieldError?,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    // Text that is not a number is flagged right away; "required" waits for the first save attempt.
    val shown = error ?: FieldError.NUMBER.takeIf { value.isNotBlank() && Formats.parseDecimal(value) == null }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(stringResource(label), maxLines = 1) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = shown?.let { { Text(stringResource(it.messageRes())) } },
        isError = shown != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        colors = costingFieldColors(),
    )
}

@Composable
private fun UnitDropdown(
    selected: MeasureUnit,
    units: List<MeasureUnit>,
    onSelect: (MeasureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionDropdown(
        selected = selected,
        options = units,
        optionLabel = { stringResource(it.labelRes) },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** A button that shows the chosen option and opens a menu with all of them. */
@Composable
private fun <T> OptionDropdown(
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // The top padding matches the space the floating label takes above a text field, so both line up.
    Box(modifier.padding(top = 8.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = options.size > 1,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(optionLabel(selected), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(result: EditorResult) {
    val cost = result.cost
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.4f))
            ColumnHeader(R.string.totals_batch)
            ColumnHeader(R.string.totals_unit)
        }
        // Raw material and extras are listed apart only when there are extras to tell them from.
        if (cost.extrasCost.signum() != 0) {
            AmountRow(R.string.totals_raw_material, cost.rawMaterialCost, cost.rawMaterialUnitCost)
            AmountRow(R.string.totals_extras, cost.extrasCost, cost.extrasUnitCost)
        }
        AmountRow(R.string.totals_cost, cost.batchCost, cost.unitCost, emphasis = true)
    }
}

@Composable
private fun PricingSection(
    fields: PriceFields,
    vat: String,
    vatError: FieldError?,
    onPriceChange: (String) -> Unit,
    onMarkupChange: (String) -> Unit,
    onFoodCostChange: (String) -> Unit,
    onVatChange: (String) -> Unit,
    onPriceWithVatChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.pricing_title)
        Text(
            text = stringResource(R.string.pricing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The amber bar marks the selling price; the colour is for this meaning only.
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.secondary),
            )
            NumberField(
                value = fields.price,
                onValueChange = onPriceChange,
                label = R.string.pricing_net_price,
                error = null,
                suffix = stringResource(R.string.currency_symbol),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = fields.markup,
                onValueChange = onMarkupChange,
                label = R.string.pricing_markup,
                error = null,
                suffix = stringResource(R.string.percent_symbol),
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = fields.foodCost,
                onValueChange = onFoodCostChange,
                label = R.string.pricing_food_cost,
                error = null,
                suffix = stringResource(R.string.percent_symbol),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = vat,
                onValueChange = onVatChange,
                label = R.string.pricing_vat,
                error = vatError,
                suffix = stringResource(R.string.percent_symbol),
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = fields.priceWithVat,
                onValueChange = onPriceWithVatChange,
                label = R.string.pricing_price_with_vat,
                error = null,
                suffix = stringResource(R.string.currency_symbol),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The shared result card, with the profit per batch and the food cost under the grid. */
@Composable
private fun EditorResultCard(result: EditorResult) {
    val price = result.price
    ResultCard(
        marginPct = price?.marginPct,
        status = result.status,
        unitCost = result.cost.unitCost,
        netPrice = price?.netPrice,
        priceWithVat = price?.priceWithVat,
        profitPerUnit = price?.profitPerUnit,
        footer = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.result_profit_batch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = price?.profitPerBatch?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            price?.foodCostPct?.let {
                Text(
                    text = stringResource(R.string.result_food_cost, Formats.percent(it)),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun RowScope.ColumnHeader(@StringRes label: Int) {
    Text(
        text = stringResource(label).uppercase(SpanishLocale),
        modifier = Modifier.weight(1f),
        style = FigureLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun AmountRow(@StringRes label: Int, batch: BigDecimal?, unit: BigDecimal?, emphasis: Boolean = false) {
    val amountStyle = (if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        .merge(TabularFigures)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            modifier = Modifier.weight(1.4f),
            style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (emphasis) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = batch?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
            modifier = Modifier.weight(1f),
            style = amountStyle,
            textAlign = TextAlign.End,
        )
        Text(
            text = unit?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
            modifier = Modifier.weight(1f),
            style = amountStyle,
            textAlign = TextAlign.End,
        )
    }
}

@StringRes
private fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.error_required
    FieldError.NUMBER -> R.string.error_number
    FieldError.QUANTITY_ZERO -> R.string.error_quantity_zero
    FieldError.WASTE_RANGE -> R.string.error_waste_range
    FieldError.NOT_IN_CATALOGUE -> R.string.error_pick_ingredient
    FieldError.UNIT_MISMATCH -> R.string.error_unit_mismatch
    FieldError.CYCLE -> R.string.error_cycle
    FieldError.VAT_RANGE -> R.string.error_vat_range
}
