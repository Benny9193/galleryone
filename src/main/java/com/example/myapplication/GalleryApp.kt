package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.data.GalleryItem
import com.example.myapplication.data.StartTab
import com.example.myapplication.ui.AlbumsScreen
import com.example.myapplication.ui.PhotoGridScreen
import com.example.myapplication.ui.PhotoViewerScreen
import com.example.myapplication.ui.SettingsScreen
import com.example.myapplication.ui.TimelineScreen
import com.example.myapplication.ui.TrashScreen
import java.util.ArrayList

private val REQUIRED_PERMISSIONS = arrayOf(
    android.Manifest.permission.READ_MEDIA_IMAGES,
    android.Manifest.permission.READ_MEDIA_VIDEO,
)

private fun hasMediaPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun GalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        hasPermission = granted
        if (granted) {
            viewModel.loadAll()
        } else {
            // If the system will no longer show a prompt, the user must enable it in Settings.
            val activity = context as? Activity
            val canAskAgain = activity != null &&
                REQUIRED_PERMISSIONS.any { activity.shouldShowRequestPermissionRationale(it) }
            permanentlyDenied = !canAskAgain
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.loadAll() else permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    when {
        hasPermission -> GalleryNavHost(viewModel)
        permanentlyDenied -> PermissionDenied(onOpenSettings = { openAppSettings(context) })
        else -> PermissionRationale(onGrant = { permissionLauncher.launch(REQUIRED_PERMISSIONS) })
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    context.startActivity(intent)
}

private const val ROUTE_TIMELINE = "timeline"
private const val ROUTE_ALBUMS = "albums"
private const val ROUTE_TRASH = "trash"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun GalleryNavHost(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val pendingIntent by viewModel.pendingIntent.collectAsStateWithLifecycle()
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Launch the system trash/untrash confirmation when the ViewModel requests one.
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onPendingHandled(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(pendingIntent) {
        pendingIntent?.let { sender ->
            confirmLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // One-shot snackbars: a confirmed delete is undoable (restores from trash).
    LaunchedEffect(snack) {
        when (val s = snack) {
            is GallerySnack.Deleted -> {
                val label = if (s.count == 1) "1 item deleted" else "${s.count} items deleted"
                val result = snackbarHostState.showSnackbar(
                    message = label,
                    actionLabel = "Undo",
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.requestUntrash()
            }

            GallerySnack.Restored -> snackbarHostState.showSnackbar("Restored")
            is GallerySnack.PermanentlyDeleted -> {
                val label = if (s.count == 1) "1 item permanently deleted"
                else "${s.count} items permanently deleted"
                snackbarHostState.showSnackbar(label)
            }

            null -> {}
        }
        if (snack != null) viewModel.onSnackShown()
    }

    // Wait for persisted settings so the start tab and theme are correct on first frame.
    val appSettings = settings
    if (appSettings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val startDestination =
        if (appSettings.startTab == StartTab.ALBUMS) ROUTE_ALBUMS else ROUTE_TIMELINE

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute == ROUTE_TIMELINE ||
        currentRoute == ROUTE_ALBUMS ||
        currentRoute == ROUTE_TRASH

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_TIMELINE,
                        onClick = { navController.switchTab(ROUTE_TIMELINE) },
                        icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        label = { Text("Photos") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_ALBUMS,
                        onClick = { navController.switchTab(ROUTE_ALBUMS) },
                        icon = { Icon(Icons.Filled.PhotoAlbum, contentDescription = null) },
                        label = { Text("Albums") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_TRASH,
                        onClick = { navController.switchTab(ROUTE_TRASH) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (trash.isNotEmpty()) Badge { Text("${trash.size}") }
                                }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            }
                        },
                        label = { Text("Trash") },
                    )
                }
            }
        }
    ) { outerPadding ->
        NavHost(navController = navController, startDestination = startDestination) {
            composable(ROUTE_TIMELINE) {
                TimelineScreen(
                    items = timeline,
                    filter = filter,
                    onFilterChange = { viewModel.setFilter(it) },
                    onItemClick = { index -> navController.navigate("viewer/timeline/$index") },
                    onShareSelected = { items -> shareItems(context, items) },
                    onDeleteSelected = { items -> viewModel.requestTrash(items) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    bottomPadding = outerPadding,
                )
            }
            composable(ROUTE_ALBUMS) {
                AlbumsScreen(
                    albums = albums,
                    filter = filter,
                    onFilterChange = { viewModel.setFilter(it) },
                    onAlbumClick = { album ->
                        viewModel.loadMedia(album.bucketId)
                        navController.navigate(
                            "grid/${album.bucketId}/${android.net.Uri.encode(album.name)}"
                        )
                    },
                    bottomPadding = outerPadding,
                )
            }
            composable(ROUTE_TRASH) {
                TrashScreen(
                    items = trash,
                    onRestore = { items -> viewModel.requestUntrash(items) },
                    onDeleteForever = { items -> viewModel.requestPermanentDelete(items) },
                    bottomPadding = outerPadding,
                )
            }
            composable(
                route = "grid/{bucketId}/{title}",
                arguments = listOf(
                    navArgument("bucketId") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType },
                )
            ) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: "Album"
                PhotoGridScreen(
                    title = title,
                    items = media,
                    onItemClick = { index -> navController.navigate("viewer/album/$index") },
                    onShareSelected = { items -> shareItems(context, items) },
                    onDeleteSelected = { items -> viewModel.requestTrash(items) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "viewer/{source}/{startIndex}",
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("startIndex") { type = NavType.IntType },
                )
            ) { backStackEntry ->
                val source = backStackEntry.arguments?.getString("source") ?: "album"
                val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
                val list = if (source == "timeline") timeline else media
                PhotoViewerScreen(
                    items = list,
                    startIndex = startIndex,
                    autoPlayVideos = appSettings.autoPlayVideos,
                    onShare = { item -> shareItems(context, listOf(item)) },
                    onDelete = { item -> viewModel.requestTrash(listOf(item)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    settings = appSettings,
                    onThemeChange = { viewModel.setTheme(it) },
                    onStartTabChange = { viewModel.setStartTab(it) },
                    onAutoPlayChange = { viewModel.setAutoPlayVideos(it) },
                    onDynamicColorChange = { viewModel.setDynamicColor(it) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun shareItems(context: Context, items: List<GalleryItem>) {
    if (items.isEmpty()) return
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType()
            putExtra(Intent.EXTRA_STREAM, items.first().uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.all { it.isVideo } -> "video/*"
                items.none { it.isVideo } -> "image/*"
                else -> "*/*"
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun GalleryItem.mimeType(): String = if (isVideo) "video/*" else "image/*"

@Composable
private fun PermissionRationale(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Photo access is needed to show your gallery.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant) {
            Text("Grant access")
        }
    }
}

@Composable
private fun PermissionDenied(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Photo access is turned off. Enable it in Settings to see your gallery.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenSettings) {
            Text("Open settings")
        }
    }
}
