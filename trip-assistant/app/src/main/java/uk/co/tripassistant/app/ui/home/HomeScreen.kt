package uk.co.tripassistant.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.tripassistant.app.service.AssistantStoppedReason
import uk.co.tripassistant.app.ui.components.EmptyState
import uk.co.tripassistant.app.ui.components.LabelledValue
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.StatTile
import uk.co.tripassistant.app.ui.components.ThinDivider
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.app.ui.navigation.Destinations
import uk.co.tripassistant.app.ui.theme.StatusBorderline
import uk.co.tripassistant.app.ui.theme.StatusGood
import uk.co.tripassistant.app.ui.theme.StatusPoor
import uk.co.tripassistant.app.ui.theme.StatusUnknown
import uk.co.tripassistant.core.entitlement.AccessLevel
import uk.co.tripassistant.core.entitlement.EntitlementStatus
import uk.co.tripassistant.core.format.Formats

/**
 * The Home screen of spec section 35.
 *
 * Everything above the Start button answers one of the four questions the driver has before a
 * shift; everything below it is somewhere to go between shifts.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Trip Assistant", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(16)

        StatusCard(state)
        VerticalSpace(12)

        TrialBanner(
            state = state,
            onOpenSubscription = { onNavigate(Destinations.SUBSCRIPTION) },
            onRetryVerification = viewModel::retryEntitlementVerification
        )

        ProfileCard(state, onOpenProfiles = { onNavigate(Destinations.PROFILES) })
        VerticalSpace(12)

        TodayCard(state, onOpenHistory = { onNavigate(Destinations.HISTORY) })
        VerticalSpace(16)

        if (!state.isRunning && state.blockers.isNotEmpty()) {
            SetupCard(
                state = state,
                onOpenSubscription = { onNavigate(Destinations.SUBSCRIPTION) },
                onAcknowledgePrivacy = {
                    onNavigate(Destinations.PRIVACY)
                },
                onOpenOverlaySettings = onOpenOverlaySettings,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
            VerticalSpace(16)
        }

        StartStopButton(
            state = state,
            onStart = onStartAssistant,
            onStop = onStopAssistant
        )

        if (state.uberDriverInstalled) {
            VerticalSpace(8)
            TextButton(
                onClick = viewModel::openUberDriver,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Uber Driver")
            }
        }

        VerticalSpace(20)
        NavigationCard(onNavigate)
        VerticalSpace(24)
    }
}

@Composable
private fun StatusCard(state: HomeUiState) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (color, label) = when {
                state.isRunning -> StatusGood to "Assistant running"
                state.isStarting -> StatusBorderline to "Starting…"
                else -> StatusUnknown to "Assistant stopped"
            }
            Icon(
                Icons.Filled.Circle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        val message = stoppedMessage(state.stoppedReason.takeIf { !state.isRunning && !state.isStarting })
        if (message != null) {
            VerticalSpace(8)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Spec section 49: the driver is told what happened, in plain words, with a way forward. */
private fun stoppedMessage(reason: AssistantStoppedReason?): String? = when (reason) {
    AssistantStoppedReason.PROJECTION_REVOKED ->
        "Screen-reading permission was ended by Android. Start the assistant again to continue."

    AssistantStoppedReason.OVERLAY_PERMISSION_LOST ->
        "The floating window permission was withdrawn, so the assistant stopped."

    AssistantStoppedReason.ENTITLEMENT_REQUIRED ->
        "Live evaluation stopped because your subscription could not be confirmed. Your history and settings are untouched."

    AssistantStoppedReason.STOPPED_BY_DRIVER, AssistantStoppedReason.NOT_STARTED, null -> null
}

