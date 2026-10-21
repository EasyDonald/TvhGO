# Changelog

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
