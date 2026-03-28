package com.velobrowser.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.velobrowser.data.local.db.dao.*
import com.velobrowser.data.local.db.entity.*

@Database(
    entities = [
        ProfileEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class VeloDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "velo_browser.db"

        fun create(context: Context): VeloDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VeloDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL(
                            """
                            INSERT INTO profiles (id, name, colorHex, isDefault, createdAt)
                            VALUES (1, 'Default', '#2196F3', 1, ${System.currentTimeMillis()})
                            """.trimIndent()
                        )
                    }
                })
                .build()
        }
    }
}