@Composable
private fun TrialBanner(
    state: HomeUiState,
    onOpenSubscription: () -> Unit,
    onRetryVerification: () -> Unit
) {
    val access = state.access ?: return
    when {
        access.level == AccessLevel.LOCKED_VERIFICATION_REQUIRED -> {
            SectionCard {
                Text("Subscription verification required", style = MaterialTheme.typography.titleMedium)
                VerticalSpace(6)
                Text(
                    "Connect to the internet to verify your subscription. Your history and settings remain available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VerticalSpace(12)
                Button(onClick = onRetryVerification) { Text("Try again") }
            }
            VerticalSpace(12)
        }

        access.level == AccessLevel.LOCKED_SUBSCRIPTION_REQUIRED -> {
            SectionCard {
                Text("Free trial finished", style = MaterialTheme.typography.titleMedium)
                VerticalSpace(6)
                Text(
                    "Subscribe to keep evaluating offers. Everything you have already recorded stays where it is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VerticalSpace(12)
                Button(onClick = onOpenSubscription) { Text("See subscription") }
            }
            VerticalSpace(12)
        }

        access.status == EntitlementStatus.TRIAL && access.trialDaysRemaining != null -> {
            SectionCard {
                Text(
                    text = "Free trial · ${access.trialDaysRemaining} ${if (access.trialDaysRemaining == 1) "day" else "days"} left",
                    style = MaterialTheme.typography.titleMedium
                )
                VerticalSpace(6)
                Text(
                    "Everything is included during the trial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VerticalSpace(12)
                TextButton(onClick = onOpenSubscription) { Text("See subscription") }
            }
            VerticalSpace(12)
        }
    }
}

@Composable
private fun ProfileCard(state: HomeUiState, onOpenProfiles: () -> Unit) {
    SectionCard {
        SectionHeading("Current profile")
        Text(
            text = state.profile?.name ?: "—",
            style = MaterialTheme.typography.titleLarge
        )
        VerticalSpace(10)
        if (state.thresholds.isEmpty()) {
            Text(
                "No £/mile or £/hour rule is switched on in this profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.thresholds.forEach { threshold ->
                Text(threshold, style = MaterialTheme.typography.bodyLarge)
            }
        }
        VerticalSpace(10)
        TextButton(onClick = onOpenProfiles, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text("Profiles and rules")
        }
    }
}

@Composable
private fun TodayCard(state: HomeUiState, onOpenHistory: () -> Unit) {
    SectionCard {
        SectionHeading("Today")
        if (!state.historyEnabled) {
            Text(
                "History is switched off, so today's offers are not being counted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        if (state.stats.evaluated == 0) {
            EmptyState(
                title = "No offers evaluated yet today",
                detail = "Start the assistant and the offers Uber shows you will appear here."
            )
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatTile(state.stats.evaluated.toString(), "Offers")
            StatTile(state.stats.good.toString(), "Good", valueColor = StatusGood)
            StatTile(state.stats.borderline.toString(), "Borderline", valueColor = StatusBorderline)
            StatTile(state.stats.poor.toString(), "Poor", valueColor = StatusPoor)
        }
        VerticalSpace(14)
        ThinDivider()
        VerticalSpace(10)
        state.stats.averagePoundsPerMile?.let {
            LabelledValue("Average offered £/mile", Formats.poundsPerMile(it))
        }
        state.stats.averagePoundsPerHour?.let {
            LabelledValue("Average offered £/hour", Formats.poundsPerHourPrecise(it))
        }
        VerticalSpace(6)
        TextButton(onClick = onOpenHistory, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text("See history")
        }
    }
}

@Composable
private fun SetupCard(
    state: HomeUiState,
    onOpenSubscription: () -> Unit,
    onAcknowledgePrivacy: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    SectionCard {
        SectionHeading("Before you start")
        state.blockers.forEachIndexed { index, blocker ->
            if (index > 0) {
                VerticalSpace(10)
                ThinDivider()
                VerticalSpace(10)
            }
            Text(blocker.title, style = MaterialTheme.typography.titleMedium)
            VerticalSpace(4)
            Text(
                blocker.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalSpace(8)
            OutlinedButton(
                onClick = {
                    when (blocker) {
                        StartBlocker.ENTITLEMENT -> onOpenSubscription()
                        StartBlocker.PRIVACY_ACKNOWLEDGEMENT -> onAcknowledgePrivacy()
                        StartBlocker.OVERLAY_PERMISSION -> onOpenOverlaySettings()
                        StartBlocker.NOTIFICATION_PERMISSION -> onRequestNotificationPermission()
                    }
                }
            ) {
                Text(blocker.action)
            }
        }
    }
}

@Composable
private fun StartStopButton(state: HomeUiState, onStart: () -> Unit, onStop: () -> Unit) {
    if (state.isRunning || state.isStarting) {
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Stop assistant", modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Button(
            onClick = onStart,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Start assistant", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun NavigationCard(onNavigate: (String) -> Unit) {
    SectionCard {
        NavigationRow("History", "Every offer the assistant has evaluated") {
            onNavigate(Destinations.HISTORY)
        }
        ThinDivider()
        NavigationRow("Profiles", "Normal, Busy, Quiet and your own") {
            onNavigate(Destinations.PROFILES)
        }
        ThinDivider()
        NavigationRow("Test my rules", "Try a made-up offer against your thresholds") {
            onNavigate(Destinations.RULE_TESTER)
        }
        ThinDivider()
        NavigationRow("Settings", "Overlay, alerts, history and privacy") {
            onNavigate(Destinations.SETTINGS)
        }
    }
}

@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
