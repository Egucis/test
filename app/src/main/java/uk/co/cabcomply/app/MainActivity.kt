package uk.co.cabcomply.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import uk.co.cabcomply.app.ui.navigation.CabComplyAppRoot
import uk.co.cabcomply.app.ui.theme.CabComplyTheme

/** FragmentActivity (not plain ComponentActivity) because androidx.biometric.BiometricPrompt requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CabComplyTheme {
                CabComplyAppRoot()
            }
        }
    }
}
