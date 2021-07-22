package priv.lzy.andtestsuite.theme

enum class Theme(val storageKey: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system"),
    BATTERY_SAVER("battery_saver")
}
/**
 * Returns the matching [Theme] for the given [storageKey] value.
 */
fun themeFromStorageKey(storageKey: String): Theme? {
    return Theme.values().firstOrNull { it.storageKey == storageKey }
}
