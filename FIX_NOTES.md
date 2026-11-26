# Fix: Banglar Shiksha login "Server error" in WebView (works in Chrome)

Changes in MainActivity.kt:
1. Removed the `X-Requested-With: com.bspsms.android` header (WebSettingsCompat.setRequestedWithHeaderOriginAllowList).
2. User-Agent is now Chrome-style (no `wv`, no `Version/4.0`), built from the device's real WebView Chrome version.
3. NO_CACHE_HEADERS (Cache-Control/Pragma/Expires) are no longer sent on page loads.
Applied to all 3 portal WebViews (main, popup, background marksheet).

## v2 (captcha/session mismatch)
Screenshot showed correct captcha (4+9=13) rejected with "Incorrect captcha", then "Server error".
-> likely session cookie mismatch. Added test flags in MainActivity.kt companion:
- PORTAL_USE_PROFILE_API = false  (default WebView cookie store instead of per-user Profile)
- PORTAL_CHROME_STYLE_UA = false  (keep WebView's own UA; fake Chrome UA without sec-ch-ua hints can look suspicious)
X-Requested-With removal and no custom no-cache headers are kept.

## v3 (diagnostic)
Screenshots: captcha correct and rotating, but login shows "Server error" -> the login POST itself fails.
- Added read-only XHR/fetch observer (MainActivity.onPageStarted): when a POST fails, the banner shows
  "Portal Request Failed (diagnostic)" with URL, HTTP status and the first 300 chars of the response.
- Enabled WebView.setWebContentsDebuggingEnabled(true) so chrome://inspect works over USB.

## v4 (Result Ready tool)
Problem: wrong marks, and one extra subject column added per scanned student.
Cause: any non-marks row (remarks/name/etc.) in the Summative table could become a "subject" with a student-specific key;
the exporter then unioned subjects by key, adding a column per student. Body rowspans also shifted mark columns.
Fixes:
- PortalHrpcDomScripts.extractSummativeMarksJs: rowspan-aware body grid; rows with no marks are never subjects; stricter "art" match.
- MarksheetAutomationController: drop subjects without any marks; recompute full marks.
- ResultXlsxExporter.resolveSubjectSchema: only subjects that have marks for at least one student.

## v5 (Result Ready - rewritten from the real marksheet screenshots)  [NOT zipped yet]
Root cause: old parser counted numbers left-to-right and dropped "-" cells, so every mark after an empty
III column moved one column left (Total landed under III). Art/Health use a 2-row layout (SE(10) labels row +
marks row, subject cell rowSpan=2) and were lost.
- PortalHrpcDomScripts.extractSummativeMarksJs rewritten: finds the table titled "Summative Evaluation"
  (never the Formative one), builds a rowspan/colspan grid, maps columns by header (I / II / III / Total / Percentage),
  reads marks by column position (never compacts), uses "SE(10)" / "(50)" label rows only as full marks.
- Reads Class and Section from the page -> StudentSummativeResultRow.className / section.
- ResultXlsxExporter: one sheet per class ("Class I", "Class IV"...), each with its own subject columns;
  positions ranked inside each class.
Tested in Chromium against HTML rebuilt from the screenshots (Class I and Class IV, AB marks, Bengali digits,
2-row header). Kotlin itself could not be compiled in this environment.

## v6 (modern tools + logos + floating windows)  [NOT zipped yet]
- ToolVisuals.kt (new): per-tool gradient palette + GradientLogo/ToolLogo (used in drawer and window title bars).
- FloatingToolWindow.kt (new): draggable title bar, resize grip, minimize-to-bubble, maximize/restore, close, back-to-hub.
  Each tool (and the hub) has its own FloatingWindowState (position/size/mode). Content stays composed when minimized.
- MainActivity.kt: Hub and every Tool now open in a floating window above the portal (Admin panel stays full screen);
  top bar stays "SMS Portal"; Back button acts on the portal while a window is minimized; native tools'
  "Open Portal" minimizes their window; BSP_OPEN_TOOL:<key> lets the hub open any tool in its own window.
- assets/extension: hub.html/css/js redesigned (hero, search, gradient SVG tool logos, locked state, native mode);
  bsp-modern.css shared skin linked from formative/talk/lpcd/excel/bco popups (visual only).
Not compiled here (no Kotlin toolchain): build and send any compiler error.

## v7 (FINAL)
- Automation HUD (Pause/Stop/Dashboard) and portal error banners now also show while a tool window is minimized or
  another window is open; "Open dashboard" restores the tool window. Background WebViews (Layer 0) are untouched,
  so Bulk Marksheet and Result Ready run exactly as before.
- Admin Panel: gradient header with icon stat tiles, rounded search + filter chips, user card with avatar, status pill,
  tinted details box and a collapsible "Manage user" actions section. Logic and buttons are unchanged.
- Everything from v1-v6 is included (login WebView fix, Result Ready parser rewrite + class-wise Excel,
  modern tool hub / logos / floating windows).
Not compiled in the authoring environment (no Kotlin toolchain): if Android Studio reports an error, send it.
