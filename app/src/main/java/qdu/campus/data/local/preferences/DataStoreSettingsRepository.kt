package qdu.campus.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 非敏感设置。密码、Cookie、Token 一律不放这里。 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val semesterId = stringPreferencesKey("semester_id")
        val termStartDate = stringPreferencesKey("term_start_date")
        val sectionTimeOverrides = stringPreferencesKey("section_time_overrides")
        val noticeKeywords = stringPreferencesKey("notice_keywords")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val appearanceScheme = stringPreferencesKey("appearance_scheme")
        val appearanceMode = stringPreferencesKey("appearance_mode")
        val darkTinted = booleanPreferencesKey("dark_tinted")
        val homeCardIds = stringPreferencesKey("home_card_ids")
        val dormElectricityCode = stringPreferencesKey("dorm_electricity_code")
        val timetableLevel = stringPreferencesKey("timetable_level")
        val undergraduateClasses = stringPreferencesKey("ug_classes")
        val undergraduateAppliedAt = longPreferencesKey("ug_applied_at")
    }

    override val semesterId: Flow<String> =
        context.dataStore.data.map { it[Keys.semesterId] ?: DEFAULT_SEMESTER }

    override val termStartDate: Flow<String> =
        context.dataStore.data.map { it[Keys.termStartDate] ?: "" }

    override val sectionTimeOverrides: Flow<Map<Int, Pair<String, String>>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.sectionTimeOverrides].orEmpty()
                .split(';')
                .mapNotNull { entry ->
                    val parts = entry.split('|')
                    if (parts.size != 3) return@mapNotNull null
                    val index = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val start = parts[1].trim()
                    val end = parts[2].trim()
                    if (start.isBlank() || end.isBlank()) null else index to (start to end)
                }
                .toMap()
        }

    override val notificationKeywords: Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.noticeKeywords]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: DEFAULT_KEYWORDS
        }

    override val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.onboardingDone] ?: false }

    override val appearanceScheme: Flow<AppearanceScheme> =
        context.dataStore.data.map { AppearanceScheme.fromName(it[Keys.appearanceScheme]) ?: AppearanceScheme.Indigo }

    override val appearanceMode: Flow<AppearanceMode> =
        context.dataStore.data.map { AppearanceMode.fromName(it[Keys.appearanceMode]) ?: AppearanceMode.SYSTEM }

    override val darkTinted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.darkTinted] ?: false }

    override val homeCards: Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.homeCardIds]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        }

    override val dormElectricityCode: Flow<String> =
        context.dataStore.data.map { it[Keys.dormElectricityCode] ?: "" }

    override val timetableLevel: Flow<TimetableLevel?> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.timetableLevel]?.let { name ->
                TimetableLevel.entries.firstOrNull { it.name == name }
            }
        }

    override val undergraduateClasses: Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.undergraduateClasses]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
        }

    override val undergraduateAppliedAt: Flow<Long?> =
        context.dataStore.data.map { it[Keys.undergraduateAppliedAt] }

    override suspend fun setSemesterId(value: String) {
        context.dataStore.edit { it[Keys.semesterId] = value }
    }

    override suspend fun setTermStartDate(value: String) {
        context.dataStore.edit { it[Keys.termStartDate] = value }
    }

    override suspend fun setSectionTimeOverrides(value: Map<Int, Pair<String, String>>) {
        val encoded = value.entries
            .sortedBy { it.key }
            .joinToString(";") { (index, times) -> "$index|${times.first}|${times.second}" }
        context.dataStore.edit {
            if (encoded.isBlank()) it.remove(Keys.sectionTimeOverrides)
            else it[Keys.sectionTimeOverrides] = encoded
        }
    }

    override suspend fun setNotificationKeywords(value: List<String>) {
        context.dataStore.edit { it[Keys.noticeKeywords] = value.joinToString(",") }
    }

    override suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[Keys.onboardingDone] = value }
    }

    override suspend fun setAppearanceScheme(value: AppearanceScheme) {
        context.dataStore.edit { it[Keys.appearanceScheme] = value.name }
    }

    override suspend fun setAppearanceMode(value: AppearanceMode) {
        context.dataStore.edit { it[Keys.appearanceMode] = value.name }
    }

    override suspend fun setDarkTinted(value: Boolean) {
        context.dataStore.edit { it[Keys.darkTinted] = value }
    }

    override suspend fun setHomeCards(ids: List<String>) {
        context.dataStore.edit {
            if (ids.isEmpty()) it.remove(Keys.homeCardIds)
            else it[Keys.homeCardIds] = ids.joinToString(",")
        }
    }

    override suspend fun setDormElectricityCode(value: String) {
        context.dataStore.edit {
            if (value.isBlank()) it.remove(Keys.dormElectricityCode)
            else it[Keys.dormElectricityCode] = value.trim()
        }
    }

    override suspend fun setTimetableLevel(value: TimetableLevel) {
        context.dataStore.edit { it[Keys.timetableLevel] = value.name }
    }

    /** 一个班级一行：班级名里允许出现逗号，换行才是分隔符。 */
    override suspend fun setUndergraduateClasses(value: Set<String>) {
        context.dataStore.edit {
            it[Keys.undergraduateClasses] = value.sorted().joinToString("\n")
            it[Keys.undergraduateAppliedAt] = System.currentTimeMillis()
        }
    }

    override suspend fun clearTimetableData() {
        context.dataStore.edit {
            it.remove(Keys.semesterId)
            it.remove(Keys.termStartDate)
            it.remove(Keys.sectionTimeOverrides)
            it.remove(Keys.timetableLevel)
            it.remove(Keys.undergraduateClasses)
            it.remove(Keys.undergraduateAppliedAt)
        }
    }

    override suspend fun clearAllData() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        const val DEFAULT_SEMESTER = "2026-2027-1"
        val DEFAULT_KEYWORDS = listOf(
            "开题", "中期", "答辩", "学位", "培养方案",
            "奖助", "评奖", "选课", "考试",
        )
    }
}
