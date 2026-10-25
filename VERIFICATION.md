# 0.3.1 外观调整验证记录

2026-10-25：蓝白轻量外观，versionCode 7。保留原应用 ID、签名配置和所有文件操作逻辑；默认 AUTO 显示蓝白，已有 LIGHT 暖白选择继续保留。主界面、备份页与原生预览页统一默认配色。

本轮完成修改差异检查、主题默认值核对和主要文字配色对比度计算。恢复编译环境后，0.3.1 assembleDebug 成功（37 项任务），生成 APK。apksigner 验签通过，证书 SHA-256 为 897e2d32075f0aa8b35dacb4263cd2e243e37cad06851a042f2cb63f3445b5da，与上一版一致；包名 com.pocketvault.app，versionCode 7、versionName 0.3.1。未运行本轮 Robolectric 或真机外观验收，构建成功不等同于真机视觉验收。APK 仅单独交付，不上传仓库。

编译需要 JDK 17、SDK 35、Gradle 8.7，以及原 POCKETVAULT_KEYSTORE。未新增依赖。安装时必须使用原签名覆盖安装，避免丢失私有文件。

## 上版已完成验证（不作为本轮测试结果）

2026-10-24：0.3.0 assembleDebug 成功，原签名验证通过。VaultMoverTest 18 项、CrossPaneMoveTest 在 API 34 下 1 项，共 19 项通过、0 失败。未在实体 OPPO / Android 16 上验证。
