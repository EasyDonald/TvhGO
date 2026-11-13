# Changelog

All notable changes to TvhGO are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.94]

### Fixed
- With *Stream authentication* set to **Auth token**, the copied stream link (long-press a channel → copy) still contained the username and password, because the token flag was only applied on the playback paths. The authentication mode is now part of the API client, so playback, link copying and recording downloads all follow the selected mode.
- Copying a link in token mode fetches the token first when it has not been loaded yet, instead of silently producing a credential URL.
- Recording downloads use `?auth=<token>` in token mode and no longer send an `Authorization` header.

### Changed
- Setting renamed from *Streaming mode* to **Stream authentication** (username/password or auth token).

## [1.93]

### Changed
- Streaming authentication simplified to two modes: **username/password** (default, credentials embedded in the URL) and **auth token** (`?auth=<token>` taken from `/playlist/auth/channels`).
- The token belongs to the account, so it is stored once, shown in Settings → Playback → *Auth token*, fetched on demand and refreshed only when requested.
- Stream URLs become `.../stream/channel/<uuid>?profile=<profile>&auth=<token>`.

### Removed
- The local stream relay, its foreground service and the related permissions.

## [1.92]

### Added
- **Auth tokens from `/playlist/auth/channels`**: the authenticated playlist is read once and the per-channel URLs carrying an `?auth=<token>` parameter are cached. Links handed to the player contain no credentials and work with players that cannot perform Digest authentication.

### Changed
- Streaming mode *Auto* prefers the token URL and falls back to the local relay only when no token is available for that channel.

## [1.91]

### Added
- **Streaming mode setting** (Settings → Playback): *Auto*, *Direct* (credentials embedded in the URL) and *Local relay*.
- Relay improvements: reuses the API authentication object, answers `HEAD` probes, follows a single upstream redirect and records start/failure details in the app log.

## [1.9]

### Added
- **Local stream relay** for servers behind a Digest-only reverse proxy: the app opens a loopback port, performs the upstream authentication itself and forwards the transport stream, so external players receive a `http://127.0.0.1:<port>/stream/...` URL. A foreground service keeps the relay alive during playback.

### Fixed
- Channel detail: an HTTP 403 from the DVR API (account without recording permission) aborted the whole screen, so the per-channel EPG was never shown. The booking lookup is now best-effort and the guide renders regardless.

## [1.8]

### Added
- **HTTP Digest authentication** in addition to Basic. When a server answers 401 with a Digest challenge, the app computes the RFC 2617 response and retries, remembering the scheme for subsequent requests. Applied to API calls, picon downloads and the log stream.

## [1.7.3]

### Fixed
- Switching servers while a channel group filter was active left the channel list empty, because the group identifier belongs to the previous server. The filter is now cleared on profile change, and falls back to “All channels” when the stored group no longer exists.

## [1.7.2]

### Added
- Recording lists (upcoming, finished, failed, removed) and the autorec / timerec lists show the creator of each entry (`owner` / `creator` from the API). The rule detail sheet and the recording editor show it as well.

## [1.7.1]

### Fixed
- Autorec / Timerec: the “Details” action of a rule had no handler. It now opens a detail sheet with type, name, title, channel, weekdays, start and stop time, directory, priority, comment and rule ID, plus actions to enable/disable or delete the rule.

## [1.7]

### Added
- **Recording search**: a search field above the DVR list filters the current category by recording title, channel name and status; autorec and timerec rules are matched against name, title, channel and weekdays. The header shows how many entries match, and the filter is re-applied when switching category.

## [1.6.2]

### Changed
- Channel detail: the day tabs show the weekday next to the date (`10-24 (Fri)`); the programme count is shown in the status line.

## [1.6.1]

### Fixed
- Channel icons: recycled rows kept the previous channel's image; the image view is cleared and the channel-number placeholder restored on every bind.
- Icons with values that cannot be fetched (`1`, `file://`) are skipped instead of being requested repeatedly.
- Failed icon URLs are remembered for the session, so preloading completes instead of retrying broken entries.
- The icon cache key is the absolute URL, so relative entries such as `imagecache/41` no longer collide between two servers.

### Added
- Settings → picon cache shows the number of cached icons, their size and the failure count; the app log records download results.

