package com.example.myapplication.data

import android.net.Uri

/** A single image or video from MediaStore. */
data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val bucketId: Long,
    val bucketName: String,
    val isVideo: Boolean,
    val durationMs: Long,
    /** Epoch seconds when a trashed item is auto-removed; 0 if not trashed. */
    val expiresAt: Long = 0,
)

/** A device folder (MediaStore "bucket") that groups media. */
data class Album(
    val bucketId: Long,
    val name: String,
    val coverUri: Uri,
    val count: Int,
)

/** Which kinds of media to show. */
enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
}
