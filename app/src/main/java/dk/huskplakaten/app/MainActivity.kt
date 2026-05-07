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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.google.android.gms.tasks.CancellationTokenSource
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HuskPlakatenTheme {
                val appContext = applicationContext
                val sessionStore = remember { AuthSessionStore(appContext) }
                var session by remember { mutableStateOf(sessionStore.restore()) }
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
    var showRemovedArchive by remember { mutableStateOf(false) }
    var showEconomyScreen by remember { mutableStateOf(false) }
    var selectedPartyThemeKey by rememberSaveable { mutableStateOf(PartyTheme.STANDARD.key) }
    var selectedPlakatId by remember { mutableStateOf<Long?>(null) }
    var captureMode by remember { mutableStateOf(CaptureMode.ADD) }
    var removalTargetId by remember { mutableStateOf<Long?>(null) }
    var currentUserLocation by remember { mutableStateOf<Location?>(null) }
    val nearbyAlertState = remember { mutableStateMapOf<Long, Boolean>() }
    val selectedPartyTheme = remember(selectedPartyThemeKey) { PartyTheme.fromKey(selectedPartyThemeKey) }

    @SuppressLint("MissingPermission")
    suspend fun fetchBestCurrentLocation(requireReliableFix: Boolean = false): Location? {
        val now = System.currentTimeMillis()
        fun Location.isReliable(): Boolean {
            val ageMs = now - time
            return accuracy <= MAX_ACCEPTED_LOCATION_ACCURACY_METERS && ageMs <= MAX_ACCEPTED_LOCATION_AGE_MS
        }

        val freshLocation = try {
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .await()
        } catch (_: Exception) {
            null
        }
        if (freshLocation != null && (!requireReliableFix || freshLocation.isReliable())) return freshLocation
        val cached = try {
            fusedLocationClient.lastLocation.await()
        } catch (_: Exception) {
            null
        }
        if (cached != null && (!requireReliableFix || cached.isReliable())) return cached
        return if (requireReliableFix) null else freshLocation ?: cached
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchStableCurrentLocation(): Location? {
        val candidates = mutableListOf<Location>()
        repeat(3) { index ->
            fetchBestCurrentLocation(requireReliableFix = false)?.let { candidates += it }
            if (index < 2) delay(1_200L)
        }
        if (candidates.isEmpty()) return null

        val best = candidates.minByOrNull { it.accuracy } ?: return null
        return best
    }

    @SuppressLint("MissingPermission")
    fun saveCapturedPhotoWithLocation(bitmap: Bitmap) {
        scope.launch {
            try {
                val location = fetchStableCurrentLocation()
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
            currentUserLocation = fetchStableCurrentLocation()
            if (currentUserLocation == null) {
                Toast.makeText(context, context.getString(R.string.error_location_unavailable), Toast.LENGTH_SHORT).show()
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
                Priority.PRIORITY_HIGH_ACCURACY,
                3_000L
            )
                .setMinUpdateDistanceMeters(2f)
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
                val location = fetchStableCurrentLocation()
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
                accentColor = selectedPartyTheme.primary,
                onSelectPlakat = { selectedPlakatId = it },
                onBack = { showMap = false },
                onNavigate = { selected ->
                    startRouteNavigation(context, selected.latitude, selected.longitude)
                },
                onDelete = { selected ->
                    vm.delete(selected)
                    selectedPlakatId = null
                },
                onMarkRemoved = if (session.billingActive) {
                    { selected -> launchCaptureFlow(CaptureMode.REMOVE, selected.id) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else if (showEconomyScreen) {
            EconomyScreen(
                session = session,
                registeredCount = registeredCount,
                onBack = { showEconomyScreen = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        } else if (showRemovedArchive) {
            RemovedPlakaterScreen(
                items = removedItems,
                onBack = { showRemovedArchive = false },
                onDelete = { vm.delete(it) },
                onShowOnMap = { item ->
                    if (!ensureMapsKeyConfigured()) {
                        return@RemovedPlakaterScreen
                    }
                    selectedPlakatId = item.id
                    showMap = true
                },
                onNavigate = { item ->
                    startRouteNavigation(context, item.latitude, item.longitude)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
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
                    var showQuickMenu by remember { mutableStateOf(false) }
                    var showThemeSubMenu by remember { mutableStateOf(false) }
                    Column {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = selectedPartyTheme.primary.copy(alpha = 0.12f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(R.string.home_title),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = selectedPartyTheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.home_subtitle),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        TextButton(
                                            onClick = { showQuickMenu = true },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = selectedPartyTheme.primary
                                            )
                                        ) {
                                            Text(text = stringResource(R.string.menu_label))
                                        }
                                        DropdownMenu(
                                            expanded = showQuickMenu,
                                            onDismissRequest = {
                                                showQuickMenu = false
                                                showThemeSubMenu = false
                                            }
                                        ) {
                                            if (!showThemeSubMenu) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.account_logged_in_as, session.email)) },
                                                    onClick = {}
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.open_economy_page)) },
                                                    onClick = {
                                                        showQuickMenu = false
                                                        showEconomyScreen = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.home_list_title, items.size)) },
                                                    onClick = {}
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_political_theme_title)) },
                                                    onClick = { showThemeSubMenu = true }
                                                )
                                                if (removedItems.isNotEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.open_removed_archive, removedItems.size)) },
                                                        onClick = {
                                                            showQuickMenu = false
                                                            showRemovedArchive = true
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.logout_cta)) },
                                                    onClick = {
                                                        showQuickMenu = false
                                                        onLogout()
                                                    }
                                                )
                                            } else {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_back)) },
                                                    onClick = { showThemeSubMenu = false }
                                                )
                                                PartyTheme.entries.forEach { theme ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            val prefix = if (theme.key == selectedPartyThemeKey) "\u2713 " else ""
                                                            Text(prefix + theme.displayName)
                                                        },
                                                        onClick = {
                                                            selectedPartyThemeKey = theme.key
                                                            showThemeSubMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (session.billingActive) {
                            Button(
                                onClick = { launchCaptureFlow(CaptureMode.ADD) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = selectedPartyTheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.home_primary_action),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Button(
                                onClick = onActivateBilling,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = selectedPartyTheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(text = stringResource(R.string.activate_billing_cta))
                            }
                        }
                        TextButton(
                            onClick = { refreshCurrentLocation() },
                            colors = ButtonDefaults.textButtonColors(contentColor = selectedPartyTheme.primary)
                        ) {
                            Text(text = stringResource(R.string.refresh_distances))
                        }
                        if (items.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (!ensureMapsKeyConfigured()) {
                                        return@TextButton
                                    }
                                    selectedPlakatId = null
                                    showMap = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = selectedPartyTheme.primary)
                            ) {
                                Text(stringResource(R.string.show_on_map))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.home_list_title, items.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = selectedPartyTheme.primary
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
                        accentColor = selectedPartyTheme.primary,
                        distanceMeters = distanceMeters,
                        showNearbyBadge = canMarkRemoved,
                        markRemovedEnabled = canMarkRemoved,
                        modifier = Modifier.padding(4.dp)
                    )
                }

            }
        }
    }
}

