package com.inavisys.samsunglifelocationdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.inavisys.samsunglifelocationdemo.ui.SessionScreen
import com.inavisys.location.samsunglife.SamsungLifeService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.containsKey(Manifest.permission.ACCESS_FINE_LOCATION) && grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                lifecycleScope.launch { SamsungLifeService.startLocationUpdate(this@MainActivity) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission()
        setContent {
            MaterialTheme {
                Surface { SessionScreen() }
            }
        }
    }

    private fun requestPermission() {
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (locationGranted) {
            lifecycleScope.launch { SamsungLifeService.startLocationUpdate(this@MainActivity) }
        }

        val permissionsToRequest = buildList {
            if (!locationGranted) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
