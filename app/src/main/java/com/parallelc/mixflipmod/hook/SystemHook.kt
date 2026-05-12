package com.parallelc.mixflipmod.hook

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.provider.Settings
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.Prefs.FlipScreenMode
import com.parallelc.mixflipmod.hook.util.after
import com.parallelc.mixflipmod.hook.util.callMethod
import com.parallelc.mixflipmod.hook.util.findClass
import com.parallelc.mixflipmod.hook.util.getField
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.method
import com.parallelc.mixflipmod.hook.util.safeHook
import com.parallelc.mixflipmod.module
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

object SystemHook {

    fun hook(param: SystemServerStartingParam) {
        safeHook("[android] prefs cache") { initSystemPrefsCache() }
        safeHook("[android] ${Prefs.SYSTEM_COMPAT_CONFIG}") { hookCompatConfig(param) }
        safeHook("[android] ${Prefs.SYSTEM_FLIP_CONTINUITY}") { hookFlipContinuity(param) }
        safeHook("[android] ${Prefs.SYSTEM_FLIP_IME_PKG}") { hookFlipInputMethod(param) }
        safeHook("[android] ${Prefs.SYSTEM_FLIP_SCREEN_MODE}") { hookFlipScreenMode(param) }
    }

    // ── Prefs cache ──────────────────────────────────────────────

