package com.parallelc.mixflipmod.hook.util

import android.util.Log
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.module
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Executable

private const val LOG_TAG = "mixflipmod"

internal fun safeHook(name: String, block: () -> Unit) {
    runCatching(block).onFailure { log("[$name] failed", it) }
}

internal fun log(msg: String, e: Throwable? = null) {
    module?.log(Log.ERROR, LOG_TAG, msg, e)
}

internal fun hook(origin: Executable, hooker: Hooker): HookHandle = module!!.hook(origin).intercept(hooker)

internal fun hook(origin: Executable, priority: Int, hooker: Hooker): HookHandle = module!!.hook(origin).setPriority(priority).intercept(hooker)

internal fun prefEnabled(key: String): Boolean {
    return module!!.getRemotePreferences(Prefs.NAME).getBoolean(key, false)
}

internal fun prefInt(key: String, defaultValue: Int): Int {
    return module!!.getRemotePreferences(Prefs.NAME).getInt(key, defaultValue)
}

internal fun prefString(key: String, defaultValue: String): String {
    return module!!.getRemotePreferences(Prefs.NAME).getString(key, defaultValue) ?: defaultValue
}

internal fun replaceResult(value: Any?): Hooker = Hooker { value }

internal fun after(block: (Chain, Any?) -> Any?): Hooker = Hooker { chain ->
    val result = chain.proceed()
    block(chain, result)
}

internal inline fun <T> runWithCleanup(cleanup: () -> Unit, block: () -> T): T {
    return runCatching(block).also { cleanup() }.getOrThrow()
}
