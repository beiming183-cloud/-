# 北北计算器长期发布签名

从 0.3.15 起，用户长期安装链使用独立的 Release 身份，不再使用 Debug APK 作为正式分发包。

## 固定身份

- Application ID：`com.beibei.calculator`
- Release key alias：`beibei-release`
- Release certificate SHA-256：`DA6901B21ED9CCD8E33F4BFA6D2726183913F4E6C13910705E210184A17A5D33`
- 0.3.15：versionCode `329`

0.3.15 已经通过长期签名 CI 并发布，是新的正式覆盖更新基线。后续 0.3.16、0.4.0、1.0.0 等正式版本必须沿用相同 Application ID 与相同 Release key。

## CI 规则

- `.github/workflows/android.yml` 只构建开发 Debug APK；Debug 签名不属于用户升级链。
- `.github/workflows/stable-release.yml` 只从 GitHub Actions Secret `BEIBEI_SIGNING_BUNDLE_B64` 恢复长期 Release keystore。
- 正式发布的 tag 必须使用 `beibei-X.Y.Z` 格式。
- 已存在的 Release tag 禁止覆盖；修改后必须发布新的版本号。
- APK 内 `versionName` 必须与 tag 中的版本完全一致。
- APK 必须能够读取有效 `versionCode`。
- Application ID 必须等于 `com.beibei.calculator`。
- Release certificate SHA-256 必须等于本页记录的固定指纹。
- Release 构建日志若出现 R8 `Invalid stack map table`，CI 直接失败并禁止发布。
- 任一校验失败，CI 直接失败并禁止发布。
- 不允许再用 GitHub Actions cache 保存或生成正式签名密钥。

## R8 兼容性

0.3.15 的首次正式构建曾对 `StatisticsEngine.TwoVariableResults` 报出 R8 `Invalid stack map table` 警告。根因是大型 Java 17 record 的生成字节码与当前 R8 版本之间的兼容性问题，而不是统计公式错误。

Stage 3 已将该结果载体改为保持相同构造参数与 `n()`、`meanX()`、`sampleVarianceX()` 等访问接口的普通不可变类。Release 专用构建在不使用额外 ProGuard keep 规则的情况下复验通过，警告已消失。长期发布 workflow 现在把同类 warning 作为 fail-closed 门禁。

## 版本发布约定

每次正式发布前先在 `app/build.gradle` 增加 `versionCode` 并更新 `versionName`，再运行长期发布 workflow，并输入与 `versionName` 对应的 tag。不要替换已有 Release 的 APK，也不要重复使用旧 tag。

## 密钥恢复

私钥、store/key 密码和 GitHub Secret 原文不进入仓库。维护者必须离线保存签名备份，并至少保留一份独立副本。丢失 Release 私钥后，无法继续对同一 Application ID 做普通覆盖更新。
