<div align="center">

<img src="assets/icon.png" width="96" alt="虎仔" />

# 虎仔

**虎扑社区第三方 Android 客户端 · 非官方**

*An unofficial, open-source community client for Hupu (虎扑), built with Kotlin and Jetpack Compose.*

[![License](https://img.shields.io/badge/License-GPL--3.0-6e7781.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](#构建)
[![minSdk](https://img.shields.io/badge/minSdk-24-6e7781.svg)](#构建)

[功能](#功能) · [截图](#截图) · [技术栈](#技术栈) · [构建](#构建) · [下载](#下载) · [隐私](#数据与隐私)

</div>

---

## 免责声明

> 本项目为**个人学习与交流**项目，**与虎扑官方及其运营方无任何关联**，非官方应用。

- 数据来源为虎扑**公开网页与接口**，仅用于客户端展示；**所有内容（图文、评论等）版权归虎扑及其用户所有**。
- **禁止任何形式的商业用途**（包括但不限于上架应用市场、付费分发、植入广告）。
- 本项目**不提供**任何内容的下载、存储或二次分发服务。
- 若相关权利人认为本项目存在不妥，请通过 Issues 联系，将及时处理。

---

## 功能

- **首页**：热帖 / 推荐 / 话题横滑条，双形态卡片流，骨架屏 + 下拉刷新 + 缓存秒开
- **专区**：二百余个子分区浏览，二级盖入式帖子流
- **评分**：通用评分主题流 + 电竞项目赛程；赛程卡、对局 / 选手详情、评论楼中楼、打分与点亮
- **NBA 赛事数据**：与「评分」并列的「数据统计 / 文字实录」——球员数据表、球员 PK / 本场数据帝 / 球队统计三块对比、按节次的事件流（比赛进行中自动滚到最新）
- **帖子详情**：纯 Compose HTML 渲染（图文保序）、视频播放（小窗 / 全屏，双击 ±15 秒 / 滑动调速 / 左右半屏调亮度与音量 / 长按 2 倍速）、图片查看、结构化正文、回复排序（默认 / 最新 / 最热）与只看楼主、楼中楼逐层、发帖 / 编辑 / 删除、投票
- **横滑切 Tab**：首页 / 专区 / 评分顶部 Tab 支持左右滑动切换（仅单页内，不跨大页面）
- **登录与互动**：WebView 登录接管 Cookie、帖子推荐、评论点亮 / 取消点亮、发评论、楼中楼回复
- **私信**：会话列表 + 聊天页（文字 / 单图）、消息中心未读角标、站内链接直达帖子
- **用户主页**：资料卡、关注 / 取关、发帖 / 回帖 / 推荐 / 收藏 / 关注列表
- **检查更新**：打开软件自动检查（10 分钟去重）、更新弹窗可选「忽略此版本」、关于页一键手动检查，发现新版提示并跳转下载
- **个性化**：默认事项、悬浮毛玻璃 Tab 栏、主页与评分频道自定义、信息流关键词过滤、阅读字号、屏幕刷新率、深浅模式 + 主题（7 套色彩主题 / 动态取色）
- **数据同步**：同一 WiFi 下与另一台设备互传关键词 / 黑名单 / 收藏专区 / 评分频道 / 首页频道与默认事项

---

## 截图

**首页 · 专区**

<table align="center">
  <tr>
    <td align="center"><img src="screenshots/home_phone.jpg" width="240" alt="首页（手机）" /><br/>首页 · 手机</td>
    <td align="center"><img src="screenshots/zones.jpg" width="240" alt="专区 · 子分区浏览" /><br/>专区 · 子分区</td>
  </tr>
  <tr>
    <td align="center" colspan="2"><img src="screenshots/home_tablet.jpg" width="480" alt="首页（平板，自适应布局）" /><br/>首页 · 平板（自适应布局）</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/zone_detail.jpg" width="240" alt="专区 · 分区帖子流" /><br/>专区 · 分区帖子流</td>
    <td align="center"><img src="screenshots/thread.jpg" width="240" alt="帖子详情 · 图文混排" /><br/>帖子详情 · 图文混排</td>
  </tr>
</table>

**评分 · 赛程 · 对局 · 选手**

<table align="center">
  <tr>
    <td align="center"><img src="screenshots/score.jpg" width="240" alt="通用评分主题流" /><br/>通用评分主题流</td>
    <td align="center"><img src="screenshots/score_schedule.jpg" width="240" alt="赛事赛程" /><br/>赛事赛程</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/score_match.jpg" width="240" alt="对局详情" /><br/>对局详情</td>
    <td align="center"><img src="screenshots/score_player.jpg" width="240" alt="选手详情与打分" /><br/>选手详情 · 打分</td>
  </tr>
</table>

**发帖 · 消息 · 我的**

<table align="center">
  <tr>
    <td align="center"><img src="screenshots/newpost.jpg" width="240" alt="发帖编辑器" /><br/>发帖编辑器</td>
    <td align="center"><img src="screenshots/messages.jpg" width="240" alt="消息中心 · 会话列表" /><br/>消息中心</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/chat.jpg" width="240" alt="私信聊天页" /><br/>私信聊天</td>
    <td align="center"><img src="screenshots/profile.jpg" width="240" alt="用户主页" /><br/>用户主页</td>
  </tr>
</table>

**个性化设置**

<table align="center">
  <tr>
    <td align="center"><img src="screenshots/mine.jpg" width="240" alt="我的 · 个性化设置" /><br/>我的 · 个性化设置</td>
  </tr>
</table>

---

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 悬浮毛玻璃 Tab 栏 | [kyant/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`backdrop` / `capsule` / `shapes`，Apache-2.0） |
| 网络 | OkHttp（SSR HTML + JSON 接口抓取与解析） |
| 图片 / 视频 | Coil · Media3 ExoPlayer |
| 构建 | Gradle Version Catalog（AGP 9.1 / Kotlin 2.3） |
| 兼容 | minSdk 24 · targetSdk 35 · compileSdk 37 |

---

## 构建

环境要求：**JDK 17+**、Android SDK。

```bash
./gradlew assembleDebug        # 调试包
./gradlew testDebugUnitTest    # 单元测试
./gradlew assembleRelease      # 正式包（需自备签名配置，见下）
```

产物路径：`app/build/outputs/apk/<variant>/app-<variant>.apk`

> **正式包说明**：`app/build.gradle.kts` 的 `signingConfig` 从仓库根 `keystore.properties` 读取
> （该文件与 `keystore/` 均**不入库**）。clone 后若不提供 `keystore.properties`，`assembleRelease`
> 仍产出**未签名包**；自备 keystore 并创建该文件后即可得到可安装的正式签名包。

---

## 下载

最新版本 **v1.202** · [`huzai-1.202-release.apk`](https://github.com/Kanezikiiu/Huzai/releases/download/v1.202/huzai-1.202-release.apk)（minSdk 24，正式签名）

全部版本见 **[Releases](https://github.com/Kanezikiiu/Huzai/releases)**。

---

## 项目结构

```
app/src/main/java/com/java/myapplication/
├── MainActivity.kt          # 四 Tab 常驻 + 盖入式转场 + 主题应用
├── HupuApp.kt               # Application（Coil ImageLoader / 崩溃取证，默认关闭）
├── data/                    # 数据层：Api / Parser / Repository / 账号 / 缓存 / 偏好 / 图片 / 节流
└── ui/
    ├── pages/               # 各页面（首页 / 专区 / 评分 / 我的 / 帖子详情 / 私信 / 用户主页…）
    ├── components/          # 通用组件与自绘图标
    ├── glass/               # 悬浮毛玻璃 Tab 栏（基于 kyant AndroidLiquidGlass）
    └── theme/               # 主题（浅色 / 深色）
```

仓库根另有 `screenshots/`（README 截图）与 `assets/`（README 图标）。

---

## 数据与隐私

- 登录凭据（Cookie）**仅保存在本机**，不向任何第三方服务器上传。
- 应用**不收集**用户个人信息，无统计 / 埋点 SDK。
- 已关闭全局明文 HTTP：`network_security_config.xml` 仅放行虎扑自有域名。
- 登录凭据与崩溃取证文件均已从系统自动备份中排除。
- 崩溃取证**默认关闭**；开启后仅在本机记录崩溃现场，可随时在「关于 → 崩溃日志」查看或清除。

---

## 许可证

本项目基于 **GNU General Public License v3.0** 开源，详见 [LICENSE](LICENSE)。

> 依据 GPL-3.0：你可以自由使用、修改、分发本项目，但**衍生作品须同样以 GPL-3.0 开源**，
> 且不得附加额外限制。

---

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose) · [OkHttp](https://square.github.io/okhttp/) · [Coil](https://coil-kt.github.io/coil/) · [Media3](https://developer.android.com/media/media3)
- [kyant/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) —— 悬浮毛玻璃 Tab 栏所依赖的毛玻璃引擎（Apache-2.0）

---

## 反馈

欢迎通过 **Issues** 提交问题或建议。

> 本项目为个人学习作品，**不接受**任何形式的商业合作；也请勿将其用于违反虎扑服务条款或相关法律的用途。
