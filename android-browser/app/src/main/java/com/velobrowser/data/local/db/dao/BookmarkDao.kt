package com.velobrowser.data.local.db.dao

import androidx.room.*
import com.velobrowser.data.local.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getBookmarksForProfile(profileId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url AND profileId = :profileId LIMIT 1")
    suspend fun getBookmarkByUrl(url: String, profileId: Long): BookmarkEntity?

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE url = :url AND profileId = :profileId")
    suspend fun isBookmarked(url: String, profileId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
