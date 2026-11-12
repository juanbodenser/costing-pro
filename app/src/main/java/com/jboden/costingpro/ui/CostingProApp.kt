package com.jboden.costingpro.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jboden.costingpro.AppContainer
import com.jboden.costingpro.R
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.ingredients.IngredientEditScreen
import com.jboden.costingpro.ui.ingredients.IngredientEditViewModel
import com.jboden.costingpro.ui.ingredients.IngredientListScreen
import com.jboden.costingpro.ui.ingredients.IngredientListViewModel

private object Routes {
    const val RECIPES = "recipes"
    const val INGREDIENTS = "ingredients"
    const val SETTINGS = "settings"

    const val INGREDIENT_ID = "ingredientId"
    const val INGREDIENT = "ingredient/{$INGREDIENT_ID}"

    /** @param id the ingredient to edit, or [IngredientEditViewModel.NEW_ID] to create one. */
    fun ingredient(id: Long) = "ingredient/$id"
}

/** The three tabs of the bottom bar. */
private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    RECIPES(Routes.RECIPES, R.string.tab_recipes, R.drawable.ic_tab_recipes),
    INGREDIENTS(Routes.INGREDIENTS, R.string.tab_ingredients, R.drawable.ic_tab_ingredients),
    SETTINGS(Routes.SETTINGS, R.string.tab_settings, R.drawable.ic_tab_settings),
}

/** Root of the UI: the navigation graph and the bottom bar, shown only on the three tabs. */
@Composable
fun CostingProApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        // Each screen has its own Scaffold that deals with the system bars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.route == currentRoute,
                            onClick = { navController.switchTab(tab) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RECIPES,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.RECIPES) {
                PlaceholderScreen(title = R.string.tab_recipes, message = R.string.recipes_placeholder)
            }
            composable(Routes.INGREDIENTS) {
                val viewModel: IngredientListViewModel =
                    viewModel(factory = IngredientListViewModel.factory(container.ingredientRepository))
                IngredientListScreen(
                    viewModel = viewModel,
                    onAdd = { navController.navigate(Routes.ingredient(IngredientEditViewModel.NEW_ID)) },
                    onOpen = { id -> navController.navigate(Routes.ingredient(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                PlaceholderScreen(title = R.string.tab_settings, message = R.string.settings_placeholder)
            }
            composable(
                route = Routes.INGREDIENT,
                arguments = listOf(navArgument(Routes.INGREDIENT_ID) { type = NavType.LongType }),
            ) { entry ->
                val ingredientId = entry.arguments?.getLong(Routes.INGREDIENT_ID) ?: IngredientEditViewModel.NEW_ID
                val viewModel: IngredientEditViewModel = viewModel(
                    factory = IngredientEditViewModel.factory(container.ingredientRepository, ingredientId),
                )
                IngredientEditScreen(
                    viewModel = viewModel,
                    onDone = {
                        // Only the screen on top may close itself: ignores a second tap during the transition.
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navController.popBackStack()
                    },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Stands in for a tab whose screen belongs to a later phase. */
@Composable
private fun PlaceholderScreen(@StringRes title: Int, @StringRes message: Int) {
    Scaffold(topBar = { CostingTopBar(title = stringResource(title)) }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
