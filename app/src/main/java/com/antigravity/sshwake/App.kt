package com.antigravity.sshwake

import android.app.Application
import com.antigravity.sshwake.data.AppDatabase
import com.antigravity.sshwake.security.CryptoHelper

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        val database: AppDatabase by lazy {
            AppDatabase.getInstance(instance)
        }

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 移除系统自带阉割版 BC，将现代 BouncyCastleProvider 插入到第一优先级，解决 X25519 算法缺失问题
        setupBouncyCastle()

        // 预热 Keystore 加密环境
        CryptoHelper.init(this)

        // 启动时巡检重置任何异常驻留的桌面小组件状态
        com.antigravity.sshwake.widget.WidgetManager.checkAndResetExpiredWidgets(this)
    }

    private fun setupBouncyCastle() {
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (_: Exception) {
        }
    }
}
