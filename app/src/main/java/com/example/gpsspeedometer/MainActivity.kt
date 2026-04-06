package com.example.gpsspeedometer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gpsspeedometer.ui.main.MainViewModel
import com.example.gpsspeedometer.ui.main.SpeedometerScreen
import com.example.gpsspeedometer.ui.settings.SettingsScreen
import com.example.gpsspeedometer.ui.settings.SettingsViewModel
import com.example.gpsspeedometer.ui.trips.TripDetailScreen
import com.example.gpsspeedometer.ui.trips.TripsScreen
import com.example.gpsspeedometer.ui.trips.TripsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as GpsApplication).container

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
    var hasPermissions by remember { mutableStateOf(false) }

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            // Check if fine location is granted
            val fineLocation = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
            // Notification is optional but good to have
            hasPermissions = fineLocation
        }
    )

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }

    if (hasPermissions) {
        AppNavigation(appContainer)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Location permission is required to track speed.")
                Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                    Text("Grant Permissions")
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
                factory = MainViewModel.Factory(appContainer.tripManager, appContainer.settingsRepository, context)
            )

            SpeedometerScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToTrips = { navController.navigate("trips") }
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
