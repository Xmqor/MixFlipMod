package com.parallelc.mixflipmod

object Prefs {
    const val NAME = "mixflipmod"

    // android (system framework)
    const val SYSTEM_COMPAT_CONFIG     = "system_compat_config"
    const val SYSTEM_FLIP_CONTINUITY   = "system_flip_continuity"
    const val SYSTEM_FLIP_SCREEN_MODE  = "system_flip_screen_mode"
    const val SYSTEM_FLIP_IME_PKG      = "system_flip_ime_pkg"

    // com.android.systemui
    const val SYSUI_NOTIFICATION       = "sysui_notification"
    const val SYSUI_CONTROL_CENTER     = "sysui_control_center"
    const val SYSUI_STATUS_BAR_ICON    = "sysui_status_bar_icon"
    const val SYSUI_STATUS_BAR_ICON_MAX = "sysui_status_bar_icon_max"
    const val SYSUI_STATUS_BAR_CLOCK   = "sysui_status_bar_clock"

    // com.miui.fliphome
    const val FLIPHOME_NO_START_PAGE   = "fliphome_no_start_page"
    const val FLIPHOME_RECENTS_STYLE   = "fliphome_recents_style"
    const val FLIPHOME_RECENTS_LONG_PRESS_MENU = "fliphome_recents_long_press_menu"
    const val FLIPHOME_APP_LONG_PRESS_MENU = "fliphome_app_long_press_menu"
    enum class RecentsLayoutStyle(val prefValue: Int) {
        DEFAULT(-1),
        VERTICAL(0),
        HORIZONTAL(1);

        companion object {
            fun fromPref(value: Int) = entries.firstOrNull { it.prefValue == value }
        }
    }

    // Per-app hide outer
    const val HIDE_OUTER_PREFIX = "hide_outer_"
    fun hideOuterKey(pkg: String) = "$HIDE_OUTER_PREFIX$pkg"

    enum class FlipScreenMode(val prefValue: Int) {
        DEFAULT(-1),
        FULL_SCREEN(0),
        NO_SCALE(1),
        SCALE(2);

        companion object {
            fun fromPref(value: Int) = entries.firstOrNull { it.prefValue == value }
        }
    }
    const val FLIP_SCREEN_MODE_PREFIX = "flip_screen_mode_"
    fun flipScreenModeKey(pkg: String) = "$FLIP_SCREEN_MODE_PREFIX$pkg"
    fun flipScreenModePackage(key: String) = key.removePrefix(FLIP_SCREEN_MODE_PREFIX)

    const val FLIP_SCREEN_SCALE_PREFIX = "flip_screen_scale_"
    const val FLIP_SCREEN_SCALE_MIN = 0.01f
    const val FLIP_SCREEN_SCALE_MAX = 1.0f
    const val DEFAULT_FLIP_SCREEN_SCALE = 0.8f
    fun flipScreenScaleKey(pkg: String) = "$FLIP_SCREEN_SCALE_PREFIX$pkg"
    fun flipScreenScalePackage(key: String) = key.removePrefix(FLIP_SCREEN_SCALE_PREFIX)

    const val DEFAULT_FLIP_IME_PKG = "com.sohu.inputmethod.sogou.xiaomi"

    // com.sohu.inputmethod.sogou.xiaomi
    const val SOGOU_TOOLBAR_FIX   = "sogou_toolbar_fix"
    const val SOGOU_CLIPBOARD_FIX = "sogou_clipboard_fix"
}
