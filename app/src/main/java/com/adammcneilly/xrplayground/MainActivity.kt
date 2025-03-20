package com.adammcneilly.xrplayground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreatePermissionsNotGranted
import androidx.xr.runtime.SessionCreateSuccess
import com.adammcneilly.xrplayground.theme.XRTheme

class MainActivity : ComponentActivity() {
    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantedPermissions ->
        val allPermissionsGranted = grantedPermissions.all { entry ->
            entry.value
        }

        if (allPermissionsGranted) {
            createSession()
        }
    }

    private var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createSession()

        setContent {
            enableEdgeToEdge()

            XRTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Greeting("Android")
                }
            }
        }
    }

    private fun createSession() {
        when (val sessionResult = Session.create(this)) {
            is SessionCreateSuccess -> {
                // Handle success
                session = sessionResult.session
            }

            is SessionCreatePermissionsNotGranted -> {
                // Request permissions
                permissionRequestLauncher.launch(sessionResult.permissions.toTypedArray())
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}
