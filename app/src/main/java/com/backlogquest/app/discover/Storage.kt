package com.backlogquest.app.discover

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.*

@Entity(tableName = "games")
data class StoredGame(@PrimaryKey val id: String, val provider: String, val json: String, val favorite: Boolean = false, val viewed: Long = 0, val cachedAt: Long = 0)
data class ProviderCount(val provider: String, val games: Int)
@Entity(tableName = "catalogue_snapshots")
data class CatalogueSnapshot(@PrimaryKey val provider: String, val fetchedAt: Long, val nextPage: Int? = null, val total: Int? = null)
@Entity(tableName = "downloads")
data class DownloadRecord(@PrimaryKey val id: Long, val title: String, val url: String, val filename: String, val authorization: String, val destination: String, val created: Long = System.currentTimeMillis(), val state: String = "Queued", val progress: Int = 0, val localUri: String? = null)
@Dao
interface ShelfDao {
    @Query("SELECT * FROM catalogue_snapshots WHERE provider = :provider") suspend fun snapshot(provider: String): CatalogueSnapshot?
    @Upsert suspend fun putSnapshot(snapshot: CatalogueSnapshot)
    @Query("DELETE FROM catalogue_snapshots") suspend fun clearSnapshots()
    @Query("SELECT * FROM games WHERE favorite = 1 OR viewed > 0") suspend fun retained(): List<StoredGame>
    @Query("SELECT * FROM games WHERE provider = :provider AND cachedAt > 0") suspend fun catalogue(provider: String): List<StoredGame>
    @Query("SELECT * FROM games WHERE id = :id") suspend fun game(id: String): StoredGame?
    /** How many saved catalogue games each source has, for the Spin tab. */
    @Query("SELECT provider, COUNT(*) AS games FROM games WHERE cachedAt > 0 GROUP BY provider") suspend fun cachedCounts(): List<ProviderCount>
    /** Up to [limit] saved catalogue games from the given sources, in random order. */
    @Query("SELECT * FROM games WHERE cachedAt > 0 AND provider IN (:providers) ORDER BY RANDOM() LIMIT :limit") suspend fun randomCached(providers: List<String>, limit: Int): List<StoredGame>
    @Upsert suspend fun put(game: StoredGame)
    @Query("UPDATE games SET favorite = :favorite WHERE id = :id") suspend fun favorite(id: String, favorite: Boolean)
    @Query("SELECT * FROM games WHERE favorite = 1 ORDER BY id") fun favorites(): Flow<List<StoredGame>>
    @Query("SELECT * FROM games WHERE viewed > 0 ORDER BY viewed DESC LIMIT 30") fun recent(): Flow<List<StoredGame>>
    @Query("DELETE FROM games WHERE favorite = 0 AND viewed = 0") suspend fun clearUnused()
    @Query("UPDATE games SET cachedAt = 0") suspend fun expireCache()
    @Query("UPDATE games SET cachedAt = 0 WHERE provider = :provider") suspend fun expireProvider(provider: String)
    @Transaction suspend fun replaceCatalogue(provider: String, rows: List<StoredGame>, nextPage: Int? = null, total: Int? = null) {
        expireProvider(provider)
        appendCatalogue(provider, rows, nextPage, total)
    }
    @Transaction suspend fun appendCatalogue(provider: String, rows: List<StoredGame>, nextPage: Int? = null, total: Int? = null) {
        rows.forEach { row -> val previous = game(row.id); put(row.copy(favorite = previous?.favorite ?: false, viewed = previous?.viewed ?: 0)) }
        putSnapshot(CatalogueSnapshot(provider, System.currentTimeMillis(), nextPage, total))
    }
    @Query("SELECT * FROM downloads ORDER BY created DESC") fun downloads(): Flow<List<DownloadRecord>>
    @Query("SELECT * FROM downloads") suspend fun downloadSnapshot(): List<DownloadRecord>
    @Upsert suspend fun putDownload(record: DownloadRecord)
}
@Database(entities = [StoredGame::class, DownloadRecord::class, CatalogueSnapshot::class], version = 1, exportSchema = false)
abstract class ShelfDatabase : RoomDatabase() {
    abstract fun shelf(): ShelfDao
}

class AppGraph(context: Context) {
    val db = Room.databaseBuilder(context, ShelfDatabase::class.java, "discover.db").build()
    val http = JsonHttp(JsonHttp.client())
    val registry = SourceRegistry.create(http)
    val repository = ShelfRepository(registry, db.shelf())
    val downloads = ShelfDownloads(context.applicationContext, db.shelf())
}
