# 北北计算器长期发布签名

从 0.3.15 起，用户长期安装链使用独立的 Release 身份，不再使用 Debug APK 作为正式分发包。

## 固定身份

- Application ID：`com.beibei.calculator`
- Release key alias：`beibei-release`
- Release certificate SHA-256：`DA6901B21ED9CCD8E33F4BFA6D2726183913F4E6C13910705E210184A17A5D33`
- 0.3.15：versionCode 329

## CI 规则

- `.github/workflows/android.yml` 只构建开发 Debug APK；Debug 签名不属于用户升级链。
- `.github/workflows/stable-release.yml` 只从 GitHub Actions Secret `BEIBEI_SIGNING_BUNDLE_B64` 恢复长期 Release keystore。
- 正式发布前必须同时校验 Application ID 与证书 SHA-256；任一不匹配，CI 直接失败并禁止发布。
- 不允许再用 GitHub Actions cache 保存或生成正式签名密钥。

## 密钥恢复

私钥、store/key 密码和 GitHub Secret 原文不进入仓库。维护者必须离线保存签名备份。丢失 Release 私钥后，无法继续对同一 Application ID 做普通覆盖更新。