## [1.6]

### Added
- **Persistent picon cache**: channel icons are stored in the app cache directory, so switching servers, refreshing and restarting no longer re-download them. A settings entry shows the cache size and clears it on demand.
- **Logs**: a live Tvheadend log (streamed over the Tvheadend comet channel while the page is open) and an app log covering startup, server switches, channel loads, icon preloading and API errors. Both support copy-to-clipboard.

### Fixed
- Switching servers could leave the channel list empty or blocked, because channel loading used a single-threaded executor. Loading now runs on a small pool and stale responses are discarded through a generation counter.
- Icon preloading is generation-based as well, so a preload from the previous server cannot block the new one.

## [1.5.5]

### Changed
- Channel picons are preloaded for the whole list in the background instead of being fetched while scrolling.

## [1.5.4]

### Added
- Server profiles can be deleted from the switcher, with confirmation; at least one profile is always kept.

## [1.5.3]

### Changed
- Status: the input stream section is shown again with tuner information only (input name, stream name, subscriber count, signal when reported); the bitrate is intentionally omitted because the HTTP endpoint returns a cumulative average rather than a live rate.
- DVR: entries are treated as “recording” based on `sched_status` / `state`, so a task that started early through pre-padding is pinned to the top of the upcoming list.

### Fixed
- EPG timeline: rows and programme blocks are clipped below the ruler and the ruler is drawn last, so blocks no longer cover the time labels.

## [1.5.2]

### Changed
- Status: the input stream section was removed, as `/api/status/inputs` reports a cumulative average bitrate that cannot be kept consistent with the live values shown by the Tvheadend web interface.

### Fixed
- EPG timeline: the time ruler was drawn twice, the second background fill covering the tick labels.

## [1.5.1]

### Fixed
- EPG timeline: the ruler background was painted over the tick lines and labels; the duplicate draw has been removed.
- Status / input streams: bitrate is taken directly from the value returned by the API.

## [1.5]

### Added
- EPG timeline: time ruler with distinct styles for full-hour and half-hour ticks.
- Status / subscription detail: play the channel a subscription is watching; drop that user's connections.
- About: opens the project page.

### Changed
- EPG: long channel names wrap onto two lines.
- Status / subscriptions: DVR recordings have no client user-agent, so the subscription title (`DVR: <name>`) is displayed.
- Open Tvheadend web UI: the URL embeds HTTP Basic credentials.
- HTTP 403 is reported as a permission error; administrator rights are no longer assumed.

## [1.4.2]

### Fixed
- Autorec / Timerec rows: tapping a rule terminated the app, because the list click handler always read the recording-entry array. Events are now dispatched by the active category.

### Changed
- Sorting: upcoming entries are ordered by start time with running recordings pinned first; finished, failed and removed entries newest first; rules by name.

## [1.4.1]

### Fixed
- DVR configuration picker: DVR profiles and stream profiles are distinct concepts, so the picker now shows only the DVR configuration name.
- Default stream profile restored to `pass` and no longer derived from the recording configuration.
- About: the version label is read from the APK `versionName`.

## [1.4]

### Added
- Recording tasks in the upcoming and recording states can be edited: title, start and stop time, pre/post padding and enabled state.

### Changed
- DVR configuration names are read from the server.
- The play counter was removed from recording rows, and errors no longer highlight the row subtitle.

## [1.3.4]

### Fixed
- DVR lists are paged (400 entries per request, following the reported total), removing the 200-entry limit.

### Added
- Multiple external players: add players with a display name and package name, edit, delete and switch between them.

## [1.3.3]

### Changed
- The server address is no longer displayed in the interface; the header, status line, status page and profile switcher show the configuration name only.

## [1.3.2]

First public release.

### Added
- Channel list with channel number or picon, search, group filter and a subtitle that shows either the group or the current programme.
- Channel detail with per-day EPG (up to seven days), per-channel recording and per-channel stream profile.
- Playback through an external player and stream URL sharing.
- EPG timeline with drag scrolling.
- DVR with upcoming, finished, failed, removed, autorec and timerec categories.
- Status view with subscriptions, input streams and connections.
- Multiple server profiles, dark/light theme and four interface languages.
