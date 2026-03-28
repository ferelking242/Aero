package com.velobrowser.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.velobrowser.domain.model.BookmarkEntry

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("url")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val faviconUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val profileId: Long = 1L
) {
    fun toDomain(): BookmarkEntry = BookmarkEntry(
        id = id,
        url = url,
        title = title,
        faviconUrl = faviconUrl,
        createdAt = createdAt,
        profileId = profileId
    )

    companion object {
        fun fromDomain(bookmark: BookmarkEntry): BookmarkEntity = BookmarkEntity(
            id = bookmark.id,
            url = bookmark.url,
            title = bookmark.title,
            faviconUrl = bookmark.faviconUrl,
            createdAt = bookmark.createdAt,
            profileId = bookmark.profileId
        )
    }
}
