package com.example.myapplication.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads images and videos from the device via MediaStore and groups them into albums (buckets). */
class MediaStoreRepository(private val context: Context) {

    // The unified "files" collection lets us query images and videos together.
    private val collection: Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.DATE_EXPIRES,
    )

    private val mediaTypeImage = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
    private val mediaTypeVideo = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

    // Per-type collections. Item URIs must come from these (not the generic Files collection),
    // otherwise MediaStore.createDeleteRequest rejects them ("must be Media items").
    private val imageCollection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videoCollection: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** All media matching [filter], newest first. Optionally restricted to a single bucket. */
    suspend fun getMedia(
        filter: MediaFilter = MediaFilter.ALL,
        bucketId: Long? = null,
    ): List<GalleryItem> = withContext(Dispatchers.IO) {
        val typeClause = when (filter) {
            MediaFilter.ALL ->
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN ($mediaTypeImage, $mediaTypeVideo)"
            MediaFilter.PHOTOS ->
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = $mediaTypeImage"
            MediaFilter.VIDEOS ->
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = $mediaTypeVideo"
        }
        val selection = buildString {
            append(typeClause)
            if (bucketId != null) append(" AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?")
        }
        val selectionArgs = bucketId?.let { arrayOf(it.toString()) }
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        ).use { cursor -> cursorToItems(cursor) }
    }

    /**
     * Items currently in the trash (recoverable). Uses a query Bundle with MATCH_ONLY so MediaStore
     * returns trashed items instead of hiding them.
     */
    suspend fun getTrashed(filter: MediaFilter = MediaFilter.ALL): List<GalleryItem> =
        withContext(Dispatchers.IO) {
            val typeClause = when (filter) {
                MediaFilter.ALL ->
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN ($mediaTypeImage, $mediaTypeVideo)"
                MediaFilter.PHOTOS ->
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = $mediaTypeImage"
                MediaFilter.VIDEOS ->
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = $mediaTypeVideo"
            }
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, typeClause)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            context.contentResolver.query(collection, projection, queryArgs, null).use { cursor ->
                cursorToItems(cursor)
            }
        }

    private fun cursorToItems(cursor: Cursor?): List<GalleryItem> {
        val items = ArrayList<GalleryItem>()
        cursor ?: return items
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketNameCol =
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
        val expiresCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_EXPIRES)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val isVideo = cursor.getInt(typeCol) == mediaTypeVideo
            val itemCollection = if (isVideo) videoCollection else imageCollection
            items += GalleryItem(
                id = id,
                uri = ContentUris.withAppendedId(itemCollection, id),
                name = cursor.getString(nameCol) ?: "",
                dateAdded = cursor.getLong(dateCol),
                bucketId = cursor.getLong(bucketIdCol),
                bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                isVideo = isVideo,
                durationMs = if (cursor.isNull(durationCol)) 0L else cursor.getLong(durationCol),
                expiresAt = if (cursor.isNull(expiresCol)) 0L else cursor.getLong(expiresCol),
            )
        }
        return items
    }

    /** One Album per bucket, using the newest item in each bucket as the cover. */
    suspend fun getAlbums(filter: MediaFilter = MediaFilter.ALL): List<Album> =
        withContext(Dispatchers.IO) {
            val media = getMedia(filter) // already newest-first
            val byBucket = LinkedHashMap<Long, MutableList<GalleryItem>>()
            for (item in media) {
                byBucket.getOrPut(item.bucketId) { ArrayList() }.add(item)
            }
            byBucket.map { (bucketId, items) ->
                Album(
                    bucketId = bucketId,
                    name = items.first().bucketName,
                    coverUri = items.first().uri,
                    count = items.size,
                )
            }.sortedBy { it.name.lowercase() }
        }
}
