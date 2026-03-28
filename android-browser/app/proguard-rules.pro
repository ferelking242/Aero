-keep class com.velobrowser.domain.model.** { *; }
-keep class com.velobrowser.data.local.db.entity.** { *; }
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
