# Changelog

## 1.4.1
- DVR profile picker shows the **DVR profile name only** (DVR profiles are not stream profiles — the stream profile annotation was wrong and has been removed)
- Reverted the “default stream profile follows the DVR profile” behaviour; the default stream profile is pass again unless you pick one
- “About” now reads the real version from the APK instead of a hardcoded string

## 1.4
- Upcoming / recording entries can be **edited**: title, start, stop, pre/post minutes, enabled (/api/idnode/save)
- DVR row subtitle is no longer highlighted red when a file has errors
- Removed the play-count from DVR rows
- **DVR config presets fixed**: config names are resolved from the server and annotated with the stream profile they use (e.g. “默认配置（pass-norewrite）”); on first run the default stream profile follows the default DVR config’s profile instead of a hardcoded pass

## 1.3.4
- **DVR lists are paged**: upcoming / finished / failed / removed / autorec / timerec now request 400 per page and keep going until 	otal, fixing the 200-item cap reported for Finished
- **Multiple external players**: add your own players with a display name + package name, edit, delete, and switch between them (VLC is preloaded, “System chooser” always available)

## 1.3.3
- Server address is no longer displayed anywhere in the UI (header, channel page status line, status page, settings rows, profile switcher) — only the profile name is shown, so screenshots don't leak the domain
- The URL is still editable in the server edit dialog and still used by “Open Tvheadend web UI”

## 1.3.2
- Themed text fields: text/hint colors now follow the theme (white-on-white in light mode fixed), rounded background, no underline

## 1.3.1
- All icons are `VectorDrawable` (refresh / tune / link / play / back / clear / arrow-down); fixes the off-center refresh glyph

## 1.3
- DVR page mirrors the web UI: **Upcoming / Finished / Failed / Removed / Autorec / Timerec**
- Failed & Removed can be **re-recorded** (`api/dvr/entry/rerecord/toggle`)
- Autorec / Timerec rules: enable, disable, delete, details

## 1.2.3
- Dialogs are fully hand-drawn (`Sheet.java`): bottom card, drag handle, themed buttons and list rows — no `AlertDialog`

## 1.2.2
- Lists are full-bleed with 1px separators
- Native `Spinner` replaced by a custom dropdown button + themed list; DVR view switch became a segmented control

## 1.2.1
- UI rebuilt: pill indicator on the bottom bar, grouped settings cards, new channel/DVR rows, new header

## 1.2
- Drop connections from the status page (`api/connections/cancel`), same call as the web UI

## 1.1.1
- Light theme fixes: themed dropdowns, card strokes, EPG block text

## 1.1
- Dark / light / follow-system theme
- Download finished recordings to the phone
- Show total recording size
- Subscription details dialog

## 1.0
- Renamed to **TvhGO**, new package `com.easydonald.tvhgo`, new icon, card UI

## 0.x (internal iterations)
- 0.9 (1.2.3) …
- Multi-server profiles, per-channel stream profile, 7-day EPG, four UI languages, DVR management, EPG timeline, status polling
