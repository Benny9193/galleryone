package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.GalleryItem
import kotlin.math.ceil

/** "Nd left" until the trashed item is auto-removed, or null if unknown. */
private fun daysLeftLabel(expiresAtSeconds: Long): String? {
    if (expiresAtSeconds <= 0) return null
    val nowSeconds = System.currentTimeMillis() / 1000
    val days = ceil((expiresAtSeconds - nowSeconds) / 86_400.0).toInt().coerceAtLeast(0)
    return "${days}d left"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    items: List<GalleryItem>,
    onRestore: (List<GalleryItem>) -> Unit,
    onDeleteForever: (List<GalleryItem>) -> Unit,
    bottomPadding: PaddingValues,
) {
    val selected = remember { mutableStateMapOf<Long, GalleryItem>() }
    val selectionMode = selected.isNotEmpty()

    fun toggle(item: GalleryItem) {
        if (selected.containsKey(item.id)) selected.remove(item.id) else selected[item.id] = item
    }

    BackHandler(enabled = selectionMode) { selected.clear() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            onRestore(selected.values.toList())
                            selected.clear()
                        }) {
                            Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restore")
                        }
                        IconButton(onClick = {
                            onDeleteForever(selected.values.toList())
                            selected.clear()
                        }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Delete forever")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Recently deleted") },
                    actions = {
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { onDeleteForever(items) }) {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    contentDescription = "Empty trash",
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No recently deleted items.\nDeleted photos and videos appear here and " +
                        "are removed automatically after about 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 2.dp,
                end = 2.dp,
                top = 2.dp,
                bottom = bottomPadding.calculateBottomPadding() + 8.dp,
            ),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { _, item ->
                MediaThumbnail(
                    item = item,
                    isSelected = selected.containsKey(item.id),
                    onClick = { toggle(item) },
                    onLongClick = { toggle(item) },
                    caption = daysLeftLabel(item.expiresAt),
                )
            }
        }
    }
}
