package com.velobrowser.domain.repository

import com.velobrowser.domain.model.BookmarkEntry
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getBookmarksForProfile(profileId: Long): Flow<List<BookmarkEntry>>
    suspend fun addBookmark(bookmark: BookmarkEntry): Long
    suspend fun deleteBookmark(id: Long)
    suspend fun isBookmarked(url: String, profileId: Long): Boolean
    suspend fun getBookmarkByUrl(url: String, profileId: Long): BookmarkEntry?
}
