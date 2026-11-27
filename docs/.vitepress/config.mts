import { defineConfig } from 'vitepress'
import { UserGuideSidebar } from '../docs/user-guide/sidebar.ts'
import { DeveloperGuideSidebar } from '../docs/developer-guide/sidebar.ts'
import { ContributorGuideSidebar } from '../docs/contributor-guide/sidebar.ts'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  // published at https://koukomin.github.io/Telos-Launcher/ (GitHub Pages project site)
  base: '/Telos-Launcher/',
  title: 'Telos Launcher',
  description:
    'Telos is a search-focused, free and open source Android launcher with a suite of privacy-minded apps. A fork of Kvaesitso.',
  lastUpdated: true,
  cleanUrls: true,
  sitemap: {
    hostname: 'https://koukomin.github.io/Telos-Launcher/',
  },
  themeConfig: {
    logo: '/icon.png',
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'User Guide', link: '/docs/user-guide/' },
      { text: 'Telos Apps', link: '/docs/user-guide/telos/' },
      { text: 'Launcher', link: '/docs/user-guide/telos/launcher/' },
      { text: 'Developer Guide', link: '/docs/developer-guide/' },
      { text: 'Contributor Guide', link: '/docs/contributor-guide/' },
    ],

    sidebar: {
      '/docs/user-guide/': UserGuideSidebar,
      '/docs/developer-guide/': DeveloperGuideSidebar,
      '/docs/contributor-guide/': ContributorGuideSidebar,
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/koukomin/Telos-Launcher' },
    ],
    footer: {
      message:
        'Released under the GPL-3.0 license. Telos is a fork of <a href="https://github.com/MM2-0/Kvaesitso">Kvaesitso</a>.',
      copyright: 'Copyright © 2026 Telos contributors and MM2-0 / the Kvaesitso contributors',
    },
    search: {
      provider: 'local',
    },
    editLink: {
      pattern: 'https://github.com/koukomin/Telos-Launcher/edit/main/docs/:path',
      text: 'Edit this page on GitHub',
    },
  },
  head: [
    ['link', { rel: 'icon', href: '/Telos-Launcher/icon.png' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    [
      'link',
      {
        rel: 'stylesheet',
        href: 'https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200&display=swap'
      }
    ],
    ['meta', { name: 'keywords', content: 'Telos, Android Launcher, Search Launcher, Kvaesitso, Open Source, Privacy, Android App Suite, F-Droid' }],
    ['meta', { name: 'author', content: 'Telos Contributors' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:locale', content: 'en_US' }],
    ['meta', { property: 'og:site_name', content: 'Telos Launcher Documentation' }],
    ['meta', { property: 'og:title', content: 'Telos Launcher — Search-Focused Android Launcher & Privacy App Suite' }],
    ['meta', { property: 'og:description', content: 'Telos is a search-focused, free and open-source Android launcher with a suite of privacy-minded virtual apps.' }],
    ['meta', { property: 'og:image', content: 'https://koukomin.github.io/Telos-Launcher/icon.png' }],
    ['meta', { property: 'og:url', content: 'https://koukomin.github.io/Telos-Launcher/' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:title', content: 'Telos Launcher — Search-Focused Android Launcher' }],
    ['meta', { name: 'twitter:description', content: 'Search-focused, free and open-source Android launcher with a suite of privacy-minded apps.' }],
    ['meta', { name: 'twitter:image', content: 'https://koukomin.github.io/Telos-Launcher/icon.png' }],
  ],
})
