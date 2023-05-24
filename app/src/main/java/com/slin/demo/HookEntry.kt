package com.slin.demo

import com.highcapable.yukihookapi.BuildConfig
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.YukiHookAPI.configs
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onHook() = YukiHookAPI.encase {
        // Your code here.
//        this.prefs.
    }
    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
    }
}