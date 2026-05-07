package dk.huskplakaten.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plakater")
data class PlakatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: String = "",
    val createdAtMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val imageJpeg: ByteArray? = null,
    val qrText: String? = null,
    val isRemoved: Boolean = false,
    val removedAtMillis: Long? = null,
    val removedLatitude: Double? = null,
    val removedLongitude: Double? = null,
    val removalImageJpeg: ByteArray? = null
)
