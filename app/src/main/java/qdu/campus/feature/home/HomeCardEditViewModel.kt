package qdu.campus.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.feature.web.CampusService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页「常用」的编辑页状态。
 *
 * 每次改动立刻落盘，不设「保存」按钮：返回键、进程被杀都不会丢掉已经调好的顺序。
 * 至少保留一张——全部删光不是「首页不要卡片」，而是回到默认，这两件事不该混在一个操作上。
 */
@HiltViewModel
class HomeCardEditViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _cards = MutableStateFlow(CampusService.homeCards)
    val cards: StateFlow<List<CampusService>> = _cards.asStateFlow()

    init {
        viewModelScope.launch {
            settings.homeCards.collect { _cards.value = CampusService.resolveHomeCards(it) }
        }
    }

    fun moveUp(index: Int) = swap(index, index - 1)

    fun moveDown(index: Int) = swap(index, index + 1)

    fun remove(service: CampusService) {
        if (_cards.value.size <= 1) return
        persist(_cards.value - service)
    }

    fun add(service: CampusService) {
        if (_cards.value.contains(service)) return
        persist(_cards.value + service)
    }

    fun resetToDefault() = persist(CampusService.homeCards)

    private fun swap(from: Int, to: Int) {
        val cards = _cards.value.toMutableList()
        if (from !in cards.indices || to !in cards.indices) return
        val moved = cards.removeAt(from)
        cards.add(to, moved)
        persist(cards)
    }

    private fun persist(cards: List<CampusService>) {
        _cards.value = cards
        viewModelScope.launch { settings.setHomeCards(cards.map { it.id }) }
    }
}
