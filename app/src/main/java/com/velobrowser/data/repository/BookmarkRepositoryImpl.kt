package com.velobrowser.data.repository

import com.velobrowser.data.local.db.dao.BookmarkDao
import com.velobrowser.data.local.db.entity.BookmarkEntity
import com.velobrowser.domain.model.BookmarkEntry
import com.velobrowser.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getBookmarksForProfile(profileId: Long): Flow<List<BookmarkEntry>> =
        bookmarkDao.getBookmarksForProfile(profileId).map { it.map { e -> e.toDomain() } }

    override suspend fun addBookmark(bookmark: BookmarkEntry): Long =
        bookmarkDao.insertBookmark(BookmarkEntity.fromDomain(bookmark))

    override suspend fun deleteBookmark(id: Long) =
        bookmarkDao.deleteBookmark(id)

    override suspend fun isBookmarked(url: String, profileId: Long): Boolean =
        bookmarkDao.isBookmarked(url, profileId)

    override suspend fun getBookmarkByUrl(url: String, profileId: Long): BookmarkEntry? =
        bookmarkDao.getBookmarkByUrl(url, profileId)?.toDomain()
}
