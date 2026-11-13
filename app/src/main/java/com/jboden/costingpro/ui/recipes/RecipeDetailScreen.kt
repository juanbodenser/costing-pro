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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jboden.costingpro.R
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.TabularFigures
import com.jboden.costingpro.ui.common.costingChipColors
import com.jboden.costingpro.ui.common.labelRes
import com.jboden.costingpro.domain.Allergen
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.jboden.costingpro.ui.components.AllergenChip
import com.jboden.costingpro.ui.components.MarginPill
import com.jboden.costingpro.ui.components.ResultCard
import com.jboden.costingpro.ui.theme.RecipeTitleStyle
import java.io.File

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
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        viewModel.exportPdf(uri)
    }
    LaunchedEffect(state.pdfResult) {
        state.pdfResult?.let { ok ->
            Toast.makeText(context, if (ok) R.string.pdf_done else R.string.pdf_failed, Toast.LENGTH_LONG).show()
            viewModel.onPdfResultShown()
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
                            hasPhoto = summary.photoPath != null,
                            onChoosePhoto = viewModel::openPhotoOptions,
                            onRemovePhoto = viewModel::removePhoto,
                            onExportPdf = {
                                try {
                                    savePdf.launch(pdfFileName(summary.name))
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, R.string.pdf_failed, Toast.LENGTH_LONG).show()
                                }
                            },
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
            RecipeHeader(
                summary = summary,
                photoFile = photoFile,
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ResultCard(
                    marginPct = summary.price?.marginPct,
                    status = summary.status,
                    unitCost = summary.cost.unitCost,
                    netPrice = summary.price?.netPrice,
                    priceWithVat = summary.price?.priceWithVat,
                    profitPerUnit = summary.price?.profitPerUnit,
                )
                AllergensBlock(summary.allergens)
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
private fun MoreMenu(
    hasPhoto: Boolean,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onExportPdf: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(if (hasPhoto) R.string.photo_change else R.string.photo_add)) },
            onClick = {
                expanded = false
                onChoosePhoto()
            },
        )
        if (hasPhoto) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_remove)) },
                onClick = {
                    expanded = false
                    onRemovePhoto()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_export_pdf)) },
            onClick = {
                expanded = false
                onExportPdf()
            },
        )
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

/** A file name for the PDF of a recipe: its name without the characters file systems reject. */
internal fun pdfFileName(recipeName: String): String {
    val clean = recipeName.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").trim().replace(Regex("""\s+"""), " ")
    return "${clean.ifEmpty { "escandallo" }}.pdf"
}

/**
 * The top of the screen. With a photo: the photo cropped to 16:9, with the name, the yield chip
 * and the margin pill over a dark gradient at its bottom. Without one: the name and the yield
 * chip. Changing or removing the photo lives in the more-actions menu, not here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeHeader(
    summary: RecipeSummary,
    photoFile: (String) -> File,
) {
    val yieldText = stringResource(
        R.string.recipe_yield_summary,
        stringResource(
            R.string.quantity_with_unit,
            Formats.number(summary.yieldQuantity),
            stringResource(summary.yieldUnit.labelRes),
        ),
    )
    val photoPath = summary.photoPath
    if (photoPath != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    model = photoFile(photoPath),
                    contentDescription = stringResource(R.string.recipe_photo_description, summary.name),
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                )
                FlowRow(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = summary.name,
                        style = RecipeTitleStyle,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    YieldChip(yieldText, overPhoto = true)
                    summary.status?.let { MarginPill(it, marginPct = summary.price?.marginPct) }
                }
            }
        }
    } else {
        FlowRow(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = summary.name, style = RecipeTitleStyle)
            YieldChip(yieldText, overPhoto = false)
        }
    }
}

/** The yield of the recipe as a small chip next to its name. */
@Composable
private fun YieldChip(text: String, overPhoto: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .background(
                if (overPhoto) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge.merge(TabularFigures),
        color = if (overPhoto) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                colors = costingChipColors(),
            )
        }
        Column {
            rows.forEachIndexed { index, row ->
                BreakdownLine(
                    row = row,
                    modifier = Modifier
                        .background(
                            if (index % 2 == 1) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BreakdownLine(row: BreakdownRow, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name ?: stringResource(R.string.breakdown_extras),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (row.isExtra) {
                Text(
                    text = stringResource(R.string.breakdown_extra),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.isSubRecipe) {
                Text(
                    text = stringResource(R.string.line_sub_recipe),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
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

/**
 * The allergens of the recipe, inherited from its ingredients and sub-recipes, as filled plum
 * chips with icons on a block with its own background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergensBlock(allergens: Set<Allergen>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_allergens),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (allergens.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_no_allergens),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allergens.forEach { AllergenChip(it) }
            }
        }
    }
}
