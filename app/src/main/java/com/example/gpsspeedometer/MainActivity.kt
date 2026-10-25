package com.example.gpsspeedometer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import android.content.Context
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gpsspeedometer.ui.main.MainViewModel
import com.example.gpsspeedometer.ui.main.SpeedometerScreen
import com.example.gpsspeedometer.ui.map.MapScreen
import com.example.gpsspeedometer.ui.map.MapViewModel
import com.example.gpsspeedometer.ui.settings.SettingsScreen
import com.example.gpsspeedometer.ui.settings.SettingsViewModel
import com.example.gpsspeedometer.ui.trips.TripDetailScreen
import com.example.gpsspeedometer.ui.trips.TripsScreen
import com.example.gpsspeedometer.ui.trips.TripsViewModel
import org.osmdroid.config.Configuration
import java.io.File
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize osmdroid configuration safely
        com.example.gpsspeedometer.ui.map.AppTileSources.setupOsmdroid(applicationContext)

        val appContainer = (application as GpsApplication).container

        // Keep screen awake while using speedometer if enabled in settings
        lifecycleScope.launch {
            appContainer.settingsRepository.settingsFlow
                .map { it.keepScreenOn }
                .distinctUntilChanged()
                .collect { keepAwake ->
                    if (keepAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GpsSpeedometerApp(appContainer)
                }
            }
        }
    }
}

@Composable
fun GpsSpeedometerApp(appContainer: com.example.gpsspeedometer.di.AppContainer) {
    val context = LocalContext.current
    val initialPermissionGranted = remember {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasFine || hasCoarse
    }
    var hasPermissions by remember { mutableStateOf(initialPermissionGranted) }

    var crashLog by remember {
        val prefs = context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getString("last_crash_trace", null))
    }

    if (crashLog != null) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = {
                val prefs = context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)
                prefs.edit().remove("last_crash_trace").apply()
                crashLog = null
            },
            title = { Text("App Crash Diagnostic Log") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "The previous session encountered a crash:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = crashLog!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(crashLog!!))
                    Toast.makeText(context, "Crash log copied to clipboard!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Log")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val prefs = context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)
                    prefs.edit().remove("last_crash_trace").apply()
                    crashLog = null
                }) {
                    Text("Dismiss")
                }
            }
        )
    }

    val permissions = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val fineLocation = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseLocation = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            hasPermissions = fineLocation || coarseLocation
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(permissions)
        }
    }

    if (hasPermissions) {
        AppNavigation(appContainer)
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Precise location permission is required to measure accurate GPS speed and track trips.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(permissions) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun AppNavigation(appContainer: com.example.gpsspeedometer.di.AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext as android.app.Application

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    appContainer.tripManager,
                    appContainer.locationRepository,
                    appContainer.speedLimitRepository,
                    appContainer.settingsRepository,
                    context
                )
            )

            SpeedometerScreen(
                viewModel = viewModel,
                onNavigateToMap = { navController.navigate("map") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToTrips = { navController.navigate("trips") }
            )
        }

        composable("map") {
            val viewModel: MapViewModel = viewModel(
                factory = MapViewModel.Factory(
                    appContainer.tripManager,
                    appContainer.speedLimitRepository,
                    appContainer.settingsRepository,
                    appContainer.database.tripDao(),
                    context = context
                )
            )
            MapScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "trip_map/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            val viewModel: MapViewModel = viewModel(
                factory = MapViewModel.Factory(
                    appContainer.tripManager,
                    appContainer.speedLimitRepository,
                    appContainer.settingsRepository,
                    appContainer.database.tripDao(),
                    pastTripId = tripId,
                    context = context
                )
            )
            MapScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(appContainer.settingsRepository)
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("trips") {
            val viewModel: TripsViewModel = viewModel(
                factory = TripsViewModel.Factory(appContainer.database.tripDao(), appContainer.settingsRepository)
            )
            TripsScreen(
                viewModel = viewModel,
                onTripClick = { tripId -> navController.navigate("trip_detail/$tripId") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "trip_detail/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            val viewModel: TripsViewModel = viewModel(
                factory = TripsViewModel.Factory(appContainer.database.tripDao(), appContainer.settingsRepository)
            )
            TripDetailScreen(
                tripId = tripId,
                viewModel = viewModel,
                onViewMap = { id -> navController.navigate("trip_map/$id") },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
