# Telos System (wiki)

Virtual app: dashboard (CPU, RAM, uptime), Apps / AppOps, Tweaks, Privacy Shield, **Shizuku**.

See [Built-in Shizuku](./shizuku) for pairing and adb.

| Tab | Role |
| --- | --- |
| Dashboard | Device stats, graphics API, text size for Telos System |
| Apps | Packages, AppOps Next (daemon, UserService, or privileged shell). ColorOS / Android 16+ dumpsys uses `AppOps Service state:` and a separate Uid Op State section; Telos reads that dump without the restriction history prefix. |
| Tweaks | System tweaks |
| Privacy Shield | Shizuku clients, freeze external Shizuku, banking cloak (DevOpts snapshot restore), clipboard. Saved Wi-Fi is under Telos Network → Settings |
| Shizuku | Builtin server, wireless-debugging settings, pairing notification, paired-key autostart, optional wireless-debugging shutdown, private startup scripts with explicit identity and opt-in, copyable adb command, labs |

[User guide](/docs/user-guide/telos/system/).

See [startup scripts and reconnection](../developer-guide/startup-scripts) for limits, setup and Android prerequisites.
