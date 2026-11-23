package com.cleansaver.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "downloaded_media",
    indices = [Index(value = ["shortcode", "slide_index", "media_type"], unique = true)]
)
data class DownloadedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shortcode") val shortcode: String,
    @ColumnInfo(name = "slide_index") val slideIndex: Int,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "mediastore_uri") val mediaStoreUri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "caption") val caption: String = "",
    @ColumnInfo(name = "creator_handle") val creatorHandle: String = "",
    @ColumnInfo(name = "platform") val platform: String = "INSTAGRAM",
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "tag") val tag: String = ""
)

@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media ORDER BY created_at_ms DESC")
    fun observeAll(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE media_type = :type ORDER BY created_at_ms DESC")
    fun observeByType(type: String): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE shortcode = :shortcode AND slide_index = :slideIndex LIMIT 1")
    suspend fun findExisting(shortcode: String, slideIndex: Int): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedMediaEntity): Long

    @Query("UPDATE downloaded_media SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE downloaded_media SET tag = :tag WHERE id = :id")
    suspend fun setTag(id: Long, tag: String)

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloaded_media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM downloaded_media")
    suspend fun clearAll()
}

@Database(entities = [DownloadedMediaEntity::class], version = 2, exportSchema = false)
abstract class CleanSaverDatabase : RoomDatabase() {
    abstract fun downloadedMediaDao(): DownloadedMediaDao
}
