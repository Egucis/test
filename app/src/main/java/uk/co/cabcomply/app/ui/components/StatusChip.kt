package uk.co.cabcomply.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.cabcomply.app.ui.theme.BrandAmber
import uk.co.cabcomply.app.ui.theme.BrandGreen
import uk.co.cabcomply.app.ui.theme.BrandInkMuted
import uk.co.cabcomply.app.ui.theme.BrandRed

enum class StatusTone { SUCCESS, WARNING, DANGER, NEUTRAL }

/**
 * Status is always shown as an icon + text label together, never colour alone
 * (product spec sections 67-68).
 */
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (color, icon) = when (tone) {
        StatusTone.SUCCESS -> BrandGreen to Icons.Filled.CheckCircle
        StatusTone.WARNING -> BrandAmber to Icons.Filled.Warning
        StatusTone.DANGER -> BrandRed to Icons.Filled.Error
        StatusTone.NEUTRAL -> BrandInkMuted to Icons.Filled.Schedule
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}