    private fun initSystemPrefsCache() {
        val prefs = module!!.getRemotePreferences(Prefs.NAME)
        refreshSystemPrefsCache(prefs)
        if (systemPrefsListener == null) {
            systemPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                if (key != null) updateSystemPrefsCache(sharedPreferences, key)
            }.also { prefs.registerOnSharedPreferenceChangeListener(it) }
        }
    }

    private fun refreshSystemPrefsCache(prefs: SharedPreferences) {
        systemCompatConfigEnabled = prefs.getBoolean(Prefs.SYSTEM_COMPAT_CONFIG, false)
        systemFlipContinuityEnabled = prefs.getBoolean(Prefs.SYSTEM_FLIP_CONTINUITY, false)
        flipInputMethodPackageCache = normalizeFlipInputMethodPackage(
            prefs.getString(Prefs.SYSTEM_FLIP_IME_PKG, Prefs.DEFAULT_FLIP_IME_PKG)
        )
        flipScreenModeCache = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(Prefs.FLIP_SCREEN_MODE_PREFIX)) return@mapNotNull null
            val modeInt = value as? Int ?: return@mapNotNull null
            val mode = FlipScreenMode.fromPref(modeInt) ?: return@mapNotNull null
            if (mode == FlipScreenMode.DEFAULT) return@mapNotNull null
            Prefs.flipScreenModePackage(key) to mode
        }.toMap()
        flipScreenScaleCache = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(Prefs.FLIP_SCREEN_SCALE_PREFIX)) return@mapNotNull null
            val scale = (value as? Float) ?: return@mapNotNull null
            Prefs.flipScreenScalePackage(key) to scale
        }.toMap()
    }

    private fun updateSystemPrefsCache(prefs: SharedPreferences, key: String) {
        when {
            key == Prefs.SYSTEM_COMPAT_CONFIG -> {
                systemCompatConfigEnabled = prefs.getBoolean(key, false)
            }
            key == Prefs.SYSTEM_FLIP_CONTINUITY -> {
                systemFlipContinuityEnabled = prefs.getBoolean(key, false)
            }
            key == Prefs.SYSTEM_FLIP_IME_PKG -> {
                flipInputMethodPackageCache = normalizeFlipInputMethodPackage(
                    prefs.getString(key, Prefs.DEFAULT_FLIP_IME_PKG)
                )
                syncFlipInputMethodIfFolded()
            }
            key.startsWith(Prefs.FLIP_SCREEN_MODE_PREFIX) -> {
                val packageName = Prefs.flipScreenModePackage(key)
                val mode = FlipScreenMode.fromPref(prefs.getInt(key, FlipScreenMode.DEFAULT.prefValue))
                    ?: FlipScreenMode.DEFAULT
                val updated = flipScreenModeCache.toMutableMap()
                if (mode == FlipScreenMode.DEFAULT) {
                    updated.remove(packageName)
                } else {
                    updated[packageName] = mode
                }
                flipScreenModeCache = updated
            }
            key.startsWith(Prefs.FLIP_SCREEN_SCALE_PREFIX) -> {
                val packageName = Prefs.flipScreenScalePackage(key)
                val scale = prefs.getFloat(key, Prefs.DEFAULT_FLIP_SCREEN_SCALE)
                val updated = flipScreenScaleCache.toMutableMap()
                updated[packageName] = scale
                flipScreenScaleCache = updated
            }
        }
    }

    // ── Compat config ────────────────────────────────────────────

    private fun hookCompatConfig(param: SystemServerStartingParam) {
        val props = arrayOf("miui.continuity.policy", "android.window.PROPERTY_COMPAT_ALLOW_SMALL_COVER_SCREEN")
        val mgr = param.classLoader.findClass("com.android.server.wm.ApplicationCompatManager")
        val propertyIntHook = Hooker { chain ->
            if (!systemCompatConfigEnabled) return@Hooker chain.proceed()
            when (chain.args[0]) {
                "miui.continuity.policy" -> 5
                "android.window.PROPERTY_COMPAT_ALLOW_SMALL_COVER_SCREEN" -> 1
                else -> chain.proceed()
            }
        }
        hook(mgr.method("getPropertyIntByApplication", String::class.java, String::class.java), propertyIntHook)
        hook(mgr.method("getPropertyIntByActivity", String::class.java, ComponentName::class.java), propertyIntHook)

        val hasPropertyHook = Hooker { chain ->
            if (!systemCompatConfigEnabled) return@Hooker chain.proceed()
            if (chain.args[0] in props) true else chain.proceed()
        }
        hook(mgr.method("hasPropertyByApplication", String::class.java, String::class.java), hasPropertyHook)
        hook(mgr.method("hasPropertyByActivity", String::class.java, ComponentName::class.java), hasPropertyHook)
    }

    // ── Flip continuity ──────────────────────────────────────────

    private fun hookFlipContinuity(param: SystemServerStartingParam) {
        val c = param.classLoader.findClass("com.android.server.wm.InterceptActivityController")
        hook(c.method("isFlipContinuityEnabledFromSetting", String::class.java, Int::class.java, String::class.java)) { chain ->
            if (systemFlipContinuityEnabled) true else chain.proceed()
        }
    }

    // ── Flip screen mode ─────────────────────────────────────────

    private fun hookFlipScreenMode(param: SystemServerStartingParam) {
        val ipm = param.classLoader.findClass("com.android.server.pm.IPackageManagerBase")
        hook(ipm.method("getActivityInfo", ComponentName::class.java, Long::class.java, Int::class.java), after { _, result ->
            (result as? ActivityInfo)?.applyFlipScreenMode()
            result
        })
        hook(ipm.method("getApplicationInfo", String::class.java, Long::class.java, Int::class.java), after { _, result ->
            (result as? ApplicationInfo)?.applyFlipScreenMode()
            result
        })

        val sizeCompatStub = param.classLoader.findClass("android.sizecompat.MiuiAppSizeCompatModeStub")
        val getSizeCompatStub = sizeCompatStub.method("get")
        isFlipFolded = {
            runCatching { getSizeCompatStub.invoke(null)?.callMethod("isFlipFolded") as? Boolean ?: false }.getOrDefault(false)
        }

        val activityRecord = param.classLoader.findClass("com.android.server.wm.ActivityRecord")
        val atmsImpl = param.classLoader.findClass("com.android.server.wm.ActivityTaskManagerServiceImpl")
        val controller = param.classLoader.findClass("com.android.server.wm.BoundsCompatController")
        val windowLayout = param.classLoader.findClass("android.view.WindowLayoutStubImpl")

        hook(controller.method("canUseFixedAspectRatio", Configuration::class.java), after { chain, result ->
            if (!isFlipFolded()) return@after result
            val packageName = activityPackageName(chain.thisObject?.getField("mOwner"))
            when (packageName?.let { flipScreenModeFor(it) }) {
                FlipScreenMode.FULL_SCREEN -> false
                FlipScreenMode.NO_SCALE,
                FlipScreenMode.SCALE -> true
                else -> result
            }
        })
        hook(atmsImpl.method("getGlobalScale", activityRecord), after { chain, result ->
            if (!isFlipFolded()) return@after result
            val packageName = activityPackageName(chain.args[0]) ?: return@after result
            when (flipScreenModeFor(packageName)) {
                FlipScreenMode.NO_SCALE -> FLIP_UNSCALE
                FlipScreenMode.SCALE -> flipScreenScaleFor(packageName)
                else -> result
            }
        })
        hook(windowLayout.method("getLayoutInDisplayCutoutMode", WindowManager.LayoutParams::class.java)) { chain ->
            if (!isFlipFolded()) return@hook chain.proceed()
            val attrs = chain.args[0] as? WindowManager.LayoutParams
            val packageName = attrs?.packageName
            val mode = packageName?.let { flipScreenModeFor(it) }
            val result = chain.proceed()
            if (packageName != null && mode != null && mode != FlipScreenMode.DEFAULT) {
                if (mode == FlipScreenMode.FULL_SCREEN) LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else result
            } else {
                result
            }
        }

        val propertyClass = param.classLoader.findClass($$"android.content.pm.PackageManager$Property")
        hook(
            ipm.method(
                "getPropertyAsUser",
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
            )
        ) { chain ->
            val propertyName = chain.args[0] as? String
            val packageName = chain.args[1] as? String
            if (propertyName == WATCH_OVERLAY_PROPERTY &&
                packageName != null &&
                flipScreenModeFor(packageName) == FlipScreenMode.FULL_SCREEN
            ) {
                val className = chain.args[2] as? String
                propertyClass
                    .getDeclaredConstructor(
                        String::class.java, Boolean::class.javaPrimitiveType!!, String::class.java, String::class.java
                    )
                    .also { it.isAccessible = true }
                    .newInstance(propertyName, false, packageName, className)
            } else {
                chain.proceed()
            }
        }
    }

    private fun ActivityInfo.applyFlipScreenMode() {
        val mode = flipScreenModeFor(packageName)
        if (mode == FlipScreenMode.DEFAULT) return
        metaData = (if (metaData != null) Bundle(metaData) else Bundle()).also {
            it.putInt(FLIP_SCREEN_META_DATA, mode.prefValue)
        }
    }

    private fun ApplicationInfo.applyFlipScreenMode() {
        val mode = flipScreenModeFor(packageName)
        if (mode == FlipScreenMode.DEFAULT) return
        metaData = (if (metaData != null) Bundle(metaData) else Bundle()).also {
            it.putInt(FLIP_SCREEN_META_DATA, mode.prefValue)
        }
    }

    private fun flipScreenModeFor(packageName: String): FlipScreenMode {
        return flipScreenModeCache[packageName] ?: FlipScreenMode.DEFAULT
    }

    private fun flipScreenScaleFor(packageName: String): Float {
        return flipScreenScaleCache[packageName] ?: Prefs.DEFAULT_FLIP_SCREEN_SCALE
    }

    // ── Flip input method ───────────────────────────────────────

    private fun hookFlipInputMethod(param: SystemServerStartingParam) {
        val switcher = param.classLoader.findClass("com.android.server.inputmethod.SogouInputMethodSwitcher")
        runCatching { module!!.deoptimize(switcher.method("getSogouMethodIdLocked")) }
        runCatching { module!!.deoptimize(switcher.method("mayChangeInputMethodLocked", EditorInfo::class.java)) }
        runCatching { module!!.deoptimize(switcher.method("shouldHideImeSwitcherLocked")) }
        hook(switcher.method("isSogouMethodLocked", String::class.java)) { chain ->
            val methodId = chain.args[0] as? String ?: return@hook chain.proceed()
            val userId = chain.thisObject?.getField("mService")?.getField("mCurrentImeUserId") as? Int
                ?: return@hook chain.proceed()
            inputMethodPackage(param, methodId, userId)?.let { it == flipInputMethodPackageCache }
                ?: chain.proceed()
        }

        val immsClass = param.classLoader.findClass("com.android.server.inputmethod.InputMethodManagerService")
        val serviceImpl = param.classLoader.findClass("com.android.server.inputmethod.InputMethodManagerServiceImpl")
        inputMethodServiceImplClass = serviceImpl
        inputMethodSettingsRepositoryClass = param.classLoader.findClass("com.android.server.inputmethod.InputMethodSettingsRepository")
        runCatching { module!!.deoptimize(serviceImpl.method("getSogouMethodIdLocked", immsClass)) }
        runCatching { module!!.deoptimize(serviceImpl.method($$"lambda$onDisplayDeviceStateChanged$8")) }
        runCatching {
            val menuCtrl = param.classLoader.findClass("com.android.server.inputmethod.InputMethodMenuController")
            val info = param.classLoader.loadClass("android.view.inputmethod.InputMethodInfo")
            module!!.deoptimize(serviceImpl.method("showCountdownAlertDialog",
                Int::class.javaPrimitiveType!!, menuCtrl, immsClass, info,
                Int::class.javaPrimitiveType!!, android.content.Context::class.java,
                String::class.java, java.util.List::class.java))
        }
        hook(serviceImpl.method("isSogouMethodLocked", immsClass, String::class.java)) { chain ->
            val service = chain.args[0] ?: return@hook chain.proceed()
            val methodId = chain.args[1] as? String ?: return@hook chain.proceed()
            val userId = service.getField("mCurrentImeUserId") as? Int ?: return@hook chain.proceed()
            inputMethodPackage(param, methodId, userId)?.let { it == flipInputMethodPackageCache }
                ?: chain.proceed()
        }
    }

    private fun normalizeFlipInputMethodPackage(packageName: String?): String {
        return packageName
            ?.trim()
            ?.ifEmpty { Prefs.DEFAULT_FLIP_IME_PKG }
            ?: Prefs.DEFAULT_FLIP_IME_PKG
    }

    private fun inputMethodPackage(param: SystemServerStartingParam, methodId: String, userId: Int): String? {
        val repository = inputMethodSettingsRepositoryClass ?: return null
        val settings = repository.method("get", Int::class.java).invoke(null, userId) ?: return null
        val imi = settings.callMethod("getMethodMap")?.callMethod("get", methodId) ?: return null
        return imi.callMethod("getPackageName") as? String
    }

    private fun syncFlipInputMethodIfFolded() {
        runCatching {
            val serviceImplClass = inputMethodServiceImplClass ?: return
            val serviceImpl = serviceImplClass.method("getInstance").invoke(null) ?: return
            if (serviceImpl.getField("mDeviceFolded") as? Boolean != true) return

            val context = serviceImpl.getField("mContext") as? Context ?: return
            val imms = serviceImpl.getField("mImms") ?: return
            val userId = imms.getField("mCurrentImeUserId") as? Int ?: return
            val methodId = inputMethodIdForPackage(flipInputMethodPackageCache, userId) ?: return

            val resolver = context.contentResolver
            val secureSettingsClass = Settings.Secure::class.java
            val getStringForUser = secureSettingsClass.method(
                "getStringForUser",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            )
            val putStringForUser = secureSettingsClass.method(
                "putStringForUser",
                android.content.ContentResolver::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            )
            val current = getStringForUser.invoke(null, resolver, DEFAULT_INPUT_METHOD, userId) as? String
            if (current != methodId) {
                putStringForUser.invoke(null, resolver, DEFAULT_INPUT_METHOD, methodId, userId)
            }
        }
    }

    private fun inputMethodIdForPackage(packageName: String, userId: Int): String? {
        val repository = inputMethodSettingsRepositoryClass ?: return null
        val settings = repository.method("get", Int::class.java).invoke(null, userId) ?: return null
        val methodMap = settings.callMethod("getMethodMap") ?: return null
        val backingMap = methodMap.getField("mMap") as? Map<*, *> ?: return null
        return backingMap.entries.firstNotNullOfOrNull { (methodId, imi) ->
            val id = methodId as? String ?: return@firstNotNullOfOrNull null
            val imePackageName = imi?.callMethod("getPackageName") as? String
            if (imePackageName == packageName) id else null
        }
    }

    // ── Utility ──────────────────────────────────────────────────

    private fun activityPackageName(activityRecord: Any?): String? {
        return runCatching {
            activityRecord?.getField("packageName") as? String
        }.getOrNull()
    }

    // ── Constants & state ────────────────────────────────────────

    private const val FLIP_SCREEN_META_DATA = "miui.supportFlipFullScreen"
    private const val WATCH_OVERLAY_PROPERTY = "miui.supportFlipWatchOverlayGroupView"
    private const val DEFAULT_INPUT_METHOD = "default_input_method"
    private const val LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3
    private const val FLIP_UNSCALE = 1.0f
    @Volatile
    private var systemCompatConfigEnabled = false
    @Volatile
    private var systemFlipContinuityEnabled = false
    @Volatile
    private var flipInputMethodPackageCache = Prefs.DEFAULT_FLIP_IME_PKG
    @Volatile
    private var flipScreenModeCache: Map<String, FlipScreenMode> = emptyMap()
    @Volatile
    private var flipScreenScaleCache: Map<String, Float> = emptyMap()
    @Volatile
    private var inputMethodServiceImplClass: Class<*>? = null
    @Volatile
    private var inputMethodSettingsRepositoryClass: Class<*>? = null
    private var systemPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var isFlipFolded: () -> Boolean = { false }
}
