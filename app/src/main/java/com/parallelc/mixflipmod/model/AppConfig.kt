package com.parallelc.mixflipmod.model

import androidx.annotation.StringRes
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.R

data class AppConfig(
    val packageName: String,
    val prefs: List<PrefSpec>,
)

sealed class ConfigNode {
    abstract val id: String
    @get:StringRes
    abstract val titleRes: Int?
    @get:StringRes
    abstract val summaryRes: Int?
    abstract val packages: List<AppConfig>

    data class Package(
        val config: AppConfig,
        @param:StringRes override val titleRes: Int? = null,
        @param:StringRes override val summaryRes: Int? = null,
    ) : ConfigNode() {
        override val id: String = config.packageName
        override val packages: List<AppConfig> = listOf(config)
    }

    data class Group(
        override val id: String,
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val summaryRes: Int? = null,
        override val packages: List<AppConfig>,
    ) : ConfigNode()
}

sealed class OptionValue {
    data class Str(val value: String) : OptionValue()
    data class IntVal(val value: Int) : OptionValue()
    data class FloatVal(val value: Float) : OptionValue()
}

sealed class PrefSpec {
    abstract val prefKey: String
    @get:StringRes
    abstract val titleRes: Int?
    @get:StringRes
    abstract val summaryRes: Int?

    data class Switch(
        override val prefKey: String,
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val summaryRes: Int? = null,
        val children: List<PrefSpec> = emptyList(),
    ) : PrefSpec()

    data class IntInput(
        override val prefKey: String,
        @param:StringRes override val titleRes: Int? = null,
        @param:StringRes override val summaryRes: Int? = null,
        val defaultValue: Int = 0,
        val minValue: Int = 0,
        val maxValue: Int? = null,
    ) : PrefSpec()

    data class StringInput(
        override val prefKey: String,
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val summaryRes: Int? = null,
        val defaultValue: String = "",
    ) : PrefSpec()

    data class OptionSelect(
        override val prefKey: String,
        @param:StringRes override val titleRes: Int,
        @param:StringRes override val summaryRes: Int? = null,
        val defaultValue: OptionValue,
        val options: List<Option> = emptyList(),
        val source: Source = Source.Static,
    ) : PrefSpec() {
        data class Option(
            val value: OptionValue,
            @param:StringRes val labelRes: Int,
        )

        enum class Source {
            Static,
            InputMethods,
        }
    }
}

private fun hideOuterConfig(packageName: String) = AppConfig(
    packageName = packageName,
    prefs = listOf(
        PrefSpec.Switch(Prefs.hideOuterKey(packageName), R.string.pref_hide_outer),
    ),
)

internal val hideOuterPackages = listOf(
    "com.android.calendar",
    "com.android.contacts",
    "com.android.deskclock",
    "com.android.mms",
    "com.android.soundrecorder",
    "com.miui.calculator",
    "com.miui.gallery",
    "com.netease.cloudmusic",
)

private val systemFrameworkConfig = AppConfig(
    packageName = "android",
    prefs = listOf(
        PrefSpec.Switch(Prefs.SYSTEM_COMPAT_CONFIG, R.string.pref_system_compat_config),
        PrefSpec.Switch(Prefs.SYSTEM_FLIP_CONTINUITY, R.string.pref_system_flip_continuity, R.string.pref_system_flip_continuity_summary),
        PrefSpec.OptionSelect(
            Prefs.SYSTEM_FLIP_IME_PKG,
            R.string.pref_system_flip_ime_pkg,
            defaultValue = OptionValue.Str(""),
            options = listOf(
                PrefSpec.OptionSelect.Option(OptionValue.Str(""), R.string.ime_select_default),
            ),
            source = PrefSpec.OptionSelect.Source.InputMethods,
        ),
    ),
)

private val systemUiConfig = AppConfig(
    packageName = "com.android.systemui",
    prefs = listOf(
        PrefSpec.Switch(Prefs.SYSUI_NOTIFICATION, R.string.pref_sysui_notification),
        PrefSpec.Switch(Prefs.SYSUI_CONTROL_CENTER, R.string.pref_sysui_control_center, R.string.pref_sysui_control_center_summary),
        PrefSpec.Switch(Prefs.SYSUI_STATUS_BAR_CLOCK, R.string.pref_sysui_status_bar_clock),
        PrefSpec.Switch(
            Prefs.SYSUI_STATUS_BAR_ICON,
            R.string.pref_sysui_status_bar_icon_max,
            children = listOf(
                PrefSpec.IntInput(
                    Prefs.SYSUI_STATUS_BAR_ICON_MAX,
                    defaultValue = 3,
                    minValue = 1,
                    maxValue = 15,
                ),
            ),
        ),
    ),
)

private val flipHomeConfig = AppConfig(
    packageName = "com.miui.fliphome",
    prefs = listOf(
        PrefSpec.Switch(Prefs.FLIPHOME_NO_START_PAGE, R.string.pref_fliphome_no_start_page, R.string.pref_fliphome_no_start_page_summary),
        PrefSpec.OptionSelect(
            Prefs.FLIPHOME_RECENTS_STYLE,
            R.string.pref_fliphome_recents_style,
            defaultValue = OptionValue.IntVal(Prefs.RecentsLayoutStyle.DEFAULT.prefValue),
            options = listOf(
                PrefSpec.OptionSelect.Option(OptionValue.IntVal(Prefs.RecentsLayoutStyle.DEFAULT.prefValue), R.string.recents_style_default),
                PrefSpec.OptionSelect.Option(OptionValue.IntVal(Prefs.RecentsLayoutStyle.VERTICAL.prefValue), R.string.recents_style_vertical),
                PrefSpec.OptionSelect.Option(OptionValue.IntVal(Prefs.RecentsLayoutStyle.HORIZONTAL.prefValue), R.string.recents_style_horizontal),
            ),
        ),
        PrefSpec.Switch(Prefs.FLIPHOME_RECENTS_LONG_PRESS_MENU, R.string.pref_fliphome_recents_long_press_menu, R.string.pref_fliphome_recents_long_press_menu_summary),
        PrefSpec.Switch(Prefs.FLIPHOME_APP_LONG_PRESS_MENU, R.string.pref_fliphome_app_long_press_menu, R.string.pref_fliphome_app_long_press_menu_summary),
    ),
)

private val sogouConfig = AppConfig(
    packageName = "com.sohu.inputmethod.sogou.xiaomi",
    prefs = listOf(
        PrefSpec.Switch(Prefs.SOGOU_TOOLBAR_FIX, R.string.pref_sogou_toolbar_fix),
        PrefSpec.Switch(Prefs.SOGOU_CLIPBOARD_FIX, R.string.pref_sogou_clipboard_fix),
    ),
)

internal val configNodes = listOf(
    ConfigNode.Group(
        id = "hide_outer",
        titleRes = R.string.pref_hide_outer,
        summaryRes = R.string.pref_hide_outer_summary,
        packages = hideOuterPackages.map(::hideOuterConfig),
    ),
    ConfigNode.Package(systemFrameworkConfig),
    ConfigNode.Package(systemUiConfig),
    ConfigNode.Package(flipHomeConfig),
    ConfigNode.Package(sogouConfig),
)

internal val appConfigs = configNodes.flatMap { it.packages }
