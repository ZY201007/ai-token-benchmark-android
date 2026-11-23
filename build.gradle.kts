// 顶层构建文件：集中声明插件版本，app 模块通过 apply false / 别名引用
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}
