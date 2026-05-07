package dk.huskplakaten.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dk.huskplakaten.app.data.PlakatEntity
import dk.huskplakaten.app.data.PlakatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class PlakatViewModel(
    private val repository: PlakatRepository
) : ViewModel() {
    private val currentUserId = MutableStateFlow<String?>(null)

    val plakater: StateFlow<List<PlakatEntity>> = currentUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) flowOf(emptyList()) else repository.observeActive(userId)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val removedPlakater: StateFlow<List<PlakatEntity>> = currentUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) flowOf(emptyList()) else repository.observeRemoved(userId)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val registeredPosterCount: StateFlow<Int> = currentUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) flowOf(0) else repository.observeCount(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setCurrentUser(userId: String?) {
        currentUserId.value = userId
    }

    fun addPhotoPlakat(
        bitmap: Bitmap,
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {
            repository.insert(
                PlakatEntity(
                    ownerUserId = currentUserId.value ?: "",
                    createdAtMillis = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude,
                    imageJpeg = bitmap.toJpegBytes()
                )
            )
        }
    }

    fun delete(item: PlakatEntity) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun markAsRemoved(item: PlakatEntity) {
        viewModelScope.launch {
            repository.markAsRemoved(
                id = item.id,
                removedAtMillis = System.currentTimeMillis(),
                removedLatitude = item.latitude,
                removedLongitude = item.longitude,
                removalImageJpeg = null
            )
        }
    }

    fun markAsRemovedWithProof(
        item: PlakatEntity,
        bitmap: Bitmap,
        removedLatitude: Double,
        removedLongitude: Double
    ) {
        viewModelScope.launch {
            repository.markAsRemoved(
                id = item.id,
                removedAtMillis = System.currentTimeMillis(),
                removedLatitude = removedLatitude,
                removedLongitude = removedLongitude,
                removalImageJpeg = bitmap.toJpegBytes()
            )
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
}

class PlakatViewModelFactory(
    private val repository: PlakatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlakatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlakatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
