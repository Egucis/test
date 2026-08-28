package uk.co.tripassistant.app.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import uk.co.tripassistant.app.service.AssistantStateHolder
import uk.co.tripassistant.app.service.AssistantStatus
import uk.co.tripassistant.app.ui.components.LabelledValue
import uk.co.tripassistant.app.ui.components.RecommendationChip
import uk.co.tripassistant.app.ui.components.SectionCard
import uk.co.tripassistant.app.ui.components.SectionHeading
import uk.co.tripassistant.app.ui.components.VerticalSpace
import uk.co.tripassistant.app.ui.theme.StatusGood
import uk.co.tripassistant.app.ui.theme.StatusPoor
import uk.co.tripassistant.app.ui.theme.StatusUnknown
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.parser.ParserRegistry
import uk.co.tripassistant.core.pipeline.AnalysisDiagnostics
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    assistantState: AssistantStateHolder,
    private val registry: ParserRegistry
) : ViewModel() {
    val status: StateFlow<AssistantStatus> = assistantState.status
    val diagnostics: StateFlow<AnalysisDiagnostics?> = assistantState.lastDiagnostics
    val evaluation: StateFlow<OfferEvaluation?> = assistantState.lastEvaluation
    val framesAnalysed: StateFlow<Long> = assistantState.framesAnalysed

    fun knownParserVersions(): List<String> = registry.knownVersions()
}

/**
 * The diagnostics screen of spec section 44.
 *
 * It exists for the day Uber changes its interface: it shows which fields were found, which were
 * not, and which layout parser produced them, so a support conversation can be about facts. It
 * deliberately shows the *parsed fields* and never the recognised screen text or a screenshot
 * (spec sections 40 and 52), and it is off by default so none of this clutters normal use.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val evaluation by viewModel.evaluation.collectAsStateWithLifecycle()
    val frames by viewModel.framesAnalysed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineMedium)
        VerticalSpace(16)

        SectionCard {
            SectionHeading("Assistant")
            val running = status is AssistantStatus.Running
            LabelledValue(
                label = "Screen capture",
                value = if (running) "Active" else "Inactive",
                valueColor = if (running) StatusGood else StatusUnknown
            )
            LabelledValue(
                label = "Text recognition",
                value = if (running) "Active" else "Inactive",
                valueColor = if (running) StatusGood else StatusUnknown
            )
            LabelledValue("Frames analysed this session", frames.toString())
        }

        VerticalSpace(12)
        val current = diagnostics
        if (current == null) {
            SectionCard {
                Text(
                    "Nothing analysed yet. Start the assistant and open an Uber offer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SectionCard {
                SectionHeading("Last screen")
                LabelledValue("Screen type", current.screenType.name)
                LabelledValue("Match score", current.screenScore.toString())
                LabelledValue("Layout parser", current.parserVersion ?: "None matched")
                if (current.signals.isNotEmpty()) {
                    LabelledValue("Signals", current.signals.joinToString(", "))
                }
            }

            VerticalSpace(12)
            SectionCard {
                SectionHeading("Fields read")
                current.fields.forEach { field ->
                    LabelledValue(
                        label = field.label,
                        value = if (field.found) "${field.value} ✓" else "NOT FOUND",
                        valueColor = if (field.found) StatusGood else StatusPoor
                    )
                }
            }

            if (current.notes.isNotEmpty()) {
                VerticalSpace(12)
                SectionCard {
                    SectionHeading("Assumptions and corrections")
                    current.notes.forEach { note ->
                        Text("• ${note.message}", style = MaterialTheme.typography.bodyMedium)
                        VerticalSpace(4)
                    }
                }
            }

            if (current.validationIssues.isNotEmpty()) {
                VerticalSpace(12)
                SectionCard {
                    SectionHeading("Rejected by validation")
                    current.validationIssues.forEach { issue ->
                        Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                        VerticalSpace(4)
                    }
                }
            }
        }

        evaluation?.let { result ->
            VerticalSpace(12)
            SectionCard {
                SectionHeading("Result")
                RecommendationChip(result.recommendation)
                VerticalSpace(10)
                LabelledValue("Confidence", result.confidence.name)
                result.unreadable?.let { LabelledValue("Reason", it.detailText) }
                result.primaryReason?.let {
                    LabelledValue("Main reason", "${it.headline} · ${it.detail}")
                }
            }
        }

        VerticalSpace(12)
        SectionCard {
            SectionHeading("Layouts this build knows")
            viewModel.knownParserVersions().forEach { version ->
                Text(version, style = MaterialTheme.typography.bodyMedium)
            }
            VerticalSpace(8)
            Text(
                "If Uber changes its offer card, a new layout parser is added here rather than the " +
                    "old ones being edited, so older phones keep working.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalSpace(20)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        VerticalSpace(20)
    }
}
