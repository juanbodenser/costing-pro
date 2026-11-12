package com.jboden.costingpro.ui.recipes

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jboden.costingpro.R
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.common.labelRes
import com.jboden.costingpro.ui.theme.color
import java.io.File
import java.math.BigDecimal

@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    photoFile: (String) -> File,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicated: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnDuplicated by rememberUpdatedState(onDuplicated)

    // The recipe is gone (deleted here): close.
    LaunchedEffect(state.loading, summary == null) {
        if (!state.loading && summary == null) currentOnBack()
    }
    LaunchedEffect(state.duplicatedId) {
        state.duplicatedId?.let {
            viewModel.onDuplicateOpened()
            currentOnDuplicated(it)
        }
    }

    // The camera writes into a file we created; its path must survive if the app is recreated meanwhile.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingCameraPath?.let { viewModel.onCameraResult(it, success) }
        pendingCameraPath = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onGalleryPicked(uri)
    }
    val launchCamera = {
        val (path, uri) = viewModel.newCameraPhoto()
        pendingCameraPath = path
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCameraPath = null
            viewModel.onCameraResult(path, success = false)
            Toast.makeText(context, R.string.photo_camera_missing, Toast.LENGTH_LONG).show()
        }
    }
    val launchGallery = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            CostingTopBar(
                title = summary?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (summary != null) {
                        IconButton(onClick = { onEdit(summary.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
                        }
                        MoreMenu(
                            onDuplicate = { viewModel.duplicate(context.getString(R.string.recipe_copy_name, summary.name)) },
                            onDelete = viewModel::requestDelete,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (summary == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoSection(
                recipeName = summary.name,
                photoPath = summary.photoPath,
                photoFile = photoFile,
                onChoose = viewModel::openPhotoOptions,
                onRemove = viewModel::removePhoto,
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = summary.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(
                            R.string.recipe_yield_summary,
                            stringResource(
                                R.string.quantity_with_unit,
                                Formats.number(summary.yieldQuantity),
                                stringResource(summary.yieldUnit.labelRes),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SummaryCard(summary)
                BreakdownCard(
                    rows = state.breakdown,
                    sortByCost = state.sortByCost,
                    onToggleSort = viewModel::toggleSortByCost,
                )
                Column(modifier = Modifier.padding(bottom = 24.dp)) {}
            }
        }
    }

    if (state.photoOptionsOpen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.photo_options_title)) },
            text = {
                Column {
                    TextButton(onClick = launchCamera) { Text(stringResource(R.string.photo_camera)) }
                    TextButton(onClick = launchGallery) { Text(stringResource(R.string.photo_gallery)) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.confirmingDelete && summary != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_message, summary.name)) },
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
                    Text(stringResource(R.string.recipe_delete_blocked_message))
                    recipes.forEach { Text(stringResource(R.string.bullet_item, it)) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }

    if (state.photoFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            text = { Text(stringResource(R.string.photo_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
}

@Composable
private fun MoreMenu(onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_duplicate)) },
            onClick = {
                expanded = false
                onDuplicate()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

/** The photo at the top, or the button to add one. */
@Composable
private fun PhotoSection(
    recipeName: String,
    photoPath: String?,
    photoFile: (String) -> File,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    if (photoPath != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(
                model = photoFile(photoPath),
                contentDescription = stringResource(R.string.recipe_photo_description, recipeName),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentScale = ContentScale.Crop,
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onChoose) { Text(stringResource(R.string.photo_change)) }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.photo_remove)) }
            }
        }
    } else {
        SectionCard(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null)
                Text(text = stringResource(R.string.photo_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Margin as the biggest figure, then cost, price, VAT price and profit. */
@Composable
private fun SummaryCard(summary: RecipeSummary) {
    val price = summary.price
    val status = summary.status
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.result_margin),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = price?.marginPct?.let { Formats.percent(it) } ?: stringResource(R.string.value_missing),
                    style = MaterialTheme.typography.displaySmall.merge(TabularFigures),
                    fontWeight = FontWeight.SemiBold,
                    color = status?.color() ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            if (status != null) StatusLabel(status)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultFigure(R.string.detail_unit_cost, summary.cost.unitCost, Modifier.weight(1f))
            ResultFigure(R.string.detail_net_price, price?.netPrice, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultFigure(R.string.detail_price_with_vat, price?.priceWithVat, Modifier.weight(1f))
            ResultFigure(R.string.detail_profit_unit, price?.profitPerUnit, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BreakdownCard(rows: List<BreakdownRow>, sortByCost: Boolean, onToggleSort: () -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            FilterChip(
                selected = sortByCost,
                onClick = onToggleSort,
                label = { Text(stringResource(R.string.breakdown_sort)) },
            )
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            BreakdownLine(row)
        }
    }
}

@Composable
private fun BreakdownLine(row: BreakdownRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name ?: stringResource(R.string.breakdown_extras),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (row.quantity != null && row.unit != null) {
                Text(
                    text = stringResource(
                        R.string.quantity_with_unit,
                        Formats.number(row.quantity),
                        stringResource(row.unit.labelRes),
                    ),
                    style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = row.cost?.let { Formats.money(it) } ?: stringResource(R.string.value_missing),
            style = MaterialTheme.typography.bodyLarge.merge(TabularFigures),
            textAlign = TextAlign.End,
        )
        Text(
            text = row.sharePct?.let { Formats.percent(it) } ?: stringResource(R.string.value_missing),
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
