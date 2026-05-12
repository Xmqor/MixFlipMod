package com.parallelc.mixflipmod

import com.parallelc.mixflipmod.hook.FlipHomeHook
import com.parallelc.mixflipmod.hook.HideOuterHook
import com.parallelc.mixflipmod.hook.SogouHook
import com.parallelc.mixflipmod.hook.SystemHook
import com.parallelc.mixflipmod.hook.SystemUIHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

internal var module: Main? = null

class Main : XposedModule() {

    private val hooks = listOf(
        FlipHomeHook,
        SystemUIHook,
        HideOuterHook,
        SogouHook,
    )

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        SystemHook.hook(param)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        hooks.filter { param.packageName in it.targetPackages }.forEach { it.hook(param) }
    }
}
