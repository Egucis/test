package uk.co.cabcomply.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import uk.co.cabcomply.app.ui.navigation.CabComplyAppRoot
import uk.co.cabcomply.app.ui.theme.CabComplyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
