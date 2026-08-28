package uk.co.tripassistant.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.db.entity.EvaluatedOfferEntity
import uk.co.tripassistant.app.data.repository.HistoryRepository
import uk.co.tripassistant.app.data.repository.OfferStats
import uk.co.tripassistant.app.util.DayRange
import uk.co.tripassistant.core.model.OfferOutcome
import uk.co.tripassistant.core.model.Recommendation
import java.time.LocalDate
import javax.inject.Inject

/** The date filters of spec section 32. */
enum class HistoryRange(val label: String) {
    TODAY("Today"),
    DAYS_7("7 days"),
    DAYS_30("30 days"),
    CUSTOM("Custom")
}

/** The result filters of spec section 32. */
enum class HistoryFilter(val label: String) {
    ALL("All"),
    GOOD("Good"),
    BORDERLINE("Borderline"),
    POOR("Poor"),
    ACCEPTED("Accepted")
}

data class HistoryUiState(
    val range: HistoryRange = HistoryRange.TODAY,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val offers: List<EvaluatedOfferEntity> = emptyList(),
    /** Statistics for the whole date range, before the result filter is applied. */
    val stats: OfferStats = OfferStats(),
    val historyEnabled: Boolean = true
)

private data class Selection(
    val range: HistoryRange,
    val filter: HistoryFilter,
    val from: LocalDate?,
    val to: LocalDate?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    settings: uk.co.tripassistant.app.data.prefs.SettingsRepository
) : ViewModel() {

    private val selection = MutableStateFlow(
        Selection(HistoryRange.TODAY, HistoryFilter.ALL, null, null)
    )

    private val rangeOffers = selection.flatMapLatest { current ->
        val range = millisRange(current)
        history.observeBetween(range.first, range.last)
    }

    val state: StateFlow<HistoryUiState> = combine(
        selection,
        rangeOffers,
        settings.settings.map { it.historyEnabled }
    ) { current, offers, historyEnabled ->
        HistoryUiState(
            range = current.range,
            filter = current.filter,
            customFrom = current.from,
            customTo = current.to,
            offers = offers.filter { matches(it, current.filter) },
            // Statistics describe the date range, not the result filter — otherwise "Good"
            // would report that 100% of offers were good (spec section 33).
            stats = OfferStats.from(offers),
            historyEnabled = historyEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setRange(range: HistoryRange) {
        selection.value = selection.value.copy(range = range)
    }

    fun setCustomRange(from: LocalDate, to: LocalDate) {
        val ordered = if (from.isAfter(to)) to to from else from to to
        selection.value = selection.value.copy(
            range = HistoryRange.CUSTOM,
            from = ordered.first,
            to = ordered.second
        )
    }

    fun setFilter(filter: HistoryFilter) {
        selection.value = selection.value.copy(filter = filter)
    }

    /** Spec section 34: deleting history leaves settings and subscription state untouched. */
    fun deleteAllHistory() {
        viewModelScope.launch { history.deleteAll() }
    }

    private fun millisRange(selection: Selection): LongRange = when (selection.range) {
        HistoryRange.TODAY -> DayRange.today()
        HistoryRange.DAYS_7 -> DayRange.lastDays(7)
        HistoryRange.DAYS_30 -> DayRange.lastDays(30)
        HistoryRange.CUSTOM -> {
            val from = selection.from
            val to = selection.to
            if (from != null && to != null) DayRange.between(from, to) else DayRange.lastDays(30)
        }
    }

    private fun matches(offer: EvaluatedOfferEntity, filter: HistoryFilter): Boolean = when (filter) {
        HistoryFilter.ALL -> true
        HistoryFilter.GOOD -> offer.recommendation == Recommendation.GOOD
        HistoryFilter.BORDERLINE -> offer.recommendation == Recommendation.BORDERLINE
        HistoryFilter.POOR -> offer.recommendation == Recommendation.POOR
        HistoryFilter.ACCEPTED -> offer.outcome == OfferOutcome.ACCEPTED
    }
}
