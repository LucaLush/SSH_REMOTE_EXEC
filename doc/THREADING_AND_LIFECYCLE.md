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

### 协程作用域方案 (`SupervisorJob`)
在 `SSHWidgetProvider` 中：
```kotlin
class SSHWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
```
1. **为什么用 `SupervisorJob`**：
   - 保证若某次 SSH 连接由于超时抛出异常，**不会连带取消整个 Provider 的协程作用域**，后续的小部件点击依然能正常响应。
2. **异步执行与即时响应**：
   - 用户点击小部件后，`onReceive` 立即通过 `WidgetManager.updateWidgetView` 将小组件界面切换至 `RUNNING` 态（蓝色转圈）并返回；
   - `scope.launch` 触发后台协程切入 `Dispatchers.IO` 执行 SSH 连接与通信；
   - 结果返回后切回 `Dispatchers.Main`，更新小组件为 `SUCCESS`（绿色）或 `ERROR`（红色）；
   - 通过 `delay(800)` / `delay(2000)` 平滑自动重置回 `IDLE` 待命态。

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
