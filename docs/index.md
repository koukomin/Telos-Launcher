---
layout: home

hero:
  name: "Telos Launcher"
  text: "Built-in apps, privacy and search"
  tagline: Search-focused launcher with built-in phone, messages, files, media, store and freezer
  actions:
    - theme: brand
      text: Get Started
      link: /docs/user-guide/
    - theme: alt
      text: GitHub Releases
      link: https://github.com/koukomin/Telos-Launcher/releases
    - theme: alt
      text: Add to Obtainium
      link: https://obtainium.imranr.dev/add?url=https://github.com/koukomin/Telos-Launcher

features:
  - icon: 🔍
    title: Search-focused
    text: Find apps, contacts, files, calendar events, calculations, unit conversions and web results from one place.

  - icon: 📞
    title: Phone & Messages
    text: Built-in phone with dialer, call recording, screening and SIP; built-in SMS and MMS messenger.

  - icon: 📁
    title: Files & Media
    text: Built-in file manager, photo viewer, music, video and radio players, document viewer and PDF tools.

  - icon: ❄️
    title: Smart Freeze
    text: Freeze apps through Shizuku, root or Island, and launch them instantly when you need them.

  - icon: 🛍️
    title: Store & System
    text: App installer and updater (GitHub, F-Droid), CPU/RAM telemetry, built-in Shizuku server and AppOps.

  - icon: 🎨
    title: Customizable
    text: Presets, themes, custom icon packs, fonts, shapes, widgets and a flexible home screen grid.

  - icon: 🎬
    title: Media
    text: Listen, Watch and Live in one app — local files, YouTube, TV, Stremio Fork (deep links, calendar, continue watching), NextLib decoders.
  - icon: ✉️
    title: Ermis
    text: IMAP/SMTP mail with TLS, conversations, drafts and optional Google/microG consent.
    link: /docs/user-guide/telos/ermis/
  - icon: 🛡️
    title: Privacy and crash guard
    text: No tracking by default, and a crash guard that keeps the launcher usable if something goes wrong.
    link: /docs/user-guide/

---

<script setup>
  import { withBase } from 'vitepress'
  import Footer from '.vitepress/theme/Footer.vue'
  const shots = [
    { src: '/images/telos-shots/01-home-widgets.png', caption: 'Home screen & widgets' },
    { src: '/images/telos-shots/02-search-omnibar.png', caption: 'Search & quick actions' },
    { src: '/images/telos-shots/03-phone-app.png', caption: 'Built-in Phone' },
    { src: '/images/telos-shots/04-messages-app.png', caption: 'Built-in Messages' },
    { src: '/images/telos-shots/05-files-cloud.png', caption: 'Telos Files' },
    { src: '/images/telos-shots/06-network-hub.png', caption: 'Telos Network' },
    { src: '/images/telos-shots/07-store-installer.png', caption: 'Telos Store' },
    { src: '/images/telos-shots/08-freeze-manager.png', caption: 'Smart Freeze' },
    { src: '/images/telos-shots/09-system-appops.png', caption: 'Telos System' },
    { src: '/images/telos-shots/10-cyberpunk-preset.png', caption: 'Cyberpunk preset' },
  ]
</script>

<div class="telos-shot-grid">
  <figure v-for="shot in shots" :key="shot.src">
    <a :href="withBase(shot.src)"><img :src="withBase(shot.src)" :alt="shot.caption" loading="lazy" width="1080" height="2400" /></a>
    <figcaption>{{ shot.caption }}</figcaption>
  </figure>
</div>
<div class="telos-story">

## Latest improvements

- **Media:** one mini player per Music/Radio page, home-screen metadata and controls for built-in
  playback, and separate Music, Radio, TV and YouTube notifications. Video, Music, Radio, TV and
  YouTube share Media3 plus official NextLib FFmpeg decoders; playback stays in Telos. Viewer
  (photos and documents) stays built in.
- **Video, TV and Stremio Fork:** brightness-left and volume-right sliders with icons; a top-left
  stop-and-back button. TV keeps a separate minimize action.
- **Freeze Manager:** consistent snowflake/grayscale and icon packs, verified Disable/Suspend
  transitions, and actual-state scans with Unfreeze all / Freeze again for saved user/system groups.
- **Store:** opt-in frozen-app updates with durable restoration and streaming Shizuku/root installs.
  Published source artwork is shown for uninstalled apps, and full installer failures are readable.
  Plain Device Admin is insufficient; Device Owner supports only states it can restore.

Details: [Media](/docs/user-guide/telos/media/), [Store](/docs/user-guide/telos/store/),
[Freeze](/docs/user-guide/telos/freeze/backends-profiles).

## The story behind Telos

Telos started as a personal project, and I want to be upfront about how it was made: **I built it with zero coding knowledge.** All of the development was done with AI assistants — [ChatGPT](https://chatgpt.com), [Claude](https://claude.ai), Grok and Gemini. I couldn't have written this software by hand. What I could do was describe what I wanted, test each build on my own phone, and keep iterating with the AI until it worked. In that sense Telos is as much an experiment in AI-assisted development as it is a launcher.

It all comes down to one missing feature. I was already a happy user of [Kvaesitso](https://github.com/MM2-0/Kvaesitso), which I think is an excellent launcher. But a few things I really wanted weren't there — above all **freezing apps and launching frozen apps straight from the launcher.** For me that is the single most important feature, for two reasons:

1. **Battery life and RAM.** Apps that run in the background — social media, shopping, games — eat up battery and memory even when you're not using them. Freezing them stops them completely until you need them.
2. **App overload.** Having dozens of icons on your home screen or in your app drawer makes your phone feel cluttered. When apps are frozen, they're out of the way until you search for them.

When I searched for a solution, I found a few third-party freezer tools, but none of them integrated nicely with a launcher. You'd have to open the freezer app, unfreeze, launch, and then remember to freeze again later. I wanted something seamless: search for an app, tap it, it unfreezes and opens; when you're done, it freezes itself back.

So I decided to try building it myself — or rather, with AI. I took Kvaesitso's source code as a base, and step by step, prompt by prompt, added the freeze integration. Once that worked, I kept going: I added a built-in file manager, media players, a phone app, messages, an app store, a PDF tools suite, a network manager, and more. What started as "Kvaesitso with a freezer" grew into Telos: a complete, self-contained launcher environment.

Everything is open source under the GPLv3 license. If you're curious, the source code is on [GitHub](https://github.com/koukomin/Telos-Launcher).

</div>

<Footer />

<style>
.telos-shot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
  margin: 32px 0;
}
.telos-shot-grid figure {
  margin: 0;
  text-align: center;
}
.telos-shot-grid img {
  border-radius: 12px;
  border: 1px solid var(--vp-c-divider);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  transition: transform .2s, box-shadow .2s;
}
.telos-shot-grid img:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0,0,0,0.25);
}
.telos-shot-grid figcaption {
  margin-top: 8px;
  font-size: 0.85em;
  color: var(--vp-c-text-2);
}
.telos-story {
  margin: 40px 0;
  line-height: 1.7;
}
.telos-story h2 {
  border-top: 1px solid var(--vp-c-divider);
  padding-top: 24px;
}
.telos-story ol {
  padding-left: 20px;
}
.telos-story li {
  margin-bottom: 8px;
}
</style>
