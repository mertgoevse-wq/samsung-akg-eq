package de.sonora.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import de.sonora.app.service.SonoraService
import de.sonora.app.ui.SonoraRoot
import de.sonora.app.ui.SonoraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SonoraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SonoraTheme {
                SonoraRoot(viewModel)
            }
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, SonoraService::class.java)
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onStop() {
        viewModel.onActivityStopped()
        super.onStop()
    }
}
