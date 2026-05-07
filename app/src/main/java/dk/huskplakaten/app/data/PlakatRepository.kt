package dk.huskplakaten.app.data

import kotlinx.coroutines.flow.Flow

class PlakatRepository(
    private val dao: PlakatDao
) {
    fun observeActive(ownerUserId: String): Flow<List<PlakatEntity>> = dao.observeActive(ownerUserId)
    fun observeRemoved(ownerUserId: String): Flow<List<PlakatEntity>> = dao.observeRemoved(ownerUserId)
    fun observeCount(ownerUserId: String): Flow<Int> = dao.observeCount(ownerUserId)

    suspend fun insert(item: PlakatEntity) = dao.insert(item)
    suspend fun markAsRemoved(
        id: Long,
        removedAtMillis: Long,
        removedLatitude: Double,
        removedLongitude: Double,
        removalImageJpeg: ByteArray?
    ) = dao.markAsRemoved(
        id = id,
        removedAtMillis = removedAtMillis,
        removedLatitude = removedLatitude,
        removedLongitude = removedLongitude,
        removalImageJpeg = removalImageJpeg
    )

    suspend fun delete(item: PlakatEntity) = dao.delete(item)
}
