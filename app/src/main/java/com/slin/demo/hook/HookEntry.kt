package com.slin.demo.hook

import com.highcapable.yukihookapi.BuildConfig
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.YukiHookAPI.configs
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onHook() = YukiHookAPI.encase {
        // Your code here.
//        this.prefs.
        loadApp {
            loadHooker(LocationHooker)
            loggerD("slin", "load LocationHooker 1")
        }
    }
    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
    }
}