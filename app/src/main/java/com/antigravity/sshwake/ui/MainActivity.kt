package com.antigravity.sshwake.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.antigravity.sshwake.R
import com.antigravity.sshwake.databinding.ActivityMainBinding
import com.antigravity.sshwake.ui.commands.CommandsFragment
import com.antigravity.sshwake.ui.keys.KeysFragment
import com.antigravity.sshwake.ui.servers.ServersFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val commandsFragment = CommandsFragment()
    private val serversFragment = ServersFragment()
    private val keysFragment = KeysFragment()
    private var activeFragment: Fragment = commandsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 无论系统深浅色设置，状态栏图标与电池强制使用高对比度纯白
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_fragment, keysFragment, "KEYS").hide(keysFragment)
                .add(R.id.nav_host_fragment, serversFragment, "SERVERS").hide(serversFragment)
                .add(R.id.nav_host_fragment, commandsFragment, "COMMANDS")
                .commit()
            activeFragment = commandsFragment
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_commands -> {
                    switchFragment(commandsFragment, getString(R.string.title_commands))
                    true
                }
                R.id.menu_servers -> {
                    switchFragment(serversFragment, getString(R.string.title_servers))
                    true
                }
                R.id.menu_keys -> {
                    switchFragment(keysFragment, getString(R.string.title_keys))
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(target: Fragment, title: String) {
        if (activeFragment != target) {
            supportFragmentManager.beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit()
            activeFragment = target
            binding.topAppBar.title = title
        }
    }
}
