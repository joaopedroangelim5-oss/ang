package com.angvelda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.core.tween
import com.angvelda.app.ui.AngVeldaTheme
import com.angvelda.app.ui.EditorScreen
import com.angvelda.app.ui.LibraryScreen
import com.angvelda.app.ui.SettingsScreen
import com.angvelda.app.ui.TopicDetailScreen
import com.angvelda.app.ui.TopicsScreen
import com.angvelda.app.ui.VaultScreen
import com.angvelda.app.ui.WebScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AngVeldaTheme(vm.prefs) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot(vm) }
            }
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

fun navigateTab(nav: NavController, route: String) {
    nav.navigate(route) {
        popUpTo("web") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = listOf(
        NavTab("web", "Teia", Icons.Filled.Hub),
        NavTab("topics", "Tópicos", Icons.Filled.AccountTree),
        NavTab("library", "Biblioteca", Icons.Filled.Book),
        NavTab("vault", "Cofre", Icons.Filled.Lock)
    )
    val showBar = route in tabs.map { it.route }
    val anim = vm.prefs.animations

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { navigateTab(nav, t.route) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "web",
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { if (anim) fadeIn(tween(200)) else EnterTransition.None },
            exitTransition = { if (anim) fadeOut(tween(150)) else ExitTransition.None },
            popEnterTransition = { if (anim) fadeIn(tween(200)) else EnterTransition.None },
            popExitTransition = { if (anim) fadeOut(tween(150)) else ExitTransition.None }
        ) {
            composable("web") { WebScreen(vm, nav) }
            composable("topics") { TopicsScreen(vm, nav) }
            composable("library") { LibraryScreen(vm, nav) }
            composable("vault") { VaultScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("topic/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                TopicDetailScreen(vm, nav, e.arguments?.getString("id") ?: "none")
            }
            composable(
                "editor/{entryId}?topicId={topicId}&type={type}",
                arguments = listOf(
                    navArgument("entryId") { type = NavType.StringType },
                    navArgument("topicId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "" }
                )
            ) { e ->
                EditorScreen(
                    vm, nav,
                    e.arguments?.getString("entryId") ?: "new",
                    e.arguments?.getString("topicId") ?: "",
                    e.arguments?.getString("type") ?: ""
                )
            }
        }
    }
}
