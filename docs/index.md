---
layout: home

hero:
  name: 'Telos'
  text: 'Search first. Own your phone.'
  tagline: A free and open source Android launcher built around fast search, with a family of privacy-minded Telos apps.
  image:
    src: /icon.png
    alt: Telos
  actions:
    - theme: brand
      text: Telos apps overview
      link: /docs/user-guide/telos/
    - theme: alt
      text: Launcher features
      link: /docs/user-guide/telos/launcher/
    - theme: alt
      text: User guide
      link: /docs/user-guide/

features:
  - icon: 🔎
    title: Search-first launcher
    details: Apps, contacts, files, calendar, calculator, unit converter, web and more in one fast, customizable search.
    link: /docs/user-guide/telos/launcher/
  - icon: 📞
    title: Telos Phone
    details: A clean dialer and call experience that fits the launcher.
    link: /docs/user-guide/telos/phone/
  - icon: 💬
    title: Messages
    details: Read and send messages from a simple, focused app.
    link: /docs/user-guide/telos/messages/
  - icon: 📁
    title: Files
    details: Browse local storage, cloud and network shares, and keep private files in a vault.
    link: /docs/user-guide/telos/files/
  - icon: 📸
    title: Screenshot
    details: Full, partial and scrolling screenshots, with crop, pixelate, blur and drawing.
    link: /docs/user-guide/telos/screenshot/
  - icon: 🎥
    title: Screen Recorder
    details: Record the screen to a video with the microphone, pause and a countdown.
    link: /docs/user-guide/telos/screen-recorder/
  - icon: 🎙️
    title: Voice Recorder
    details: Voice recordings with a list, search, pause and the call recordings of Telos Phone.
    link: /docs/user-guide/telos/voice-recorder/
  - icon: 🧮
    title: Calculator
    details: Standard and scientific calculator with VAT, unit and currency conversion and a history.
    link: /docs/user-guide/telos/calculator/
  - icon: 🖼️
    title: Photos
    details: A gallery with viewer, editor and document handling.
    link: /docs/user-guide/telos/photos/
  - icon: 🎵
    title: Music
    details: Play your own library with a lightweight music player.
    link: /docs/user-guide/telos/music/
  - icon: 🎬
    title: Video
    details: A straightforward video player for your local media.
    link: /docs/user-guide/telos/video/
  - icon: 📻
    title: Radio
    details: Tune in to internet radio stations.
    link: /docs/user-guide/telos/radio/
  - icon: 🛍️
    title: Store
    details: Discover and manage apps from the Telos Store.
    link: /docs/user-guide/telos/store/
  - icon: ❄️
    title: Smart Freeze
    details: Freeze rarely used apps to save battery and keep them out of the way.
    link: /docs/user-guide/telos/freeze/
  - icon: 🛡️
    title: Privacy and crash guard
    details: No tracking by default, and a crash guard that keeps the launcher usable if something goes wrong.
    link: /docs/user-guide/
---

<script setup>
  import { withBase } from 'vitepress'
  import Footer from '.vitepress/theme/Footer.vue'
  const shots = [1, 2, 3, 4, 5, 6].map((n) => withBase(`/img/screenshot-${n}.png`))
</script>
<div class="home-screenshots">
  <img v-for="src in shots" :key="src" :src="src" alt="Telos screenshot" />

  <div class="credits">Wallpaper by Allec Gomes on <a href="https://unsplash.com/de/fotos/ein-grunes-blatt-das-auf-einem-gewasser-schwimmt-UcWUMqIsld8" target="_blank">Unsplash.com</a></div>
</div>
<p class="telos-note">Telos is a fork of <a href="https://github.com/MM2-0/Kvaesitso">Kvaesitso</a> by MM2-0 and its contributors, released under the GPL-3.0.</p>
<Footer></Footer>
