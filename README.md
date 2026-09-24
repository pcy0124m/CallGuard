# 拦截助手（CallGuard）

一款 Android 原生应用，集 **电话拦截、短信拦截、广告拦截** 三大功能于一体，
所有拦截记录均可在主界面对应标签页中查看。

## 安装

直接安装 `CallGuard-拦截助手-v1.0-debug.apk`（Android 10 及以上）：
传输到手机后点击安装；如系统提示"未知来源应用"，允许本次安装即可。
首次启动按提示完成三项授权：短信权限、来电拦截角色、VPN 权限。

## 功能说明

### 1. 电话拦截（系统级，真实拒接）
- 通过系统 `CallScreeningService` 实现来电筛选，命中规则的来电**自动拒接**，且不写入系统通话记录、不弹系统通知。
- 拦截规则：
  - 手动黑名单号码（主界面"添加黑名单"维护）；
  - 营销/客服号段（400/95/96/1010/1009 开头，默认开启）；
  - 未知号码。
- 每条被拦截的来电都会记录：号码、拦截原因、时间，并弹通知提醒。

### 2. 短信拦截（识别 + 记录 + 通知）
- 监听系统短信广播，基于关键词识别垃圾短信（贷款、中奖、刷单、退订回T 等）。
- 命中的短信会记录：发送方、完整内容、命中的关键词、时间，并弹通知提醒。
- **系统限制说明**：Android 4.4 之后，只有被设为"默认短信应用"才能阻止短信进入收件箱。
  本应用采用"识别 + 记录 + 通知"方式，正常短信不受影响。

### 3. 广告拦截（本地 VPN + DNS 过滤）
- 原理：建立本地 VPN，把系统 DNS 指向 VPN 内的假 DNS 地址，只路由 DNS 查询包：
  - 命中广告域名黑名单 → 返回 `0.0.0.0`，广告加载失败即被拦截；
  - 未命中 → 转发到上游真实 DNS（223.5.5.5），正常上网不受影响。
- 内置 20+ 常见广告/追踪域名（doubleclick、admob、umeng、appsflyer 等），
  可在主界面"添加广告域名"追加自定义域名。
- 拦截记录按域名聚合计数（域名、拦截次数、最近拦截时间），实时展示。

## 界面

主界面三个标签页：**电话拦截 / 短信拦截 / 广告拦截**，每页展示对应拦截记录列表
（每 3 秒自动刷新）；顶部状态栏显示各功能生效状态；提供"授权来电拦截 /
开启广告拦截 / 添加黑名单 / 添加广告域名 / 清空记录"操作按钮。

## 从源码构建

### 方式一：Android Studio（推荐）
用 Android Studio 打开 `CallGuard` 项目根目录，Gradle 同步后 Run 到手机即可。

### 方式二：命令行手动编译（无需 Gradle）
本项目零第三方运行时依赖（仅 Kotlin 标准库），可用以下工具链手动编译：
`aapt2`（资源）+ `kotlinc`（Kotlin 2.0）+ `d8`（脱糖转 dex）+ `apksig`（v1/v2/v3 签名）。

## 技术结构

```
app/src/main/java/com/kuku/callguard/
├── MainActivity.kt                  # 主界面：三标签页展示拦截记录
├── data/
│   ├── Entities.kt                  # BlockedCall / BlockedSms / BlockedAd 数据类
│   ├── Db.kt                        # SQLiteOpenHelper 本地数据库
│   └── Prefs.kt                     # 黑名单、号段开关、自定义广告域名
├── call/CallScreeningServiceImpl.kt # 来电拦截（系统 CallScreeningService）
├── sms/
│   ├── SmsReceiver.kt               # 短信广播接收 + 入库 + 通知
│   └── SpamFilter.kt                # 垃圾短信关键词识别
├── ad/AdBlockVpnService.kt          # 本地 VPN DNS 广告过滤
└── util/Notifier.kt                 # 拦截通知与前台服务通知
```

- 最低系统：Android 10（API 29）；目标 SDK：34；Kotlin 2.0
- 无 AndroidX / Room / Material 依赖，纯平台 API 实现

## 注意事项

- 电话拦截依赖系统角色授权，部分厂商 ROM（MIUI/EMUI 等）可能隐藏该入口，
  可到 系统设置 → 电话 → 骚扰拦截 中手动选择本应用。
- 广告拦截基于 DNS 层过滤，无法拦截与正文同域名的广告；如需更彻底的过滤可扩充黑名单。
- 关键词规则可在 `sms/SpamFilter.kt` 中自行增删；号段规则可在
  `call/CallScreeningServiceImpl.kt` 的 `SERVICE_PREFIXES` 中调整。
- APK 为 debug 签名，仅供个人安装使用；如需上架或长期使用，建议用 Android Studio
  生成正式签名的 release 包。
