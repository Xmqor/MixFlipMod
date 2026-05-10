package com.parallelc.mixflipmod.hook

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.parallelc.mixflipmod.Prefs
import com.parallelc.mixflipmod.hook.util.after
import com.parallelc.mixflipmod.hook.util.callMethod
import com.parallelc.mixflipmod.hook.util.field
import com.parallelc.mixflipmod.hook.util.findClass
import com.parallelc.mixflipmod.hook.util.getField
import com.parallelc.mixflipmod.hook.util.hook
import com.parallelc.mixflipmod.hook.util.method
import com.parallelc.mixflipmod.hook.util.prefInt
import com.parallelc.mixflipmod.hook.util.replaceResult
import com.parallelc.mixflipmod.hook.util.runWithCleanup
import com.parallelc.mixflipmod.hook.util.setField
import com.parallelc.mixflipmod.module
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.Invoker.Type
import io.github.libxposed.api.XposedInterface.PRIORITY_LOWEST
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

object SystemUIHook : BaseHook() {
    override val targetPackages = listOf("com.android.systemui")
    private val controlCenterComponents = setOf(
        ComponentName("miui.systemui.plugin", "miui.systemui.controlcenter.MiuiControlCenter"),
        ComponentName("miui.systemui.plugin", "miui.systemui.quicksettings.LocalMiuiQSTilePlugin"),
    )

    override fun setupHooks(prefKey: String, param: PackageReadyParam) {
        val configs = param.classLoader.findClass("com.miui.utils.configs.MiuiConfigs")
        when (prefKey) {
            Prefs.SYSUI_NOTIFICATION -> hookNotification(param, configs)
            Prefs.SYSUI_CONTROL_CENTER -> hookControlCenter(param, configs)
            Prefs.SYSUI_STATUS_BAR_ICON -> hookStatusBarIcon(param, configs)
            Prefs.SYSUI_STATUS_BAR_CLOCK -> hookStatusBarClock(param, configs)
        }
    }

    private fun hookNotification(param: PackageReadyParam, miuiConfigs: Class<*>) {
        val clazz = param.classLoader.findClass("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow")
        hook(clazz.method("createMenuViews", Boolean::class.java)) { chain ->
            val handle =
                hook(miuiConfigs.method("isTinyScreen", Context::class.java), replaceResult(false))
            runWithCleanup({ handle.unhook() }) { chain.proceed() }
        }
    }

