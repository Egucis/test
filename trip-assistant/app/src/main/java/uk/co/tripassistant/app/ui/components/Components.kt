package uk.co.tripassistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.tripassistant.app.ui.theme.StatusBorderline
import uk.co.tripassistant.app.ui.theme.StatusGood
import uk.co.tripassistant.app.ui.theme.StatusPoor
import uk.co.tripassistant.app.ui.theme.StatusUnknown
import uk.co.tripassistant.core.model.MetricStatus
import uk.co.tripassistant.core.model.Recommendation

/** The rounded, generously spaced card the whole app is built from (spec section 45). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/**
 * A status is a colour, an icon and a word — never a colour on its own
 * (spec sections 25, 46 and 48).
 */
@Composable
fun RecommendationChip(recommendation: Recommendation, modifier: Modifier = Modifier) {
    val (color, icon, label) = recommendationStyle(recommendation)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun recommendationColor(recommendation: Recommendation): Color = when (recommendation) {
    Recommendation.GOOD -> StatusGood
    Recommendation.BORDERLINE -> StatusBorderline
    Recommendation.POOR -> StatusPoor
    Recommendation.UNKNOWN -> StatusUnknown
}

@Composable
private fun recommendationStyle(recommendation: Recommendation): Triple<Color, ImageVector, String> =
    when (recommendation) {
        Recommendation.GOOD -> Triple(StatusGood, Icons.Filled.CheckCircle, "GOOD")
        Recommendation.BORDERLINE -> Triple(StatusBorderline, Icons.Filled.WarningAmber, "BORDERLINE")
        Recommendation.POOR -> Triple(StatusPoor, Icons.Filled.Cancel, "POOR")
        Recommendation.UNKNOWN -> Triple(StatusUnknown, Icons.Filled.HelpOutline, "CAN'T READ")
    }

fun metricStatusColor(status: MetricStatus): Color = when (status) {
    MetricStatus.GREEN -> StatusGood
    MetricStatus.AMBER -> StatusBorderline
    MetricStatus.RED -> StatusPoor
    MetricStatus.NOT_EVALUATED -> StatusUnknown
}

/** Short text next to the colour, so a metric never depends on colour alone. */
fun metricStatusLabel(status: MetricStatus): String = when (status) {
    MetricStatus.GREEN -> "Pass"
    MetricStatus.AMBER -> "Close"
    MetricStatus.RED -> "Fail"
    MetricStatus.NOT_EVALUATED -> "Not shown"
}

/** A label on the left, a value on the right — the app's most common row. */
@Composable
fun LabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasise: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

/** One of the big numbers on Home, e.g. "47 / Offers". */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
fun VerticalSpace(height: Int) {
    Spacer(modifier = Modifier.height(height.dp))
}

/** Empty states are a first-class screen, not an afterthought (spec section 45). */
@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        VerticalSpace(6)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
