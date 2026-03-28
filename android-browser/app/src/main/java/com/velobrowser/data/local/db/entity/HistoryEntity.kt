package com.velobrowser.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.velobrowser.domain.model.HistoryEntry

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("url"), Index("visitedAt")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis(),
    val profileId: Long = 1L
) {
    fun toDomain(): HistoryEntry = HistoryEntry(
        id = id,
        url = url,
        title = title,
        visitedAt = visitedAt,
        profileId = profileId
    )

    companion object {
        fun fromDomain(entry: HistoryEntry): HistoryEntity = HistoryEntity(
            id = entry.id,
            url = entry.url,
            title = entry.title,
            visitedAt = entry.visitedAt,
            profileId = entry.profileId
        )
    }
}
