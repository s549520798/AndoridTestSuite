package priv.lzy.andtestsuite.theme

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import priv.lzy.andtestsuite.base.UseCase
import priv.lzy.andtestsuite.data.preferences.PreferenceStorage
import priv.lzy.andtestsuite.di.CoroutinesQualifiers.IoDispatcher
import javax.inject.Inject

class GetThemeUseCase @Inject constructor(
    private val preferenceStorage: PreferenceStorage,
    @IoDispatcher dispatcher: CoroutineDispatcher
): UseCase<Unit, Theme>(dispatcher){
    override suspend fun execute(parameters: Unit): Theme {
        val selectedTheme = preferenceStorage.selectedTheme.first()
        return themeFromStorageKey(selectedTheme)
            ?: when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Theme.SYSTEM
                else -> Theme.BATTERY_SAVER
            }
    }
}