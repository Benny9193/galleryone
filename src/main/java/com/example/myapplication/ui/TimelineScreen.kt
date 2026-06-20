package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
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
import com.example.myapplication.data.MediaFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    items: List<GalleryItem>,
    filter: MediaFilter,
    onFilterChange: (MediaFilter) -> Unit,
    onItemClick: (index: Int) -> Unit,
    onShareSelected: (List<GalleryItem>) -> Unit,
    onDeleteSelected: (List<GalleryItem>) -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val selected = remember { mutableStateMapOf<Long, GalleryItem>() }
    val selectionMode = selected.isNotEmpty()

    fun toggle(item: GalleryItem) {
        if (selected.containsKey(item.id)) selected.remove(item.id) else selected[item.id] = item
    }

    BackHandler(enabled = selectionMode) { selected.clear() }

    // Group items (already newest-first) by calendar day, preserving order.
    val grouped = remember(items) {
        items.withIndex().groupBy { (_, item) ->
            Instant.ofEpochSecond(item.dateAdded).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

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
                    title = { Text("Photos") },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
            FilterChipsRow(filter = filter, onFilterChange = onFilterChange)
            if (items.isEmpty()) {
                EmptyMessage(
                    "No photos or videos yet.",
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding.calculateBottomPadding()),
                )
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 2.dp,
                    end = 2.dp,
                    top = 2.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 8.dp,
                ),
            ) {
                grouped.forEach { (date, entries) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = formatDateHeader(date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(
                        count = entries.size,
                        key = { entries[it].value.id },
                    ) { i ->
                        val (originalIndex, item) = entries[i]
                        MediaThumbnail(
                            item = item,
                            isSelected = selected.containsKey(item.id),
                            onClick = {
                                if (selectionMode) toggle(item) else onItemClick(originalIndex)
                            },
                            onLongClick = { toggle(item) },
                        )
                    }
                }
            }
        }
    }
}

private val headerFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(headerFormatter)
    }
}
