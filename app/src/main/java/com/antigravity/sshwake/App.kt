package com.antigravity.sshwake

import android.app.Application
import com.antigravity.sshwake.data.AppDatabase
import com.antigravity.sshwake.security.CryptoHelper

class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        val database: AppDatabase by lazy {
            AppDatabase.getInstance(instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 预热 Keystore 加密环境
        CryptoHelper.init(this)
    }
}
