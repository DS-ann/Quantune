package com.dsann.music.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlaylistEntity::class, PlaylistSongEntity::class, HistoryEventEntity::class], version = 4, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    companion object {
        private const val DATABASE_NAME = "dsmusic_music_database"

        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                migrateExistingDatabase(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /** Moves the existing Room database by recognizing its schema, not its old filename. */
        private fun migrateExistingDatabase(context: Context) {
            val target = context.getDatabasePath(DATABASE_NAME)
            if (target.exists()) return
            val source = context.databaseList()
                .asSequence()
                .filter { it != DATABASE_NAME }
                .map { context.getDatabasePath(it) }
                .firstOrNull(::isDsmusicDatabase) ?: return
            try {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = false)
                listOf("-wal", "-shm").forEach { suffix ->
                    val sidecar = File(source.path + suffix)
                    if (sidecar.isFile) sidecar.copyTo(File(target.path + suffix), overwrite = true)
                }
                source.delete()
                listOf("-wal", "-shm").forEach { File(source.path + it).delete() }
            } catch (error: Exception) {
                target.delete()
                listOf("-wal", "-shm").forEach { File(target.path + it).delete() }
                Log.w("MusicDatabase", "Could not migrate the existing music database", error)
            }
        }

        private fun isDsmusicDatabase(file: File): Boolean {
            if (!file.isFile) return false
            return try {
                val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    val tables = mutableSetOf<String>()
                    database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                        while (cursor.moveToNext()) tables.add(cursor.getString(0))
                    }
                    tables.contains("playlists") && tables.contains("playlist_songs")
                } finally {
                    database.close()
                }
            } catch (_: Exception) {
                false
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_songs ADD COLUMN lufs REAL DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN youtubePlaylistId TEXT")
            }
        }
    }
}
