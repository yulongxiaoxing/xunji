# Computer-only rootless Hook experiment

This diagnostic is separate from the Xunji app. It never installs into the USB-connected phone. `lab.py` restricts operations to `emulator-5588` and checks `ro.kernel.qemu=1` before executing an operation.

Artifacts, downloaded official Android16 image, isolated AVD, lab-only signing key and logs live in `.codex/hook-lab/`. The user's existing AVD is not reused. Host downloads are verified against official image/release digests before execution. No Google/QQ/DeepSeek credentials are needed for the first phase.

`build.ps1` builds a target APK whose `marker()` returns `ORIGINAL`, and an Xposed module whose hook changes the result to `HOOK_OK`. The baseline and patched APK must each run in the emulator; a successful patch command alone is not success. Xposed API classes are compile-only.

The same module contains a separate QQ **controlled method probe**. It checks interface presence, hooks TextElement.getContent, creates its own object containing a fixed marker, confirms that the hook ran without changing the return value, then removes the hook. It does not inspect existing chat objects, subscribe to messages, transmit messages or prove that an account can log in. This makes actual method hooking distinguishable from class presence.

`verify_qq_start.py` deliberately uninstalls the isolated emulator's QQ between original and patched variants. Run this clean comparison before signing in a test account. It is not an update or account-preservation script. All its ADB operations pass through the emulator guard.

The experiment plan is [2026-10-21-rootless-hook-lab.md](../../docs/superpowers/plans/2026-10-21-rootless-hook-lab.md). Phone QQ replacement/Root requires separate user confirmation.
