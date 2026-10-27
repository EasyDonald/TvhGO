# Changelog

All notable changes to TvhGO are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.7.3]

### Fixed
- Switching servers while a channel group filter was active left the channel list empty: the group identifier belongs to the previous server, but it was kept after the switch, so no channel matched. The group filter is now cleared (and the picker reset to “All channels”) whenever the profile changes; the filter also falls back to “All channels” if the stored group no longer exists on the server.

## [1.7.2]

### Added
- Recording lists (upcoming, inished, ailed, 
emoved) and the autorec / timerec lists now show the creator of each entry (owner / creator from the Tvheadend API). The rule detail sheet and the recording editor show it as well.

## [1.7.1]

### Fixed
- Autorec / Timerec: the “Details” action of a rule did nothing. It now opens a detail sheet with type, name, title, channel, weekdays, start and stop time, directory, priority, comment, rule ID, and buttons to enable/disable or delete the rule.

## [1.7]

### Added
- **Recording search**: a search field above the DVR list filters the current category by recording title, channel name and status; autorec and timerec rules are matched against name, title, channel and weekdays. The header shows how many entries match (search N/M), the field has an inline clear button and the filter is re-applied when switching category.

## [1.6.2]

### Changed
- Channel detail: the day tabs now show the weekday next to the date (for example 10-24 (Fri)); the programme count is still shown in the status line below.

## [1.6.1]

### Fixed
- Channel icons: recycled rows kept the previous channel's icon; the image view is now cleared and the channel-number placeholder restored on every bind.
- Icons: values that cannot be fetched (`"1"`, `file://` paths) are skipped instead of being requested on every load.
- Icons: failed URLs are remembered for the session, so preloading completes instead of retrying broken entries indefinitely.
- Icons: the cache key is the absolute URL, so relative entries such as `imagecache/41` no longer collide between two servers.

### Added
- Settings → picon cache shows the number of cached icons, the size and the failure count; the app log records how many icons were downloaded, cached, failed and skipped.

## [1.6]

### Added
- **Persistent picon cache**: channel icons are stored in the app cache directory (file name derived from the icon URL), so switching servers, refreshing, and restarting no longer re-download icons. A new settings entry shows the cache size and clears it on demand.
- **Logs**: settings now offers *Tvheadend log* (live server log streamed over the Tvheadend comet channel, with debug level enabled while the page is open) and *App log* (recent client events: startup, server switches, channel loads, icon preloading, API errors). Both pages support copy-to-clipboard.

### Fixed
- Switching servers could leave the channel list empty or blocked: channel loading used a single-threaded executor, so a request against the previous server delayed the new one. Loading now runs on a small pool and stale responses are discarded through a generation counter.
- Icon preloading is generation-based as well, so a preload from the previous server cannot block or overwrite the new server's icons.

### Changed
- Picon images are decoded to a maximum side of 96 px before being cached on disk and in memory.

## [1.5.5]

### Changed
- Channel picons are preloaded for the whole list in the background instead of being fetched while scrolling. Loading runs with four concurrent requests, refreshes the visible rows every 25 icons, and completes within a few seconds on a local server. Images are downscaled to 96 px before caching and the cache holds the full channel list.

## [1.5.4]

### Added
- Settings / server profiles: profiles can now be deleted. The server switcher lists every profile with a delete action per row and asks for confirmation; at least one profile is always kept, and deleting the active profile falls back to the first remaining one. Deleting an entry before the active one keeps the active selection pointing at the same server.

### Fixed
- Server profile indices are re-based correctly after a deletion that sits before the current profile.

## [1.5.3]

### Changed
- Status: the input stream section is shown again with tuner information only — input name, stream name and subscriber count (plus signal level when reported). The bitrate is intentionally not displayed: /api/status/inputs returns an average since the input started, while the Tvheadend web interface receives live per-second values over its comet channel.
- DVR: entries are treated as “recording” based on sched_status / state, so a task that started early through pre-padding is pinned to the top of the upcoming list instead of being ordered by its nominal start time.

### Fixed
- EPG timeline: rows and programme blocks are clipped below the ruler, and the ruler is painted last, so blocks no longer cover the time labels while scrolling.