@Composable
private fun RemovedPlakaterScreen(
    items: List<PlakatEntity>,
    onBack: () -> Unit,
    onDelete: (PlakatEntity) -> Unit,
    onShowOnMap: (PlakatEntity) -> Unit,
    onNavigate: (PlakatEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = modifier
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.back_to_active_posters))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.removed_list_title, items.size),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(items, key = { it.id }) { item ->
            PlakatTile(
                item = item,
                onDelete = { onDelete(item) },
                onShowOnMap = { onShowOnMap(item) },
                onNavigate = { onNavigate(item) },
                onMarkRemoved = null,
                isRemovedItem = true,
                distanceMeters = null,
                showNearbyBadge = false,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
private fun EconomyScreen(
    session: AuthSession,
    registeredCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimatedPrice = formatKroner(registeredCount)
    Column(modifier = modifier) {
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.back_to_active_posters))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.economy_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.economy_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (session.billingActive) {
                        stringResource(R.string.billing_status_active)
                    } else {
                        stringResource(R.string.billing_status_inactive)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.economy_registered_count, registeredCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.economy_base_fee),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.economy_per_poster_fee),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.economy_estimated_price, estimatedPrice),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun PlakatMapScreen(
    items: List<PlakatEntity>,
    selectedPlakatId: Long?,
    currentUserLocation: Location?,
    accentColor: Color,
    onSelectPlakat: (Long) -> Unit,
    onBack: () -> Unit,
    onNavigate: (PlakatEntity) -> Unit,
    onDelete: (PlakatEntity) -> Unit,
    onMarkRemoved: ((PlakatEntity) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var showMarkerDetails by remember { mutableStateOf(false) }
    val fallbackCenter = LatLng(55.6761, 12.5683)
    val selectedItem = items.firstOrNull { it.id == selectedPlakatId }
    val startCenter = selectedItem?.let { LatLng(it.latitude, it.longitude) }
        ?: items.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: fallbackCenter
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startCenter, if (items.isEmpty()) 6f else 18f)
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
                TextButton(
                    onClick = { onNavigate(selectedItem) },
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                ) {
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
                    },
                    onClick = {
                        onSelectPlakat(item.id)
                        showMarkerDetails = true
                        true
                    }
                )
            }
        }
    }

    if (showMarkerDetails && selectedItem != null) {
        val selectedImageBytes = selectedItem.imageJpeg
        val selectedBitmap = remember(selectedImageBytes) {
            selectedImageBytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        val selectedDistanceMeters = currentUserLocation?.let { userLocation ->
            FloatArray(1).also { out ->
                Location.distanceBetween(
                    userLocation.latitude,
                    userLocation.longitude,
                    selectedItem.latitude,
                    selectedItem.longitude,
                    out
                )
            }[0]
        }
        val dateText = remember(selectedItem.createdAtMillis) {
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(selectedItem.createdAtMillis))
        }
        val distanceText = when {
            selectedDistanceMeters == null -> stringResource(R.string.distance_unknown)
            selectedDistanceMeters < 1000f -> stringResource(R.string.distance_meters, selectedDistanceMeters.roundToInt())
            else -> stringResource(R.string.distance_km, selectedDistanceMeters / 1000f)
        }

        AlertDialog(
            onDismissRequest = { showMarkerDetails = false },
            title = { Text(text = stringResource(R.string.poster_details_title)) },
            text = {
                Column {
                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(text = dateText)
                    Text(text = String.format(Locale.getDefault(), "%.5f, %.5f", selectedItem.latitude, selectedItem.longitude))
                    Text(text = distanceText)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        onNavigate(selectedItem)
                        showMarkerDetails = false
                    }, colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                    ) {
                        Text(stringResource(R.string.route_to_poster))
                    }
                    if (onMarkRemoved != null) {
                        TextButton(
                            onClick = {
                                onMarkRemoved(selectedItem)
                                showMarkerDetails = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                        ) {
                            Text(stringResource(R.string.mark_removed))
                        }
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDelete(selectedItem)
                        showMarkerDetails = false
                    }) {
                        Text(stringResource(R.string.delete_item))
                    }
                    TextButton(onClick = { showMarkerDetails = false }) {
                        Text(stringResource(R.string.map_back_to_list))
                    }
                }
            }
        )
    }
}

