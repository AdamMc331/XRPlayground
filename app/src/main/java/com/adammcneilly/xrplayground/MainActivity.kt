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
import androidx.lifecycle.lifecycleScope
import androidx.xr.arcore.AnchorCreateSuccess
import androidx.xr.arcore.Plane
import androidx.xr.arcore.Trackable
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreatePermissionsNotGranted
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.SessionResumePermissionsNotGranted
import androidx.xr.runtime.SessionResumeSuccess
import androidx.xr.runtime.internal.TrackingState
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.SpatialCapabilities
import androidx.xr.scenecore.getSpatialCapabilities
import com.adammcneilly.xrplayground.theme.XRTheme
import kotlinx.coroutines.launch

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

    private var arCoreSession: Session? = null
    private var xrSession: androidx.xr.scenecore.Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createSession()

        setContent {
            xrSession = LocalSession.current

            if (LocalSpatialCapabilities.current.isContent3dEnabled) {
                println("ADAMLOG - SUBSCRIBING TO PLANES")
                subscribeToPlanes()
            }

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

    override fun onResume() {
        super.onResume()
        resumeSession()
    }

    override fun onPause() {
        super.onPause()
        arCoreSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arCoreSession?.destroy()
    }

    private fun resumeSession() {
        when (val resumeResult = arCoreSession?.resume()) {
            is SessionResumeSuccess -> {
                // Successfully resumed
            }

            is SessionResumePermissionsNotGranted -> {
                permissionRequestLauncher.launch(resumeResult.permissions.toTypedArray())
            }

            else -> {
                // Session was null
            }
        }
    }

    private fun createSession() {
        when (val sessionResult = Session.create(this)) {
            is SessionCreateSuccess -> {
                // Handle success
                arCoreSession = sessionResult.session
                println("ADAMLOG - ARCORE SESSION CREATED")
            }

            is SessionCreatePermissionsNotGranted -> {
                // Request permissions
                permissionRequestLauncher.launch(sessionResult.permissions.toTypedArray())
            }
        }
    }

    private fun subscribeToPlanes() {
        val currentSession = arCoreSession ?: return

        lifecycleScope.launch {
            Plane.subscribe(currentSession).collect { planes ->
                println("ADAMLOG - PLANES: ${planes.size}")
                planes.forEach { plane ->
                    println("ADAMLOG - PLANE: ${plane.state.value.label}")
                    // In theory, we could have multiples tables.
                    if (plane.state.value.label == Plane.Label.Table) {
                        attachObjectToTable(plane)
                    }
                }
            }
        }
    }

    private fun attachObjectToTable(table: Trackable<Plane.State>) {
        val currentXRSession = xrSession ?: return

        if (!currentXRSession.getSpatialCapabilities().hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_3D_CONTENT)) {
            println("ADAMLOG - 3D CONTENT NOT ENABLED")
            return
        }

        // NOTE: This appears to hang so we don't get a model that we can render and attach to the
        // plane yet. This will be where we pick up next time.
        println("ADAMLOG - CREATING MODEL")
        val gltfModel = GltfModel.create(currentXRSession, "models/motorcycle.glb")

        println("ADAMLOG - MODEL CREATE CALLED")
        val x = gltfModel.get()
        println("ADAMLOG - MODEL: $x")
        val gltfEntity = GltfModelEntity.create(currentXRSession, x)
        val pose = gltfEntity.getPose()

        println("ADAMLOG - MODEL: $x")
        println("ADAMLOG - ENTITY: $gltfEntity")

        when (val result = table.createAnchor(pose)) {
            is AnchorCreateSuccess -> {
                println("ADAMLOG - ANCHOR CREATED")
                val anchor = result.anchor

                lifecycleScope.launch {
                    anchor.state.collect { state ->
                        if (state.trackingState == TrackingState.Tracking) {
                            gltfEntity.setPose(
                                currentXRSession.perceptionSpace.transformPoseTo(state.pose, currentXRSession.activitySpace),
                            )
                        } else if (state.trackingState == TrackingState.Stopped) {
                            gltfEntity.setHidden(true)
                        }
                    }
                }
            }

            else -> {
                // handle failure
                println("ADAMLOG - UNABLE TO CREATE ANCHOR")
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}
