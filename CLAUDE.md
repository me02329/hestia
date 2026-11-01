# Hestia (fork of Free My TV, hardened for a TCL G08)

This branch supersedes the upstream installation and debloating instructions.
Read README.md and SAFE-DEPLOYMENT.md before deployment.

- The user authorized local hardening and build/testing. TV deployment is a separate next step.
- Purpose: replace Google TV because it is slow, laggy, full of background services and ads. Every addition must stay
  light: nothing in the background without a reason, nothing fetched unless a screen is visible and asks.
- Before writing a feature, read docs/ARCHITECTURE.md (layers) and docs/BRIQUES.md (call the brick, don't recode its rule).
- Manifest components stay in the root package io.hestia.tv: the TV's settings and tools/tv_safe.py name those classes.
- Do not reintroduce in-app ADB, package disabling or boot-time enforcement. WRITE_SECURE_SETTINGS is allowed only for
  the three delays in power/SleepDelay, granted once over ADB (docs/adr/001, 002); no other system setting without the
  owner asking and a new ADR.
- In-app code never disables packages. On the owner's explicit decision (2026-10-21) the TV runs with
  `launcherx` and `setupwraith` disabled; only tools/tv_safe.py (disable/enable-stock-home, STOCK list) may
  change them. On the owner's decision (2026-11-01, docs/adr/003) voice search, Google Calendar and AirPlay are off too
  (disable/enable-services, SERVICES list). Never disable anything else (TCL services, Chromecast…) without the owner asking.
- Never claim automatic restoration when ADB is unavailable; the app offers recovery navigation only.
- Use tools/tv_safe.py over the user's already paired ADB transport. Require an explicit serial.
- Snapshot before switching; preserve the immutable snapshot and operation journal. Never infer an original HOME
  or bypass device/firmware/user identity checks. No legacy `cmd role get-role-holders` dependency.
- Do not execute any TV mutation while reviewing/building this project. Installing an update kills Hestia for a
  moment: whatever is under it (e.g. TCL's TV app) comes up until Home is pressed.
- Do not commit config.properties, signing keys/passwords, device backups or configured APKs.
- Build with JDK 17, platform android-34 and build-tools 34.0.0. Minimum Android for this branch is 13.
- Validate with `python3 -m unittest discover -s tests -v`, `./build.sh` and `git diff --check`.
- Network requests use data/Net.java: fixed HTTPS origins, no redirects, bounded responses and safe error messages.
  Current sources: Open-Meteo, Bing photo archive, cyber RSS feeds (data/News.java). No API keys.
- UI in French, English or Portuguese (owner's request, 2026-10-22): English/Portuguese at each I18n.tr() call,
  French in I18n's FR table keyed by the English text (tested). Drawn on Canvas. Preserve D-pad navigation and visual previews.
- Report the difference between simulated tests, successful builds and actual TV validation.
- The upstream marketing website was removed from this fork; don't reintroduce or publish it. docs/ now holds the
  project's own documentation.
