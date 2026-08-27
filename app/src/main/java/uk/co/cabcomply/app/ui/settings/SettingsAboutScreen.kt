package uk.co.cabcomply.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.cabcomply.app.BuildConfig
import uk.co.cabcomply.app.R
import uk.co.cabcomply.app.ui.components.SectionCard

@Composable
fun SettingsAboutScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Image(painterResource(R.drawable.ic_cabcomply_logo), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("CabComply", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Built by a driver, for drivers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(20.dp))
        SectionCard {
            Text("About CabComply", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "CabComply is an independent record-keeping application for UK taxi and private-hire drivers. " +
                    "It helps you record daily vehicle checks, defects, mileage and compliance documents, and to produce " +
                    "professional reports. CabComply is not a council or government application, is not officially approved " +
                    "by any licensing authority, and does not itself guarantee legal or licensing compliance — you remain " +
                    "responsible for meeting your authority's requirements.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("Privacy & Terms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "CabComply stores your data on this device. It is not shared with third parties or used for " +
                    "advertising. See the full Privacy Policy and Terms of Use at cabcomply.co.uk.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("support@cabcomply.co.uk", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