@Composable
private fun PlakatTile(
    item: PlakatEntity,
    onDelete: () -> Unit,
    onShowOnMap: () -> Unit,
    onNavigate: () -> Unit,
    onMarkRemoved: (() -> Unit)?,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isRemovedItem: Boolean = false,
    distanceMeters: Float?,
    showNearbyBadge: Boolean,
    markRemovedEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showDetailsDialog by remember(item.id) { mutableStateOf(false) }
    var showFullscreenImage by remember(item.id) { mutableStateOf(false) }
    val imageBytes = if (isRemovedItem && item.removalImageJpeg != null) item.removalImageJpeg else item.imageJpeg
    val bitmap = remember(imageBytes) {
        imageBytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val cardElevation by animateDpAsState(
        targetValue = if (!isRemovedItem && showNearbyBadge) 8.dp else 4.dp,
        animationSpec = tween(durationMillis = 400),
        label = "tileElevation"
    )
    val imageScale by animateFloatAsState(
        targetValue = if (!isRemovedItem && showNearbyBadge) 1.04f else 1f,
        animationSpec = tween(durationMillis = 350),
        label = "tileImageScale"
    )
    val dateText = remember(item.createdAtMillis) {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAtMillis))
    }
    val removedAtMillis = item.removedAtMillis
    val removedDateText = remember(removedAtMillis) {
        removedAtMillis?.let {
            SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(it))
        }
    }
    val cardColor = if (isRemovedItem) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(112.dp)
                        .graphicsLayer(scaleX = imageScale, scaleY = imageScale)
                        .clip(RoundedCornerShape(12.dp))
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
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isRemovedItem && markRemovedEnabled && onMarkRemoved != null) {
                TextButton(
                    onClick = onMarkRemoved,
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                ) {
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
            text = {
                Column {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(385.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showFullscreenImage = true }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(text = details.trim())
                }
            },
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

    if (showFullscreenImage && bitmap != null) {
        FullscreenImageDialog(
            bitmap = bitmap,
            onDismiss = { showFullscreenImage = false }
        )
    }
}

@Composable
private fun FullscreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(transformState)
            )
        }
    }
}

