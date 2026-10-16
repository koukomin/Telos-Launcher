# External APIs

Kvaesitso integrates with a number of external APIs. Most of them require some sort of authentication, like an API key. These API keys are not part of the GitHub repository. If you want to build Kvaesitso from source with all features enabled, follow the steps in this chapter.

> [!INFO]
> Kvaesitso is still buildable even without these steps, but some features will be disabled in the resulting APK. If all you need is a debug build for testing purposes, you can probably skip this chapter.

## Web3 resolution

Telos Network uses public ENS Ideas and eth.limo DNSLink endpoints for ASCII `.eth` lookups. Unstoppable Domains needs a user-owned HTTPS backend proxy holding its API key. No shared key is distributed in the APK. See [Tor and Web3](../../user-guide/telos/network/tor-web3.md).
