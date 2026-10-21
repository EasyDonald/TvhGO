# TvhGO

**A lightweight Android client for [Tvheadend](https://tvheadend.org/) — channels, EPG, DVR and server status, in one small APK.**

No AndroidX, no ExoPlayer, no libVLC, no third-party runtime libraries — just the Android framework. The whole app is ~100 KB.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-5.0%2B-brightgreen.svg)](https://developer.android.com)
[![APK size](https://img.shields.io/badge/APK-~100%20KB-blue.svg)](https://github.com/easydonald/TvhGO/releases)

> 中文说明见下方 [中文](#中文)

---

## Features

### Channels
- Channel list with number / picon on the left, name and subtitle on the right
- Search by channel name or number (clear button embedded in the search field)
- Group (channel tag) filter with a custom-drawn picker
- Subtitle can show either the **group** or the **currently airing programme**
- Tap a channel → **channel detail page**: EPG per day (today / tomorrow / MM-dd, up to **7 days**), record a programme, play, copy stream URL, per-channel stream profile

### Playback
- Opens with the player you pick — the list is fully user-editable (**name + package**, add / edit / delete, switch), VLC is preloaded; “System chooser” is always available
- Stream URL = `http://user:pass@host:9981/stream/channel/<uuid>?profile=<name>`
- Stream profile can be chosen per server (default) and overridden per channel

### EPG
- Timeline grid (time × channel) with drag scrolling, a "now" line and tap-to-play/record blocks

### DVR
Mirrors the Tvheadend web UI's DVR tabs:
- **Upcoming / Finished / Failed / Removed / Autorec / Timerec**
- Upcoming / recording: **edit** (title, start, stop, pre/post minutes, enabled), cancel, stop, delete
- All DVR lists are **paged** (400 per request, follows 	otal) — no 200-item cap
- Finished: play / **download to phone** (system DownloadManager with Basic auth header) / delete, header shows total size
- Failed & Removed: **re-record** (`api/dvr/entry/rerecord/toggle`)
- Autorec & Timerec: list rules, enable/disable, delete, details

### Status
- Server info, active subscriptions (user / client / channel / kbps), input streams (bitrate / signal)
- Tap a subscription to see details and **drop that user's connections** (`api/connections/cancel` — same call as the web UI's *Status → Connections → Cancel*)

### Settings
- Multiple server profiles: switch / edit / add
- **Default stream profile**, read from the server (`api/idnode/load?class=profile`)
- Channel list: left column (number / picon), subtitle content
- **Theme**: follow system / dark / light (whole UI, pickers and dialogs included)
- **Language**: 简体中文 / 繁體中文 / English / 日本語
- Long-press a channel: play, copy stream URL, EPG, edit name/number, copy channel ID

### UI
Everything is hand-drawn on plain framework views:
- Bottom navigation with a pill indicator that hugs the label
- Full-bleed lists with 1px separators
- Custom dropdowns and segmented controls (no `Spinner`)
- **Fully custom dialogs** (`Sheet.java`) — bottom card, drag handle, rounded corners, no `AlertDialog`
- Vector icons (`VectorDrawable`) so nothing depends on font metrics

## Build

Requirements:
- JDK 21
- Android SDK: `platforms/android-34`, `build-tools/35.0.0` (34's `d8` has an NPE bug on anonymous classes implementing framework interfaces)

There is **no Gradle build**; `build.ps1` runs the toolchain by hand:

```
aapt2 compile → aapt2 link → javac → d8 → insert classes.dex → zipalign → apksigner
```

```powershell
# Windows PowerShell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Set `ANDROID_SDK_ROOT` (or edit the paths at the top of `build.ps1`). The script produces `TvhGO-<version>.apk` signed with a local debug keystore.

### Project layout

```
.
├── android/
│   ├── AndroidManifest.xml
│   ├── build.gradle                  # optional, for opening in Android Studio
│   ├── res/
│   │   ├── values/strings.xml
│   │   └── drawable/                 # picon fallback, vector icons
│   └── src/com/easydonald/tvhgo/
│       ├── MainActivity.java          # 5 tabs: channels / DVR / EPG / status / settings
│       ├── ChannelDetailActivity.java # per-channel EPG, per-channel profile
│       ├── EpgTimelineView.java       # timeline grid
│       ├── TvhApi.java                # all Tvheadend REST calls
│       ├── Sheet.java                 # custom dialog / bottom sheet
│       ├── Theme.java                 # dark/light palette
│       ├── I18n.java                  # zh-CN / zh-TW / en / ja dictionary
│       ├── ImageLoader.java           # picon loader (LRU + Basic auth)
│       └── Models.java, Channel.java, ServerProfile.java, Prefs.java
├── build.ps1
├── add_dex.py                         # inserts classes.dex into the APK
├── make_icon.py                       # generates the launcher icon (Pillow)
└── make_icons2.py                     # generates the vector icons
```

## Tvheadend API notes

Field notes gathered while building this, useful if you write your own client:

| Purpose | Endpoint |
| --- | --- |
| Server info / tags / channels | `/api/serverinfo`, `/api/channeltag/list`, `/api/channel/grid` |
| Save a channel | `POST /api/idnode/save` (`node={...}`) |
| Live stream | `/stream/channel/<uuid>?profile=<name>` |
| Status | `/api/status/subscriptions`, `/api/status/inputs`, `/api/status/connections` |
| Drop connections | `POST /api/connections/cancel` (`id=<connId>` or `id=all`) |
| EPG | `/api/epg/events/grid?channel=&start=&limit=&sort=start&dir=ASC` |
| Stream profiles | `/api/idnode/load?class=profile` (`/api/profile/list` misses custom profiles) |
| DVR lists | `grid_upcoming`, `grid_finished`, `grid_failed`, `grid_removed` |
| Autorec / Timerec | `/api/dvr/autorec/grid`, `/api/dvr/timerec/grid` |
| Create recording | `POST /api/dvr/entry/create_by_event`; custom: `create` with `conf=<JSON>` |
| Re-record | `POST /api/dvr/entry/rerecord/toggle` (`uuid=`) |
| Cancel / stop / delete | `POST /api/dvr/entry/cancel`, `/stop`, `/api/idnode/delete` |

Gotchas:
- `in`/`out` in subscriptions are **bytes/s** (÷125 → kbit/s); inputs `bps` is **bits/s** (÷1000).
- `/api/epg/events/grid` ignores `start_time`/`end_time`; use `start` (offset) + `limit`, or binary-search the offset.
- DVR create/cancel/stop take their parameters in the **form body**, not the query string.
- `title` in `create` must be an object: `{"chi":"..."}`.

## Privacy of screenshots
Server addresses are **not shown anywhere on screen** (header, channel list, status page, settings rows) — only the profile name. The URL only appears inside the edit dialog where you type it. This keeps screenshots safe to share.

## Known limitations
- HTTP only (no self-signed HTTPS trust)
- Playback needs VLC or another external player; there is no built-in player
- Channel editing is limited to name and number
- Tvheadend has no API to drop a single subscription — only whole connections

## License

MIT — see [LICENSE](LICENSE).

---

## 中文

**TvhGO 是一个轻量级 Tvheadend 安卓客户端**：频道、EPG、录制、服务器状态。纯 Android framework 实现，无 AndroidX / 无 ExoPlayer / 无 libVLC，整包约 100 KB。

### 功能
- **频道**：频道号或台标、按名称/号码搜索、分组筛选、副标题可显示分组或当前节目、按天 EPG（最多 7 天）、单频道可单独指定串流配置
- **播放**：VLC 或系统播放器；串流地址 `http://user:pass@host:9981/stream/channel/<uuid>?profile=<name>`
- **EPG**：时间轴网格，拖动浏览，点节目可播放/录制
- **录制**：对齐网页版六个分类（待录制/已完成/失败/已移除/自动录制/定时录制），支持下载到手机、重新录制、启停规则
- **状态**：订阅与输入流实时显示；点订阅可断开该用户的连接（等价网页版 状态→连接→Cancel）
- **设置**：多服务器配置、默认串流配置（从服务器读取）、深浅色主题（可跟随系统）、简体/繁体/英文/日文、界面语言切换
- **界面**：全部自绘——底部导航胶囊、通栏列表、自绘下拉与分段控件、自绘底部卡片对话框、矢量图标

### 构建
需要 JDK 21 + Android SDK（`platforms/android-34`、`build-tools/35.0.0`），不使用 Gradle：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

### 已知限制
- 仅支持 http 明文；依赖 VLC 等外部播放器；频道编辑仅限名称与频道号
- Tvheadend 没有「断开单个订阅」的 API，只能按连接断开

## 许可证
MIT
