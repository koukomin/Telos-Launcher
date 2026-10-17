# Terminal

The built-in Terminal entry is a companion for the separately installed official [Termux](https://github.com/termux/termux-app) runtime. Use its screen to open Termux, install it from an official source, and find its plugin ecosystem. Telos does not run downloaded shell commands or bundle a renamed shell runtime.

Termux’s app is GPL-3.0-only, compatible with GPLv3 distribution, with separate licenses for some libraries. The license permits a fork, but changing the Android package also requires rebuilding bootstrap/packages for a new installation prefix. Official plugin APKs share Termux’s package identity and signing requirements; install Termux and its plugins from the same source. A renamed embedded runtime would not preserve that binary compatibility. See the upstream [license](https://github.com/termux/termux-app/blob/master/LICENSE.md) and [forking guide](https://github.com/termux/termux-app#forking).
