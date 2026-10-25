# 0.3.1 外观调整验证记录

2026-10-25：蓝白轻量外观，versionCode 7。保留原应用 ID、签名配置和所有文件操作逻辑；默认 AUTO 显示蓝白，已有 LIGHT 暖白选择继续保留。主界面、备份页与原生预览页统一默认配色。

本轮完成修改差异检查、主题默认值核对、主要文字配色对比度计算。当前工作区的 Android SDK / Gradle 缓存已被自动维护回收，未执行 0.3.1 Android 编译、Robolectric 或真机外观验收，没有生成新版 APK。源码审查不等同于编译通过。

编译需要 JDK 17、SDK 35、Gradle 8.7，以及原 POCKETVAULT_KEYSTORE。未新增依赖。安装时必须使用原签名覆盖安装，避免丢失私有文件。

## 上版已完成验证（不作为本轮测试结果）

2026-10-24：0.3.0 assembleDebug 成功，原签名验证通过。VaultMoverTest 18 项、CrossPaneMoveTest 在 API 34 下 1 项，共 19 项通过、0 失败。未在实体 OPPO / Android 16 上验证。