## [1.5.2]

### Changed
- Status: the input stream section has been removed. The bitrate reported by /api/status/inputs is an average since the input started and drifts away from the value shown by the Tvheadend web interface, which receives live updates over its comet channel. Rather than display numbers that cannot be kept consistent, the section is no longer shown; subscriptions and connections remain available.

### Fixed
- EPG timeline: the time ruler was drawn twice and the second background fill covered the tick labels. The duplicate draw has been removed.

## [1.5.1]

### Fixed
- EPG timeline: the time ruler was drawn twice; the second background fill covered the tick lines and labels. The duplicate draw has been removed and the ruler now renders correctly.
- Status / input streams: bitrate is taken directly from `bps` returned by `/api/status/inputs`. The previous per-input estimate (sum of subscriptions on that input) and the value-hold smoothing produced inconsistent readings and have been removed. A zero value is rendered as `—`.

### Notes
- `bps` is the multiplex bitrate. Subscription `in` / `out` are per-client byte rates and are not directly comparable to it.

## [1.5]

### Added
- EPG timeline: time ruler with distinct styles for full-hour and half-hour ticks.
- Status / subscription detail: **Play this channel** action, resolved by channel name.
- About: opens the project page at https://github.com/EasyDonald/TvhGO.

### Changed
- EPG: channel names in the left column wrap onto two lines instead of being truncated.
- Status / subscriptions: DVR recordings carry no client user-agent; the subscription title (`DVR: <name>`) is shown instead.
- Open Tvheadend web UI: the URL now embeds HTTP Basic credentials so the browser authenticates directly.
- Removed the redundant “Now” button from the EPG toolbar.
- HTTP 403 is reported as a permission error; administrator rights are no longer assumed, since DVR access can be granted independently.

## [1.4.2]

### Fixed
- Autorec / Timerec rows: tapping a rule terminated the app. The list click handler always read the recording-entry array, while these two categories use the rule array, causing an out-of-bounds access. Events are now dispatched by the active category.

### Changed
- Sorting: upcoming entries are ordered by start time with running recordings pinned first; finished, failed and removed entries are ordered newest first; autorec and timerec rules are ordered by name.

## [1.4.1]

### Fixed
- DVR configuration picker: DVR profiles (Digital Video Recorder Profile) and stream profiles are distinct concepts. The picker now shows only the DVR configuration name.
- Default stream profile: restored to `pass` and no longer derived from the recording configuration.
- About: the version label is read from the APK `versionName` instead of a fixed string.

## [1.4]

### Added
- Recording tasks in the upcoming and recording states can be edited: title, start time, stop time, pre/post padding and enabled state (`POST /api/idnode/save`).

### Changed
- DVR configuration names are read from the server.
- Removed the play counter from recording rows.
- Errors on a recording no longer highlight the row subtitle in a warning colour.

## [1.3.4]

### Fixed
- DVR lists are now paged (400 entries per request, following `total`), removing the 200-entry limit on finished recordings.
- Autorec and Timerec lists use the same paging.

### Added
- Multiple external players: add players with a display name and package name, edit, delete and switch between them. VLC is preinstalled and the system chooser remains available.

## [1.3.3]

### Changed
- The server address is no longer displayed in the interface. The header, channel status line, status page, settings rows and profile switcher show the configuration name only.
- The address is available in the server edit dialog, where it is configured.

## [1.3.2]

First public release.

### Added
- Channel list with channel number or picon, search by name or number, group filter, subtitle showing either the group or the current programme.
- Channel detail with per-day EPG (today, tomorrow, `MM-dd`, up to seven days), per-channel recording and per-channel stream profile.
- Playback through an external player (VLC or the system chooser) and stream URL sharing.
- EPG timeline: drag to browse, tap a programme to play or record.
- DVR: upcoming, finished, failed, removed, autorec and timerec categories, with cancel, stop, delete, download to device, re-record and rule enable/disable.
- Status: server information, active subscriptions, input streams and connections, including the ability to drop a user's connections.
- Settings: multiple server profiles, default stream profile, dark/light theme and four interface languages (Simplified Chinese, Traditional Chinese, English, Japanese).
