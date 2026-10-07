import { defineConfig } from 'vitepress'
import { UserGuideSidebar } from '../docs/user-guide/sidebar.ts'
import { DeveloperGuideSidebar } from '../docs/developer-guide/sidebar.ts'
import { ContributorGuideSidebar } from '../docs/contributor-guide/sidebar.ts'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: 'Telos',
  description:
    'Telos is a search-focused, free and open source Android launcher with a suite of privacy-minded apps. A fork of Kvaesitso.',
  lastUpdated: true,
  themeConfig: {
    logo: '/icon.png',
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'User Guide', link: '/docs/user-guide/' },
      { text: 'Telos Apps', link: '/docs/user-guide/telos/' },
      { text: 'Launcher', link: '/docs/user-guide/telos/launcher' },
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
  head: [['link', { rel: 'icon', href: '/icon.png' }]],
})
