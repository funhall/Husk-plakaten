package dk.huskplakaten.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlakatDao {
    @Query("SELECT * FROM plakater WHERE ownerUserId = :ownerUserId AND isRemoved = 0 ORDER BY createdAtMillis DESC")
    fun observeActive(ownerUserId: String): Flow<List<PlakatEntity>>

    @Query("SELECT * FROM plakater WHERE ownerUserId = :ownerUserId AND isRemoved = 1 ORDER BY removedAtMillis DESC, createdAtMillis DESC")
    fun observeRemoved(ownerUserId: String): Flow<List<PlakatEntity>>

    @Query("SELECT COUNT(*) FROM plakater WHERE ownerUserId = :ownerUserId")
    fun observeCount(ownerUserId: String): Flow<Int>

    @Insert
    suspend fun insert(item: PlakatEntity)

    @Query(
        "UPDATE plakater SET isRemoved = 1, removedAtMillis = :removedAtMillis, " +
            "removedLatitude = :removedLatitude, removedLongitude = :removedLongitude, " +
            "removalImageJpeg = :removalImageJpeg WHERE id = :id"
    )
    suspend fun markAsRemoved(
        id: Long,
        removedAtMillis: Long,
        removedLatitude: Double,
        removedLongitude: Double,
        removalImageJpeg: ByteArray?
    )

    @Delete
    suspend fun delete(item: PlakatEntity)
}