private enum class CaptureMode {
    ADD,
    REMOVE
}

private enum class PartyTheme(
    val key: String,
    val displayName: String,
    val primary: Color
) {
    STANDARD("standard", "Standard (sort)", Color(0xFF111111)),
    SOCIALDEMOCRATIET("socialdemokratiet", "A - Socialdemokratiet", Color(0xFFC62828)),
    VENSTRE("venstre", "V - Venstre", Color(0xFF1565C0)),
    SF("sf", "F - SF", Color(0xFF2E7D32)),
    KONSERVATIVE("konservative", "C - Konservative", Color(0xFF1B5E20)),
    RADIKALE("radikale", "B - Radikale Venstre", Color(0xFFC0CA33)),
    ENHEDSLISTEN("enhedslisten", "Ø - Enhedslisten", Color(0xFFD32F2F)),
    LIBERAL_ALLIANCE("liberal_alliance", "I - Liberal Alliance", Color(0xFF00897B)),
    DANSK_FOLKEPARTI("dansk_folkeparti", "Dansk Folkeparti", Color(0xFFFFA000)),
    MODERATERNE("moderaterne", "Moderaterne", Color(0xFF5D4037)),
    ALTERNATIVET("alternativet", "Alternativet", Color(0xFF8BC34A));

    companion object {
        fun fromKey(key: String): PartyTheme = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

private fun formatKroner(registeredCount: Int): String {
    val total = 500.0 + (registeredCount * 0.15)
    return String.format(Locale("da", "DK"), "%.2f kr", total)
}

private const val REMOVE_ALLOWED_DISTANCE_METERS = 50f
private const val REMOVE_ALERT_RESET_DISTANCE_METERS = 70f
private const val MAX_ACCEPTED_LOCATION_ACCURACY_METERS = 120f
private const val MAX_ACCEPTED_LOCATION_AGE_MS = 20_000L
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
