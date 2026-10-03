# 线程模型与组件生命周期 (Threading Model & Lifecycle)

[返回开发文档目录](README.md)

---

## 1. 协程与线程调度模型 (Coroutines Architecture)

本项目完全基于 Kotlin 协程构建，杜绝在主线程进行任何磁盘 IO、数据库查询与网络通信。

```mermaid
graph LR
    subgraph MainThread["Dispatchers.Main (UI / 主线程)"]
        UI_Observe[LiveData 数据监听]
        UI_Dialog[弹窗更新 / Toast 提示]
        Widget_RemoteViews[RemoteViews 图标与背景切换]
    end

    subgraph IOThread["Dispatchers.IO (工作线程池)"]
        Room_DB[Room DAO 增删改查]
        Keystore_Crypto[AES-256 加解密计算]
        SSH_Connect[TCP Socket 握手与连接]
        SSH_Exec[Session 创建与命令执行]
        Stream_Read[输出流读取与聚合]
    end

    UI_Observe -->|withContext / launch| IOThread
    IOThread -->|withContext(Dispatchers.Main)| MainThread
```

### 调度器分工
1. **`Dispatchers.Main`**：
   - 处理界面 RecyclerView 滚动、点击事件；
   - 展现 Material Dialogs 与 Toast 状态提醒；
   - 更新 RemoteViews 并向 `AppWidgetManager` 提交更新。
2. **`Dispatchers.IO`**：
   - 所有的 `SSHExecutor` 挂起函数（`executeCommand`、`testConnection`）显式标记为 `withContext(Dispatchers.IO)`，内部自带线程安全切换，调用方无需额外指定工作线程；
   - Room 数据库查询。

---

## 2. 桌面小部件 (AppWidget) 与 BroadcastReceiver 的生命周期处理

### Android 12+ 广播限制与后台设计
在 Android 8.0 ~ 14 中，Google 对后台 Service 和 BroadcastReceiver 施加了严格限制：
- 静态注册的 `BroadcastReceiver` 的 `onReceive` 必须在几秒内返回，禁止启动后台 Service；
- 若长时间占用 `onReceive`，系统会判定并触发 ANR。

### 协程作用域方案 (`SupervisorJob`)、`goAsync()` 与防并发机制
在 `SSHWidgetProvider` 中：
```kotlin
class SSHWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val activeRunningWidgets = ConcurrentHashMap.newKeySet<Int>()
}
```
1. **为什么用 `SupervisorJob`**：
   - 保证若某次 SSH 连接由于超时抛出异常，**不会连带取消整个 Provider 的协程作用域**，后续的小部件点击依然能正常响应。
2. **防重并发互斥锁**：
   - 在接收到点击事件时，校验 `activeRunningWidgets` 以及 SharedPreferences 中的状态；
   - 若当前小部件已处于 `RUNNING` 且未超期，立即弹 Toast 提示“正在执行中，请稍候”并返回，彻底杜绝短时间快速连点导致并发发包、SSH 连接池打满或远端服务器状态错乱。
3. **`goAsync()` 保活机制**：
   - 调用 `val pendingResult = goAsync()`，向 Android ActivityManager 注册异步广播生命周期，确保在网络通信期间进程不被系统视为空闲而提前强制休眠；
   - 最终在 `finally` 块中调用 `pendingResult.finish()` 释放广播。
4. **AlarmManager 硬件唤醒重置**：
   - 传统 `delay()` 无法穿透系统的熄屏深度休眠（Deep Sleep）或进程被 LMK 冻结清理；
   - 本项目通过 `AlarmManager.setExactAndAllowWhileIdle` 结合 `ACTION_RESET_WIDGET_STATE` 广播，即便手机熄屏，系统级 Alarm 触发后必定唤醒设备并将小部件恢复至 `IDLE` 初始默认颜色。

---

## 3. UI 生命周期与内存防泄漏 (Memory Leak Prevention)

### Fragment ViewBinding 生命周期规范
在 `CommandsFragment`、`ServersFragment`、`KeysFragment` 中均严格遵循 Android 官方的 ViewBinding 释放规范：
```kotlin
private var _binding: FragmentCommandsBinding? = null
private val binding get() = _binding!!

override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentCommandsBinding.inflate(inflater, container, false)
    return binding.root
}

override fun onDestroyView() {
    super.onDestroyView()
    // 必须在 onDestroyView 中置 null，防止 Fragment View 销毁后仍被 binding 强引用导致内存泄露
    _binding = null
}
```

### 协程生命周期绑定 (`viewLifecycleOwner.lifecycleScope`)
- 在 Fragment 中发起的所有数据操作（如弹窗保存、删除确认）均绑定在 `viewLifecycleOwner.lifecycleScope` 上；
- 用户如果在网络请求中快速切换 Tab 或退出当前页面，关联的协程会自动取消，杜绝空指针与 View 泄露。
