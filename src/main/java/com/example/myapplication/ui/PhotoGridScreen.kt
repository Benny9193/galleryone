package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.GalleryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGridScreen(
    title: String,
    items: List<GalleryItem>,
    onItemClick: (index: Int) -> Unit,
    onShareSelected: (List<GalleryItem>) -> Unit,
    onDeleteSelected: (List<GalleryItem>) -> Unit,
    onBack: () -> Unit,
) {
    val selected = remember { mutableStateMapOf<Long, GalleryItem>() }
    val selectionMode = selected.isNotEmpty()

    fun toggle(item: GalleryItem) {
        if (selected.containsKey(item.id)) selected.remove(item.id) else selected[item.id] = item
    }

    // In selection mode, Back exits selection rather than leaving the screen.
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
                            onShareSelected(selected.values.toList())
                            selected.clear()
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            onDeleteSelected(selected.values.toList())
                            selected.clear()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 2.dp,
                bottom = innerPadding.calculateBottomPadding() + 2.dp,
                start = 2.dp,
                end = 2.dp,
            ),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                MediaThumbnail(
                    item = item,
                    isSelected = selected.containsKey(item.id),
                    onClick = { if (selectionMode) toggle(item) else onItemClick(index) },
                    onLongClick = { toggle(item) },
                )
            }
        }
    }
}
