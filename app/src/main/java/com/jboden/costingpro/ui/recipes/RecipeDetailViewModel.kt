package com.jboden.costingpro.ui.recipes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.data.DeleteResult
import com.jboden.costingpro.data.IngredientRepository
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.data.RecipeRepository
import com.jboden.costingpro.export.RecipePdf
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val loading: Boolean = true,
    /** Null while loading, and after the recipe has been deleted: the screen must close. */
    val summary: RecipeSummary? = null,
    /** Breakdown rows from the most expensive to the cheapest, instead of recipe order. */
    val sortByCost: Boolean = false,
    val photoOptionsOpen: Boolean = false,
    val confirmingDelete: Boolean = false,
    /** Non-null while the "cannot delete, it is in use" dialog is open: the recipes that use this one. */
    val deleteBlockedBy: List<String>? = null,
    /** The id of the copy just made, until the screen has opened it. */
    val duplicatedId: Long? = null,
    /** The picked image could not be read. */
    val photoFailed: Boolean = false,
    /** Result of the last PDF export, until the screen has told the user. */
    val pdfResult: Boolean? = null,
) {
    /** The breakdown in the order the user chose. */
    val breakdown: List<BreakdownRow>
        get() = summary?.breakdown.orEmpty().let { if (sortByCost) RecipeSummaryLogic.sortedByCost(it) else it }
}

class RecipeDetailViewModel(
    private val recipes: RecipeRepository,
    ingredients: IngredientRepository,
    private val photos: PhotoStore,
    private val recipeId: Long,
    /** Created only when the user exports a PDF. */
    createPdf: () -> RecipePdf = { error("No PDF exporter") },
) : ViewModel() {

    private val pdf by lazy(createPdf)

    /** Everything on screen that does not come from the database. */
    private data class Local(
        val sortByCost: Boolean = false,
        val photoOptionsOpen: Boolean = false,
        val confirmingDelete: Boolean = false,
        val deleteBlockedBy: List<String>? = null,
        val duplicatedId: Long? = null,
        val photoFailed: Boolean = false,
        val pdfResult: Boolean? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<RecipeDetailUiState> =
        combine(recipes.observeAll(), ingredients.observeAll(), local) { allRecipes, catalogue, local ->
            // All recipes are observed, not just this one: a change in a sub-recipe changes this cost.
            val byId = allRecipes.associateBy { it.recipe.id }
            RecipeDetailUiState(
                loading = false,
                summary = byId[recipeId]?.let { RecipeSummaryLogic.summarize(it, catalogue.associateBy { it.id }, byId) },
                sortByCost = local.sortByCost,
                photoOptionsOpen = local.photoOptionsOpen,
                confirmingDelete = local.confirmingDelete,
                deleteBlockedBy = local.deleteBlockedBy,
                duplicatedId = local.duplicatedId,
                photoFailed = local.photoFailed,
                pdfResult = local.pdfResult,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun toggleSortByCost() = local.update { it.copy(sortByCost = !it.sortByCost) }

    fun openPhotoOptions() = local.update { it.copy(photoOptionsOpen = true) }

    fun dismissDialogs() = local.update {
        it.copy(photoOptionsOpen = false, confirmingDelete = false, deleteBlockedBy = null, photoFailed = false)
    }

    // ---------- Photo ----------

    /** Prepares the file the camera app will write to. Returns its stored path and the URI to give the camera. */
    fun newCameraPhoto(): Pair<String, Uri> {
        local.update { it.copy(photoOptionsOpen = false) }
        val path = photos.newPath()
        return path to photos.cameraUri(path)
    }

    /** The camera app returned. A cancelled shot leaves an empty file, which is removed. */
    fun onCameraResult(path: String, success: Boolean) {
        viewModelScope.launch {
            if (success && photos.hasPhoto(path)) setPhoto(path) else photos.delete(path)
        }
    }

    /** The user picked an image in the gallery, or closed the picker (null). */
    fun onGalleryPicked(uri: Uri?) {
        local.update { it.copy(photoOptionsOpen = false) }
        if (uri == null) return
        viewModelScope.launch {
            val path = photos.import(uri)
            if (path == null) local.update { it.copy(photoFailed = true) } else setPhoto(path)
        }
    }

    fun removePhoto() {
        local.update { it.copy(photoOptionsOpen = false) }
        viewModelScope.launch { setPhoto(null) }
    }

    /** Stores the new photo path and deletes the file of the previous photo. */
    private suspend fun setPhoto(path: String?) {
        photos.delete(recipes.setPhoto(recipeId, path))
    }

    // ---------- PDF ----------

    /** The user chose where to save the PDF, or closed the picker (null). */
    fun exportPdf(uri: Uri?) {
        val summary = uiState.value.summary
        if (uri == null || summary == null) return
        viewModelScope.launch {
            val ok = try {
                pdf.export(summary, uri)
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
            local.update { it.copy(pdfResult = ok) }
        }
    }

    fun onPdfResultShown() = local.update { it.copy(pdfResult = null) }

    // ---------- Duplicate and delete ----------

    fun duplicate(newName: String) {
        viewModelScope.launch {
            val id = recipes.duplicate(recipeId, newName)
            local.update { it.copy(duplicatedId = id) }
        }
    }

    fun onDuplicateOpened() = local.update { it.copy(duplicatedId = null) }

    fun requestDelete() = local.update { it.copy(confirmingDelete = true) }

    fun confirmDelete() {
        local.update { it.copy(confirmingDelete = false) }
        viewModelScope.launch {
            val photoPath = recipes.get(recipeId)?.recipe?.photoPath
            when (val result = recipes.delete(recipeId)) {
                // The screen closes by itself: the recipe is gone, so the summary becomes null.
                DeleteResult.Deleted -> photos.delete(photoPath)
                is DeleteResult.InUse -> local.update { it.copy(deleteBlockedBy = result.recipes.map { r -> r.name }) }
            }
        }
    }

    companion object {
        fun factory(
            recipes: RecipeRepository,
            ingredients: IngredientRepository,
            photos: PhotoStore,
            recipeId: Long,
            createPdf: () -> RecipePdf,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeDetailViewModel(recipes, ingredients, photos, recipeId, createPdf) }
        }
    }
}
