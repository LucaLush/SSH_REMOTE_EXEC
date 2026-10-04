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
    private lateinit var commandsFragment: CommandsFragment
    private lateinit var serversFragment: ServersFragment
    private lateinit var keysFragment: KeysFragment
    private var activeFragment: Fragment? = null

    companion object {
        private const val TAG_COMMANDS = "COMMANDS"
        private const val TAG_SERVERS = "SERVERS"
        private const val TAG_KEYS = "KEYS"
        private const val KEY_ACTIVE_TAG = "KEY_ACTIVE_TAG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 无论系统深浅色设置，状态栏图标与电池强制使用高对比度纯白
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        binding.topAppBar.setNavigationOnClickListener {
            com.antigravity.sshwake.ui.about.AboutDialog(this).show()
        }

        if (savedInstanceState == null) {
            commandsFragment = CommandsFragment()
            serversFragment = ServersFragment()
            keysFragment = KeysFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_fragment, keysFragment, TAG_KEYS).hide(keysFragment)
                .add(R.id.nav_host_fragment, serversFragment, TAG_SERVERS).hide(serversFragment)
                .add(R.id.nav_host_fragment, commandsFragment, TAG_COMMANDS)
                .commit()
            activeFragment = commandsFragment
            binding.topAppBar.title = getString(R.string.title_commands)
        } else {
            // 系统长时间在后台被杀死后重新打开：必须从 FragmentManager 认领已恢复的真实实例
            commandsFragment = supportFragmentManager.findFragmentByTag(TAG_COMMANDS) as? CommandsFragment ?: CommandsFragment()
            serversFragment = supportFragmentManager.findFragmentByTag(TAG_SERVERS) as? ServersFragment ?: ServersFragment()
            keysFragment = supportFragmentManager.findFragmentByTag(TAG_KEYS) as? KeysFragment ?: KeysFragment()

            val activeTag = savedInstanceState.getString(KEY_ACTIVE_TAG, TAG_COMMANDS)
            val currentTarget = when (activeTag) {
                TAG_SERVERS -> serversFragment
                TAG_KEYS -> keysFragment
                else -> commandsFragment
            }
            activeFragment = currentTarget

            binding.topAppBar.title = when (activeTag) {
                TAG_SERVERS -> getString(R.string.title_servers)
                TAG_KEYS -> getString(R.string.title_keys)
                else -> getString(R.string.title_commands)
            }

            // 同步恢复各 Fragment 真实显示状态，保证只有当前项处于显示状态
            val transaction = supportFragmentManager.beginTransaction()
            listOf(commandsFragment, serversFragment, keysFragment).forEach { f ->
                if (f.isAdded) {
                    if (f == currentTarget) transaction.show(f) else transaction.hide(f)
                }
            }
            transaction.commit()

            // 同步底部导航栏选中的 Tab
            val targetNavId = when (activeTag) {
                TAG_SERVERS -> R.id.menu_servers
                TAG_KEYS -> R.id.menu_keys
                else -> R.id.menu_commands
            }
            if (binding.bottomNavigation.selectedItemId != targetNavId) {
                binding.bottomNavigation.selectedItemId = targetNavId
            }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val activeTag = when (activeFragment) {
            serversFragment -> TAG_SERVERS
            keysFragment -> TAG_KEYS
            else -> TAG_COMMANDS
        }
        outState.putString(KEY_ACTIVE_TAG, activeTag)
    }

    private fun switchFragment(target: Fragment, title: String) {
        if (activeFragment != target) {
            val transaction = supportFragmentManager.beginTransaction()
            listOf(commandsFragment, serversFragment, keysFragment).forEach { f ->
                if (f != target && f.isAdded) {
                    transaction.hide(f)
                }
            }
            if (target.isAdded) {
                transaction.show(target)
            } else {
                val tag = when (target) {
                    serversFragment -> TAG_SERVERS
                    keysFragment -> TAG_KEYS
                    else -> TAG_COMMANDS
                }
                transaction.add(R.id.nav_host_fragment, target, tag)
            }
            transaction.commit()
            activeFragment = target
            binding.topAppBar.title = title
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次切回主应用界面时，自动巡检桌面小部件，恢复任何超期的变色状态
        com.antigravity.sshwake.widget.WidgetManager.checkAndResetExpiredWidgets(this)
    }
}