    private fun hookControlCenter(param: PackageReadyParam, miuiConfigs: Class<*>) {
        val factoryClass = param.classLoader.findClass($$"com.android.systemui.shared.plugins.PluginInstance$PluginFactory")
        hook(factoryClass.method("createPluginContext"), object : Hooker {
            private var isHooked = false
            private var isTinyScreen = false

            override fun intercept(chain: Chain): Any? {
                val result = chain.proceed()
                val mComponentName = chain.thisObject?.getField("mComponentName") as? ComponentName ?: return result
                if (isHooked) return result
                if (mComponentName !in controlCenterComponents) return result

                val pluginLoader = (result as? ContextWrapper)?.classLoader ?: return result
                isHooked = true

                val styleClass = pluginLoader.loadClass($$"miui.systemui.controlcenter.panel.main.MainPanelController$Style")
                val compactStyle = styleClass.field("COMPACT").get(null)
                val verticalStyle = styleClass.field("VERTICAL").get(null)

                val panelClass = pluginLoader.loadClass("miui.systemui.controlcenter.panel.main.MainPanelStyleController")
                hook(panelClass.method("set_style", styleClass)) { styleChain ->
                    isTinyScreen = styleChain.args[0] == compactStyle
                    styleChain.proceed()
                }

                class HookGetStyle : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val handle = hook(panelClass.method("getStyle")) { getStyleChain ->
                            if (isTinyScreen) verticalStyle else getStyleChain.proceed()
                        }
                        return runWithCleanup({ handle.unhook() }) { chain.proceed() }
                    }
                }

                listOf(
                    "miui.systemui.controlcenter.panel.main.qs.EditButtonController",
                    "miui.systemui.controlcenter.panel.main.qs.QSListController",
                    "miui.systemui.controlcenter.panel.main.qs.CompactQSListController",
                    "miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryController",
                    "miui.systemui.controlcenter.panel.main.devicecontrol.DeviceControlsEntryController",
                ).forEach { cls ->
                    hook(pluginLoader.loadClass(cls).method("available", Boolean::class.java), HookGetStyle())
                }

                hook(pluginLoader.loadClass("miui.systemui.controlcenter.qs.tileview.QSTileItemView").method("onFinishInflate"), after { tileChain, tileResult ->
                    (tileChain.thisObject as? FrameLayout)?.setOnLongClickListener { v ->
                        tileChain.thisObject
                            ?.getField("longClickAction")
                            ?.let { it.callMethod("invoke", v) as? Boolean }
                            ?: false
                    }
                    tileResult
                })

                val rdimenClass = pluginLoader.loadClass($$"miui.systemui.controlcenter.R$dimen")
                hook(Resources::class.java.method("getDimensionPixelSize", Int::class.java), after { dimenChain, dimenResult ->
                    if (isTinyScreen && dimenChain.args[0] == rdimenClass.field("device_center_device_item_width").getInt(null)) 245 else dimenResult
                })

                hook(
                    pluginLoader.loadClass($$"miui.systemui.controlcenter.panel.main.devicecenter.devices.DeviceCenterCardController$_adapter$1")
                        .method("onCreateViewHolder", ViewGroup::class.java, Int::class.java),
                    after { _, holderResult ->
                        (holderResult?.getField("itemView") as? View)
                            ?.takeIf { isTinyScreen && it.layoutParams.width != -1 }
                            ?.let { it.layoutParams.width = 245 }
                        holderResult
                    }
                )

                val modeClass = pluginLoader.loadClass($$"miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryViewHolder$Mode")
                val modeCollapsed = modeClass.field("MODE_COLLAPSED").get(null)
                val mode1row = modeClass.field("MODE_1_ROW").get(null)
                val mode2row = modeClass.field("MODE_2_ROWS").get(null)

                hook(pluginLoader.loadClass("miui.systemui.controlcenter.panel.main.devicecenter.devices.DeviceCenterCardController").method("getMode"), Hooker { modeChain ->
                    if (!isTinyScreen) return@Hooker modeChain.proceed()
                    val size = (modeChain.thisObject?.getField("deviceItems") as? ArrayList<*>)?.size ?: return@Hooker modeChain.proceed()
                    when {
                        size == 1 -> modeCollapsed
                        size < 4 -> mode1row
                        else -> mode2row
                    }
                })

                hook(
                    pluginLoader.loadClass("miui.systemui.devicecenter.DeviceCenterController")
                        .method("handleDeviceListUpdate", Boolean::class.java),
                    Hooker { deviceChain ->
                        if (!isTinyScreen) return@Hooker deviceChain.proceed()
                        val deviceList = deviceChain.thisObject?.getField("deviceList") as? ArrayList<*> ?: return@Hooker deviceChain.proceed()
                        if (deviceList.size <= 5) return@Hooker deviceChain.proceed()
                        deviceChain.thisObject?.setField("deviceList", deviceList.subList(0, 5).toList())
                        runWithCleanup({ deviceChain.thisObject?.setField("deviceList", deviceList) }) { deviceChain.proceed() }
                    }
                )
                return result
            }
        })
    }

    private fun hookStatusBarIcon(param: PackageReadyParam, miuiConfigs: Class<*>) {
        val containerClass = param.classLoader.findClass("com.android.systemui.statusbar.phone.NotificationIconContainer")
        val isFlipTinyScreen = miuiConfigs.method("isFlipTinyScreen", Context::class.java)
        val originInvoker = module!!.getInvoker(isFlipTinyScreen).setType(Type.ORIGIN)

        fun isFolded(thisObject: Any?): Boolean {
            val context = thisObject?.getField("mContext") ?: return false
            return originInvoker.invoke(null, context) as? Boolean ?: false
        }

        fun expandFoldedIcons(chain: Chain): Pair<HookHandle?, Int?> {
            if (!isFolded(chain.thisObject)) return null to null
            val handle = hook(isFlipTinyScreen, replaceResult(false))
            val maxIcons = chain.thisObject?.getField("mMaxIcons") as? Int
            chain.thisObject?.setField("mMaxIcons", statusBarIconMax())
            return handle to maxIcons
        }

        fun restoreFoldedIcons(chain: Chain, handle: HookHandle?, maxIcons: Int?) {
            handle?.unhook()
            maxIcons?.let { chain.thisObject?.setField("mMaxIcons", it) }
        }

        val foldedIconHooker = Hooker { chain ->
            val (handle, maxIcons) = expandFoldedIcons(chain)
            runWithCleanup({ restoreFoldedIcons(chain, handle, maxIcons) }) { chain.proceed() }
        }
        hook(containerClass.method("calculateIconXTranslations"), PRIORITY_LOWEST, foldedIconHooker)
        hook(containerClass.method("onMeasure", Int::class.java, Int::class.java), PRIORITY_LOWEST, foldedIconHooker)

        val statusIconClass = param.classLoader.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer")
        hook(statusIconClass.method("onMeasure", Int::class.java, Int::class.java)) { chain ->
            val handle = if (isFolded(chain.thisObject)) hook(
                isFlipTinyScreen,
                replaceResult(false)
            ) else null
            runWithCleanup({ handle?.unhook() }) { chain.proceed() }
        }
    }

    private fun statusBarIconMax(): Int {
        return prefInt(Prefs.SYSUI_STATUS_BAR_ICON_MAX, 3).coerceIn(1, 15)
    }

    private fun hookStatusBarClock(param: PackageReadyParam, miuiConfigs: Class<*>) {
        val isFlipTinyScreen = miuiConfigs.method("isFlipTinyScreen", Context::class.java)
        val originInvoker = module!!.getInvoker(isFlipTinyScreen).setType(Type.ORIGIN)

        fun isTiny(thisObject: Any?): Boolean {
            val ctx = thisObject?.callMethod("getContext") as? Context ?: return false
            return originInvoker.invoke(null, ctx) as? Boolean ?: false
        }

        val fragmentClass = param.classLoader.findClass("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment")

        // hideClock uses clockHiddenMode() return value as the visibility constant for setPolicyVisibility
        hook(fragmentClass.method("clockHiddenMode")) { chain ->
            if (isTiny(chain.thisObject)) 8 else chain.proceed()
        }

        // updateStatusBarVisibilities never calls hideClock on tiny screen because
        // z14 == mLastModifiedVisibility.showClock (both true), so we force it ourselves
        hook(fragmentClass.method("updateStatusBarVisibilities", Boolean::class.java)) { chain ->
            chain.proceed()
            if (isTiny(chain.thisObject)) chain.thisObject?.callMethod("hideClock", false)
        }

        hook(fragmentClass.method("showClock", Boolean::class.java)) { chain ->
            if (isTiny(chain.thisObject) && chain.args[0] == true) {
                chain.thisObject?.callMethod("hideClock", false)
            } else {
                chain.proceed()
            }
        }

        val isTinyScreen = miuiConfigs.method("isTinyScreen", Context::class.java)
        val tinyScreenInvoker = module!!.getInvoker(isTinyScreen).setType(Type.ORIGIN)
        val notificationCallbackClass =
            param.classLoader.findClass($$"com.android.systemui.controlcenter.shade.NotificationHeaderExpandController$notificationCallback$1")

        fun updateFlipNotificationClockSize(callback: Any?) {
            val controller = callback?.getField($$"this$0") ?: return
            val ctx = controller.getField("context") as? Context ?: return
            val tiny = tinyScreenInvoker.invoke(null, ctx) as? Boolean ?: false
            if (!tiny) return

            val resId = ctx.resources.getIdentifier(
                "qs_control_header_clock_flip_size",
                "dimen",
                ctx.packageName
            )
            if (resId == 0) return

            val size = ctx.resources.getDimensionPixelSize(resId)
            if (controller.getField("bigTimeSize") != size) {
                controller.setField("bigTimeSize", size)
                controller.setField("lastSetTextSizeExpansion", -1f)
            }
        }

        hook(notificationCallbackClass.method("onExpansionChanged", Float::class.java)) { chain ->
            updateFlipNotificationClockSize(chain.thisObject)
            chain.proceed()
        }
    }
}
