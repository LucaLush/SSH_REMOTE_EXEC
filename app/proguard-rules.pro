# 保留 BouncyCastle 算法提供者与实现
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# 保留 sshj 协议核心与关联依赖
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn sun.security.**

# 保留 Room 数据库与反射
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# 保留 Android 原生组件
-keep public class * extends android.app.Activity
-keep public class * extends android.appwidget.AppWidgetProvider
-keep public class * extends android.app.Application

# 优化混淆等级
-repackageclasses
-allowaccessmodification
