package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import com.example.myapplication.data.Album
import com.example.myapplication.data.AppSettings
import com.example.myapplication.data.GalleryItem
import com.example.myapplication.data.MediaFilter
import com.example.myapplication.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the gallery screens. They render each Composable with in-memory sample data
 * (no MediaStore needed), so they exercise layout, grouping, and selection logic deterministically.
 */
class GalleryScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private fun item(
        id: Long,
        name: String,
        dateAdded: Long = System.currentTimeMillis() / 1000,
        isVideo: Boolean = false,
        expiresAt: Long = 0,
    ) = GalleryItem(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        name = name,
        dateAdded = dateAdded,
        bucketId = 1L,
        bucketName = "Camera",
        isVideo = isVideo,
        durationMs = 0,
        expiresAt = expiresAt,
    )

    @Test
    fun albumsScreen_showsAlbumNameAndFilters() {
        rule.setContent {
            AlbumsScreen(
                albums = listOf(
                    Album(1L, "Camera", Uri.parse("content://media/1"), count = 3),
                ),
                filter = MediaFilter.ALL,
                onFilterChange = {},
                onAlbumClick = {},
                bottomPadding = PaddingValues(),
            )
        }
        rule.onNodeWithText("Camera").assertIsDisplayed()
        rule.onNodeWithText("All").assertIsDisplayed()
        rule.onNodeWithText("Videos").assertIsDisplayed()
    }

    @Test
    fun timelineScreen_groupsTodaysItemsUnderTodayHeader() {
        rule.setContent {
            TimelineScreen(
                items = listOf(item(1, "a"), item(2, "b")),
                filter = MediaFilter.ALL,
                onFilterChange = {},
                onItemClick = {},
                onShareSelected = {},
                onDeleteSelected = {},
                onOpenSettings = {},
                bottomPadding = PaddingValues(),
            )
        }
        rule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun photoGrid_longPressEntersSelectionMode() {
        rule.setContent {
            PhotoGridScreen(
                title = "Camera",
                items = listOf(item(1, "first"), item(2, "second")),
                onItemClick = {},
                onShareSelected = {},
                onDeleteSelected = {},
                onBack = {},
            )
        }
        rule.onAllNodesWithContentDescription("first")[0]
            .performTouchInput { longClick() }
        rule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun albumsScreen_emptyState() {
        rule.setContent {
            AlbumsScreen(
                albums = emptyList(),
                filter = MediaFilter.ALL,
                onFilterChange = {},
                onAlbumClick = {},
                bottomPadding = PaddingValues(),
            )
        }
        rule.onNodeWithText("No albums", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_selectingDarkInvokesCallback() {
        var chosen: ThemeMode? = null
        rule.setContent {
            SettingsScreen(
                settings = AppSettings(),
                onThemeChange = { chosen = it },
                onStartTabChange = {},
                onAutoPlayChange = {},
                onDynamicColorChange = {},
                onBack = {},
            )
        }
        rule.onNodeWithText("Dark").performClick()
        assertEquals(ThemeMode.DARK, chosen)
    }

    @Test
    fun trashScreen_emptyState() {
        rule.setContent {
            TrashScreen(
                items = emptyList(),
                onRestore = {},
                onDeleteForever = {},
                bottomPadding = PaddingValues(),
            )
        }
        rule.onNodeWithText("Recently deleted").assertIsDisplayed()
        rule.onNodeWithText("No recently deleted", substring = true).assertIsDisplayed()
    }

    @Test
    fun trashScreen_showsDaysLeftCaption() {
        val fiveDays = System.currentTimeMillis() / 1000 + 5 * 86_400
        rule.setContent {
            TrashScreen(
                items = listOf(item(1, "a", expiresAt = fiveDays)),
                onRestore = {},
                onDeleteForever = {},
                bottomPadding = PaddingValues(),
            )
        }
        rule.onNodeWithText("5d left", substring = true).assertIsDisplayed()
    }
}
