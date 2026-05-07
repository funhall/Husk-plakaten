package dk.huskplakaten.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import dk.huskplakaten.app.data.AppDatabase
import dk.huskplakaten.app.data.PlakatEntity
import dk.huskplakaten.app.data.PlakatRepository
import dk.huskplakaten.app.ui.theme.HuskPlakatenTheme
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HuskPlakatenTheme {
                val appContext = applicationContext
                val sessionStore = remember { AuthSessionStore(appContext) }
                var session by rememberSaveable { mutableStateOf(sessionStore.restore()) }
                val repository = remember {
                    PlakatRepository(AppDatabase.getInstance(appContext).plakatDao())
                }
                val vm: PlakatViewModel = viewModel(factory = PlakatViewModelFactory(repository))
                LaunchedEffect(session?.userId) {
                    vm.setCurrentUser(session?.userId)
                }

                if (session == null) {
                    LoginScreen(
                        onEmailLogin = { email, password ->
                            sessionStore.loginWithEmail(email, password)
                                .onSuccess { session = it }
                                .onFailure { throw it }
                        },
                        onGoogleLogin = { session = sessionStore.loginWithGooglePlaceholder() }
                    )
                } else {
                    PlakatHomeScreen(
                        vm = vm,
                        session = session!!,
                        onLogout = {
                            sessionStore.clear()
                            session = null
                        },
                        onActivateBilling = {
                            sessionStore.setBillingActive(true)?.let { session = it }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    onEmailLogin: (String, String) -> Unit,
    onGoogleLogin: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_email_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                error = try {
                    onEmailLogin(email.trim(), password)
                    null
                } catch (e: Exception) {
                    e.message ?: "Login fejlede"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.login_with_email))
        }
        TextButton(onClick = onGoogleLogin) {
            Text(stringResource(R.string.login_with_google_placeholder))
        }
        if (error != null) {
            Text(text = error!!, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.login_firebase_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlakatHomeScreen(
    vm: PlakatViewModel,
    session: AuthSession,
    onLogout: () -> Unit,
    onActivateBilling: () -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val items by vm.plakater.collectAsState()
    val removedItems by vm.removedPlakater.collectAsState()
    val registeredCount by vm.registeredPosterCount.collectAsState()
    val scope = rememberCoroutineScope()
    var showMap by remember { mutableStateOf(false) }
    var selectedPlakatId by remember { mutableStateOf<Long?>(null) }
    var captureMode by remember { mutableStateOf(CaptureMode.ADD) }
    var removalTargetId by remember { mutableStateOf<Long?>(null) }
    var currentUserLocation by remember { mutableStateOf<Location?>(null) }
    val nearbyAlertState = remember { mutableStateMapOf<Long, Boolean>() }

    @SuppressLint("MissingPermission")
    fun saveCapturedPhotoWithLocation(bitmap: Bitmap) {
        scope.launch {
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location == null) {
                    Toast.makeText(context, context.getString(R.string.error_location_unavailable), Toast.LENGTH_LONG).show()
                    return@launch
                }
                vm.addPhotoPlakat(bitmap, location.latitude, location.longitude)
                Toast.makeText(context, context.getString(R.string.msg_saved), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.error_location_unavailable), Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        scope.launch {
            currentUserLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (_: Exception) {
                null
            }
        }
    }

    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    DisposableEffect(hasFineLocation, hasCoarseLocation) {
        if (!hasFineLocation && !hasCoarseLocation) {
            onDispose { }
        } else {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                5_000L
            )
                .setMinUpdateDistanceMeters(5f)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val latest = result.lastLocation ?: return
                    currentUserLocation = latest
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun saveRemovalProofWithLocation(item: PlakatEntity, bitmap: Bitmap) {
        scope.launch {
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location == null) {
                    Toast.makeText(context, context.getString(R.string.error_location_unavailable), Toast.LENGTH_LONG).show()
                    return@launch
                }
                vm.markAsRemovedWithProof(item, bitmap, location.latitude, location.longitude)
                Toast.makeText(context, context.getString(R.string.msg_removed_saved), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.error_location_unavailable), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun ensureMapsKeyConfigured(): Boolean {
        val mapsKey = context.getString(R.string.google_maps_key).trim()
        val configured = mapsKey.isNotEmpty() &&
            !mapsKey.equals("SÆT_DIN_GOOGLE_MAPS_API_KEY_HER", ignoreCase = true)
        if (!configured) {
            Toast.makeText(context, context.getString(R.string.error_maps_key_missing), Toast.LENGTH_LONG).show()
        }
        return configured
    }

    fun notifyNearbyPoster(item: PlakatEntity) {
        triggerNearbyFeedback(context, item)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            Toast.makeText(context, context.getString(R.string.error_camera_cancelled), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        when (captureMode) {
            CaptureMode.ADD -> saveCapturedPhotoWithLocation(bitmap)
            CaptureMode.REMOVE -> {
                val target = items.firstOrNull { it.id == removalTargetId }
                if (target == null) {
                    Toast.makeText(context, context.getString(R.string.error_remove_target_missing), Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                saveRemovalProofWithLocation(target, bitmap)
                removalTargetId = null
                captureMode = CaptureMode.ADD
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (cameraGranted && locationGranted) {
            refreshCurrentLocation()
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, context.getString(R.string.error_permissions_required), Toast.LENGTH_LONG).show()
        }
    }

    fun launchCaptureFlow(mode: CaptureMode, targetId: Long? = null) {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        captureMode = mode
        removalTargetId = targetId

        if (hasCamera && (hasFine || hasCoarse)) {
            refreshCurrentLocation()
            cameraLauncher.launch(null)
            return
        }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    LaunchedEffect(Unit) {
        refreshCurrentLocation()
        ensureNearbyNotificationChannel(context)
    }

    LaunchedEffect(currentUserLocation, items) {
        val location = currentUserLocation ?: return@LaunchedEffect
        items.forEach { item ->
            val distance = FloatArray(1).also { out ->
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    item.latitude,
                    item.longitude,
                    out
                )
            }[0]

            val wasAlerted = nearbyAlertState[item.id] == true
            if (distance <= REMOVE_ALLOWED_DISTANCE_METERS && !wasAlerted) {
                nearbyAlertState[item.id] = true
                notifyNearbyPoster(item)
            } else if (distance > REMOVE_ALERT_RESET_DISTANCE_METERS && wasAlerted) {
                nearbyAlertState[item.id] = false
            }
        }
    }

    val activeItemsWithDistance = remember(items, currentUserLocation) {
        items.map { item ->
            val userLocation = currentUserLocation
            val distanceMeters = if (userLocation != null) {
                FloatArray(1).also { out ->
                    Location.distanceBetween(
                        userLocation.latitude,
                        userLocation.longitude,
                        item.latitude,
                        item.longitude,
                        out
                    )
                }[0]
            } else {
                null
            }
            item to distanceMeters
        }.sortedWith(compareBy<Pair<PlakatEntity, Float?>> { it.second ?: Float.MAX_VALUE })
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (showMap) {
            PlakatMapScreen(
                items = activeItemsWithDistance.map { it.first },
                selectedPlakatId = selectedPlakatId,
                currentUserLocation = currentUserLocation,
                onBack = { showMap = false },
                onNavigate = { selected ->
                    startRouteNavigation(context, selected.latitude, selected.longitude)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.account_logged_in_as, session.email),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (session.billingActive) {
                                stringResource(R.string.billing_status_active)
                            } else {
                                stringResource(R.string.billing_status_inactive)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(
                                R.string.billing_usage_line,
                                registeredCount,
                                formatKroner(registeredCount)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (session.billingActive) {
                            Button(
                                onClick = { launchCaptureFlow(CaptureMode.ADD) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.home_primary_action))
                            }
                        } else {
                            Button(
                                onClick = onActivateBilling,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.activate_billing_cta))
                            }
                        }
                        TextButton(onClick = { refreshCurrentLocation() }) {
                            Text(text = stringResource(R.string.refresh_distances))
                        }
                        TextButton(onClick = onLogout) {
                            Text(text = stringResource(R.string.logout_cta))
                        }
                        if (items.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (!ensureMapsKeyConfigured()) {
                                        return@TextButton
                                    }
                                    selectedPlakatId = null
                                    showMap = true
                                }
                            ) {
                                Text(stringResource(R.string.show_on_map))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.home_list_title, items.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(activeItemsWithDistance, key = { it.first.id }) { entry ->
                    val item = entry.first
                    val distanceMeters = entry.second
                    val canMarkRemoved = distanceMeters == null || distanceMeters <= REMOVE_ALLOWED_DISTANCE_METERS
                    PlakatTile(
                        item = item,
                        onDelete = { vm.delete(item) },
                        onShowOnMap = {
                            if (!ensureMapsKeyConfigured()) {
                                return@PlakatTile
                            }
                            selectedPlakatId = item.id
                            showMap = true
                        },
                        onNavigate = {
                            startRouteNavigation(context, item.latitude, item.longitude)
                        },
                        onMarkRemoved = if (session.billingActive) {
                            { launchCaptureFlow(CaptureMode.REMOVE, item.id) }
                        } else {
                            null
                        },
                        distanceMeters = distanceMeters,
                        showNearbyBadge = canMarkRemoved,
                        markRemovedEnabled = canMarkRemoved,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                if (removedItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.removed_list_title, removedItems.size),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    items(removedItems, key = { it.id }) { item ->
                        PlakatTile(
                            item = item,
                            onDelete = { vm.delete(item) },
                            onShowOnMap = {
                                if (!ensureMapsKeyConfigured()) {
                                    return@PlakatTile
                                }
                                selectedPlakatId = item.id
                                showMap = true
                            },
                            onNavigate = {
                                startRouteNavigation(context, item.latitude, item.longitude)
                            },
                            onMarkRemoved = null,
                            isRemovedItem = true,
                            distanceMeters = null,
                            showNearbyBadge = false,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlakatMapScreen(
    items: List<PlakatEntity>,
    selectedPlakatId: Long?,
    currentUserLocation: Location?,
    onBack: () -> Unit,
    onNavigate: (PlakatEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val fallbackCenter = LatLng(55.6761, 12.5683)
    val selectedItem = items.firstOrNull { it.id == selectedPlakatId }
    val startCenter = selectedItem?.let { LatLng(it.latitude, it.longitude) }
        ?: items.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: fallbackCenter
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startCenter, if (items.isEmpty()) 6f else 13f)
    }

    LaunchedEffect(selectedPlakatId, items.size) {
        val target = selectedItem?.let { LatLng(it.latitude, it.longitude) } ?: return@LaunchedEffect
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 15f))
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.map_back_to_list))
            }
            if (selectedItem != null) {
                TextButton(onClick = { onNavigate(selectedItem) }) {
                    Text(stringResource(R.string.route_to_poster))
                }
            }
        }
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cameraPositionState = cameraPositionState
        ) {
            items.forEach { item ->
                val isNearby = currentUserLocation?.let {
                    FloatArray(1).also { out ->
                        Location.distanceBetween(
                            it.latitude,
                            it.longitude,
                            item.latitude,
                            item.longitude,
                            out
                        )
                    }[0] <= REMOVE_ALLOWED_DISTANCE_METERS
                } ?: false
                Marker(
                    state = MarkerState(position = LatLng(item.latitude, item.longitude)),
                    title = if (item.id == selectedPlakatId) {
                        stringResource(R.string.map_selected_marker)
                    } else if (isNearby) {
                        stringResource(R.string.nearby_label)
                    } else {
                        null
                    },
                    icon = if (isNearby) {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun PlakatTile(
    item: PlakatEntity,
    onDelete: () -> Unit,
    onShowOnMap: () -> Unit,
    onNavigate: () -> Unit,
    onMarkRemoved: (() -> Unit)?,
    isRemovedItem: Boolean = false,
    distanceMeters: Float?,
    showNearbyBadge: Boolean,
    markRemovedEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDetailsDialog by remember(item.id) { mutableStateOf(false) }
    val dateText = remember(item.createdAtMillis) {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAtMillis))
    }
    val removedAtMillis = item.removedAtMillis
    val removedDateText = remember(removedAtMillis) {
        removedAtMillis?.let {
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(it))
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val imageBytes = if (isRemovedItem && item.removalImageJpeg != null) item.removalImageJpeg else item.imageJpeg
            val bitmap = remember(imageBytes) {
                imageBytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .clickable { showDetailsDialog = true }
                )
            }
            if (!isRemovedItem) {
                val compactDistanceText = when {
                    distanceMeters == null -> stringResource(R.string.distance_unknown)
                    distanceMeters < 1000f -> stringResource(R.string.distance_meters, distanceMeters.roundToInt())
                    else -> stringResource(R.string.distance_km, distanceMeters / 1000f)
                }
                Text(
                    text = compactDistanceText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isRemovedItem) {
                Text(
                    text = stringResource(R.string.removed_short_label),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!isRemovedItem && markRemovedEnabled && onMarkRemoved != null) {
                TextButton(onClick = onMarkRemoved) {
                    Text(text = stringResource(R.string.mark_removed))
                }
            }
        }
    }

    if (showDetailsDialog) {
        val distanceText = when {
            distanceMeters == null -> stringResource(R.string.distance_unknown)
            distanceMeters < 1000f -> stringResource(R.string.distance_meters, distanceMeters.roundToInt())
            else -> stringResource(R.string.distance_km, distanceMeters / 1000f)
        }
        val details = buildString {
            appendLine(dateText)
            appendLine(String.format(Locale.getDefault(), "%.5f, %.5f", item.latitude, item.longitude))
            appendLine(distanceText)
            if (!isRemovedItem && showNearbyBadge) appendLine(stringResource(R.string.nearby_label))
            if (isRemovedItem && removedDateText != null) {
                append(
                    stringResource(
                        R.string.removed_details,
                        removedDateText,
                        item.removedLatitude ?: item.latitude,
                        item.removedLongitude ?: item.longitude
                    )
                )
            }
        }
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(text = stringResource(R.string.poster_details_title)) },
            text = { Text(text = details.trim()) },
            confirmButton = {
                TextButton(onClick = { onShowOnMap() }) { Text(stringResource(R.string.show_on_map)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onNavigate() }) { Text(stringResource(R.string.route_to_poster)) }
                    TextButton(onClick = {
                        onDelete()
                        showDetailsDialog = false
                    }) { Text(stringResource(R.string.delete_item)) }
                }
            }
        )
    }
}

private enum class CaptureMode {
    ADD,
    REMOVE
}

private fun formatKroner(registeredCount: Int): String {
    val total = 500.0 + (registeredCount * 0.15)
    return String.format(Locale("da", "DK"), "%.2f kr", total)
}

private const val REMOVE_ALLOWED_DISTANCE_METERS = 10f
private const val REMOVE_ALERT_RESET_DISTANCE_METERS = 70f
private const val NEARBY_NOTIFICATION_CHANNEL_ID = "nearby_plakater_channel"

private fun ensureNearbyNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        NEARBY_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.nearby_notification_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = context.getString(R.string.nearby_notification_channel_description)
    }
    notificationManager.createNotificationChannel(channel)
}

private fun triggerNearbyFeedback(context: android.content.Context, item: PlakatEntity) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(220)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasNotificationPermission) return
    }

    val notification = NotificationCompat.Builder(context, NEARBY_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setContentTitle(context.getString(R.string.nearby_notification_title))
        .setContentText(context.getString(R.string.nearby_notification_text))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
}

private fun startRouteNavigation(context: android.content.Context, latitude: Double, longitude: Double) {
    val googleNavigationIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=$latitude,$longitude")
    ).apply {
        setPackage("com.google.android.apps.maps")
    }

    if (googleNavigationIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(googleNavigationIntent)
        return
    }

    val fallbackIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
    )
    if (fallbackIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(fallbackIntent)
    } else {
        Toast.makeText(context, context.getString(R.string.error_no_maps_app), Toast.LENGTH_LONG).show()
    }
}
