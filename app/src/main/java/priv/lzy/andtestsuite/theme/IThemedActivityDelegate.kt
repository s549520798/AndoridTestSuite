package priv.lzy.andtestsuite.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import priv.lzy.andtestsuite.data.Result.Success
import priv.lzy.andtestsuite.di.CoroutinesQualifiers.ApplicationScope
import javax.inject.Inject

interface IThemedActivityDelegate {

    /**
     * Allows observing of the current theme
     */
    val theme: StateFlow<Theme>

    /**
     * Allows querying of the current theme synchronously
     */
    val currentTheme: Theme
}

class ThemedActivityDelegate @Inject constructor(
    @ApplicationScope externalScope: CoroutineScope,
    observeThemeUseCase: ObserveThemeModeUseCase,
    private val getThemeUseCase: GetThemeUseCase
) : IThemedActivityDelegate {

    override val theme: StateFlow<Theme> = observeThemeUseCase(Unit).map {
        it.successOr(Theme.SYSTEM)
    }.stateIn(externalScope, SharingStarted.Eagerly, Theme.SYSTEM)

    override val currentTheme: Theme
        get() = runBlocking { // Using runBlocking to execute this coroutine synchronously
            getThemeUseCase(Unit).let {
                if (it is Success) it.data else Theme.SYSTEM
            }
        }
}