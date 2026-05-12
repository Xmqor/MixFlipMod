package com.parallelc.mixflipmod.hook

import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.hook.util.createDexKitBridge
import com.parallelc.mixflipmod.hook.util.findClass
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.log
import com.parallelc.mixflipmod.hook.util.method
import com.parallelc.mixflipmod.hook.util.replaceResult
import com.parallelc.mixflipmod.hook.util.runWithCleanup
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object SogouHook : BaseHook() {
    override val targetPackages = listOf("com.sohu.inputmethod.sogou.xiaomi")

    override fun setupHooks(prefKey: String, param: PackageReadyParam) {
        when (prefKey) {
            Prefs.SOGOU_TOOLBAR_FIX -> hookToolbarFix(param)
            Prefs.SOGOU_CLIPBOARD_FIX -> hookClipboardFix(param)
        }
    }

    private fun findManagerClass(bridge: DexKitBridge, classLoader: ClassLoader) =
        bridge.findClass {
            matcher { usingStrings("flip_old_outer_keyboard") }
        }.singleOrNull()?.getInstance(classLoader)
            ?: error("FlipScreenManager not found")

    private fun findIsFlipScreen(bridge: DexKitBridge, classLoader: ClassLoader, managerClass: Class<*>) =
        bridge.findMethod {
            matcher {
                declaredClass(managerClass.name)
                invokeMethods { add { name = "isFlipScreen" } }
                returnType = "boolean"
                paramCount = 0
            }
        }.firstNotNullOfOrNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            ?: error("isFlipScreen not found")

    // 恢复工具栏
    private fun hookToolbarFix(param: PackageReadyParam) {
        createDexKitBridge(param.classLoader).use { bridge ->
            val managerClass = findManagerClass(bridge, param.classLoader)
            val isFlipScreen = findIsFlipScreen(bridge, param.classLoader, managerClass)

            val buildFunctionList = bridge.findMethod {
                matcher {
                    invokeMethods {
                        add { name = "getFlipOrderList" }
                        add { name = "isFlipScreen" }
                    }
                }
            }.singleOrNull()?.getMethodInstance(param.classLoader)
                ?: error("buildFunctionList not found")

            val getSingleton = managerClass.declaredMethods.firstOrNull { m ->
                Modifier.isStatic(m.modifiers) && m.returnType == managerClass && m.parameterCount == 0
            } ?: error("getSingleton not found")

            hookWithFakeFlipScreen(buildFunctionList, isFlipScreen) { result ->
                runCatching {
                    if (isFlipScreen.invoke(getSingleton.invoke(null)) as Boolean) {
                        @Suppress("UNCHECKED_CAST")
                        val list = result as? ArrayList<Any>
                        if (!list.isNullOrEmpty()) {
                            val idField = runCatching {
                                list[0].javaClass.getDeclaredField("f").also { it.isAccessible = true }
                            }.onFailure { log("toolbar idField lookup failed", it) }.getOrNull()
                            if (idField != null) {
                                list.removeIf { item -> idField.get(item) as? Int in listOf(6, 1052) }
                            }
                        }
                    }
                }.onFailure { log("hookToolbarFix after failed", it) }
                result
            }

            val refreshFunctionList = bridge.findMethod {
                matcher {
                    declaredClass(buildFunctionList.declaringClass.name)
                    invokeMethods { add { name = "isUpdateFlipImeFunction" } }
                }
            }.singleOrNull()?.getMethodInstance(param.classLoader)
                ?: error("refreshFunctionList not found")

            hookWithFakeFlipScreen(refreshFunctionList, isFlipScreen)
        }
    }

    // 恢复剪贴板候选词
    private fun hookClipboardFix(param: PackageReadyParam) {
        createDexKitBridge(param.classLoader).use { bridge ->
            val managerClass = findManagerClass(bridge, param.classLoader)
            val isFlipScreen = findIsFlipScreen(bridge, param.classLoader, managerClass)

            val onCandidateChange = bridge.findMethod {
                matcher {
                    usingStrings("ClipboardToCandsController onCandidateChange")
                    invokeMethods { add { name = "isFlipScreen" } }
                }
            }.singleOrNull()?.getMethodInstance(param.classLoader)
                ?: error("onCandidateChange not found")

            val containerClass = param.classLoader.findClass("com.sohu.inputmethod.main.view.IMEInputCandidateViewContainer")
            val showClipboardFirstCandidate = containerClass.method("showClipboardFirstCandidate")

            hook(onCandidateChange) { chain ->
                val isFlipScreenHandle = hook(isFlipScreen, replaceResult(false))
                val restoreHandle = hook(showClipboardFirstCandidate) { innerChain ->
                    isFlipScreenHandle.unhook()
                    innerChain.proceed()
                }
                runWithCleanup({ isFlipScreenHandle.unhook(); restoreHandle.unhook() }) {
                    chain.proceed()
                }
            }

            val showFunctionOrClipboard = bridge.findMethod {
                matcher {
                    returnType = "void"
                    paramCount = 0
                    invokeMethods {
                        add { name = "showIMEFunctionOrFirstClipboardView" }
                        add { name = "showIMEFunctionCandidateView" }
                    }
                }
            }.mapNotNull { runCatching { it.getMethodInstance(param.classLoader) }.getOrNull() }
            .firstOrNull {
                it.declaringClass.name.startsWith("com.sohu.inputmethod.main.manager.") &&
                    Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers)
            } ?: error("showFunctionOrClipboard not found")

            val showIMEFunctionOrFirstClipboardView = containerClass.method("showIMEFunctionOrFirstClipboardView")

            hook(showFunctionOrClipboard) { chain ->
                val isFlipScreenHandle = hook(isFlipScreen, replaceResult(false))
                val restoreHandle = hook(showIMEFunctionOrFirstClipboardView) { innerChain ->
                    isFlipScreenHandle.unhook()
                    innerChain.proceed()
                }
                runWithCleanup({ isFlipScreenHandle.unhook(); restoreHandle.unhook() }) {
                    chain.proceed()
                }
            }
        }
    }

    private fun hookWithFakeFlipScreen(
        target: Method,
        isFlipScreen: Method,
        after: ((Any?) -> Any?)? = null,
    ) {
        hook(target) { chain ->
            val handle = hook(isFlipScreen, replaceResult(false))
            val result = runWithCleanup({ handle.unhook() }) { chain.proceed() }
            after?.invoke(result) ?: result
        }
    }
}
