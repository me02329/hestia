# Hestia (fork of Free My TV, hardened for a TCL G08)

This branch supersedes the upstream installation and debloating instructions.
Read README.md and SAFE-DEPLOYMENT.md before deployment.

- The user authorized local hardening and build/testing. TV deployment is a separate next step.
- Do not reintroduce in-app ADB, package disabling, boot-time enforcement or WRITE_SECURE_SETTINGS.
- In-app code never disables packages. On the owner's explicit decision (2026-10-21) the TV runs with
  `launcherx` and `setupwraith` disabled by hand over ADB (see README/TRIAL.md); don't disable anything else.
- Never claim automatic restoration when ADB is unavailable; the app offers recovery navigation only.
- Use tools/tv_safe.py over the user's already paired ADB transport. Require an explicit serial.
- Snapshot before switching; preserve the immutable snapshot and operation journal. Never infer an original HOME
  or bypass device/firmware/user identity checks. No legacy `cmd role get-role-holders` dependency.
- Do not execute any TV mutation while reviewing/building this project.
- Do not commit config.properties, signing keys/passwords, device backups or configured APKs.
- Build with JDK 17, platform android-34 and build-tools 34.0.0. Minimum Android for this branch is 13.
- Validate with `python3 -m unittest discover -s tests -v`, `./build.sh` and `git diff --check`.
- Network requests use Net.java: fixed HTTPS origins, no redirects, bounded responses and safe error messages.
  Current sources: Open-Meteo, Bing photo archive, cyber RSS feeds (News.java). No API keys.
- UI in French, English or Portuguese (owner's request, 2026-10-22): English/Portuguese at each Ui.tr() call,
  French in Ui's FR table keyed by the English text. Drawn on Canvas. Preserve D-pad navigation and visual previews.
- Report the difference between simulated tests, successful builds and actual TV validation.
- The upstream marketing website (docs/) was removed from this fork; don't reintroduce or publish it.
