# Terminal

The current Terminal screen contains an experimental on-demand Termux bootstrap downloader and a command runner. It does not provide a persistent PTY session or establish full compatibility with Termux packages. The bootstrap binaries are built for the Termux app prefix; Android execution restrictions and the different Telos package/data path still require runtime integration and device validation. Do not treat a downloaded archive as evidence of a working shell.

The existing downloader is now capped at 64 MiB. Archive extraction stays inside the runtime directory, including when an existing symlink points outside it, and stops after 10,000 entries, 64 MiB per file or 256 MiB expanded total. Cancellation propagates, and the installer checks for an executable bash file before reporting its archive setup complete. These guards do not prove the binary can execute on the device. Failed extraction may leave partial files; do not rely on the experimental installer for an established shell environment.

For a supported environment, use the separately installed official [Termux](https://github.com/termux/termux-app), including its own runtime and plugins. Telos does not yet host that complete runtime.

Termux’s app is GPL-3.0-only; its runtime packages carry their own licenses. Renaming the Android package requires rebuilding bootstrap/packages for the new installation prefix, and official plugin APKs retain Termux package/signing requirements. See the upstream [license](https://github.com/termux/termux-app/blob/master/LICENSE.md) and [forking guide](https://github.com/termux/termux-app#forking).
