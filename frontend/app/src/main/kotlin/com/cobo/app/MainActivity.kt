package com.cobo.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import com.cobo.app.ui.CoboRoot
import com.cobo.app.ui.CoboTheme
import com.cobo.app.ui.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        App.init(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CoboTheme {
                val dark = isDarkTheme()
                DisposableEffect(dark) {
                    // The window background is what shows during activity transitions and
                    // before the first Compose frame. Follow the app's own theme instead of
                    // the system's, or a dark app on a light system flashes white.
                    window.setBackgroundDrawable(
                        ColorDrawable(
                            if (dark) Color.rgb(0x30, 0x30, 0x30)
                            else Color.rgb(0xF0, 0xF1, 0xF2)
                        )
                    )
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    onDispose {}
                }
                CoboRoot()
            }
        }
    }
}
