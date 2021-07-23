package priv.lzy.andtestsuite.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import priv.lzy.andtestsuite.data.preferences.DataStorePreferenceStorage.PreferencesKey.PREF_NIGHT_MODE_END_TIME
import priv.lzy.andtestsuite.data.preferences.DataStorePreferenceStorage.PreferencesKey.PREF_NIGHT_MODE_START_TIME
import priv.lzy.andtestsuite.data.preferences.DataStorePreferenceStorage.PreferencesKey.PREF_SELECT_THEME
import priv.lzy.andtestsuite.theme.Theme
import priv.lzy.andtestsuite.utils.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton


/**
 * storage setting options and user preferences
 */
interface PreferenceStorage {

    suspend fun selectTheme(theme: String)
    val selectedTheme: Flow<String>

    //设置夜间模式时间。eg. 19:00 ~ 7:00
    suspend fun setDayNightTime(start: Int, end: Int)
    val nightModeStartTime: Flow<Int>
    val nightModeEndTime: Flow<Int>

}

@Singleton
class DataStorePreferenceStorage @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferenceStorage {
    companion object {
        const val PREFS_NAME = "AndroidTestSuite"
    }

    object PreferencesKey {
        val PREF_SELECT_THEME = stringPreferencesKey("preference_theme")
        val PREF_NIGHT_MODE_START_TIME = intPreferencesKey("preference_night_mode_start_time")
        val PREF_NIGHT_MODE_END_TIME = intPreferencesKey("preference_night_mode_end_time")
    }

    override suspend fun selectTheme(theme: String) {
        dataStore.edit {
            it[PREF_SELECT_THEME] = theme
        }
    }

    override val selectedTheme: Flow<String> =
        dataStore.data.map { it[PREF_SELECT_THEME] ?: Theme.SYSTEM.storageKey }

    override suspend fun setDayNightTime(start: Int, end: Int) {
        dataStore.edit {
            it[PREF_NIGHT_MODE_START_TIME] = start
            it[PREF_NIGHT_MODE_END_TIME] = end
        }
    }

    override val nightModeStartTime: Flow<Int> =
        dataStore.data.map { it[PREF_NIGHT_MODE_START_TIME] ?: TimeUtils.convertTime2Int("19:00") }
    override val nightModeEndTime: Flow<Int> =
        dataStore.data.map { it[PREF_NIGHT_MODE_END_TIME] ?: TimeUtils.convertTime2Int("7:00") }
}