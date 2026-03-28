package com.velobrowser.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.velobrowser.domain.model.Profile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val colorHex: String = "#2196F3",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Profile = Profile(
        id = id,
        name = name,
        colorHex = colorHex,
        isDefault = isDefault,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(profile: Profile): ProfileEntity = ProfileEntity(
            id = profile.id,
            name = profile.name,
            colorHex = profile.colorHex,
            isDefault = profile.isDefault,
            createdAt = profile.createdAt
        )
    }
}
