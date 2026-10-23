# 第三方来源

## Dockeep
- 项目：https://github.com/mattgdot/Dockeep
- 作者/版权：以上游源码和 LICENSE 中的原始声明为准，原文保留。
- 本分支基线：1b298da170308c06a6bb2410908e9240e96fabec。
- 许可证：GNU General Public License v3.0（仓库 LICENSE）。
- 匣子修改：私有存储、只读 Provider、导入副本校验、导出、预览、中文、主题、图标和最小构建适配。
- Anemo 与 Material Files 未复制任何源码，也未作为依赖打包。

## 主要运行依赖
- AndroidX Core、Activity、Lifecycle、Compose、Navigation、DataStore、DocumentFile：Android Open Source Project / AndroidX，Apache License 2.0。
- Kotlin 标准库、Kotlin Coroutines：JetBrains，Apache License 2.0。
- Kotlinx Serialization、Okio：传递依赖，Apache License 2.0。
- Android 平台文件、图像和 PDF API：设备平台组件。

具体直接依赖版本见 app/build.gradle.kts 和 gradle/libs.versions.toml，传递依赖以 Gradle 解析结果为准。
测试依赖 JUnit、Robolectric 只用于开发，不打包进 APK。
原项目中的 privacy_policy.md 和 terms_conditions.md 属于上游历史文件，本版未在应用内引用；本版行为说明以 README-PocketVault.md 为准。
