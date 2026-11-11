// docs/.vitepress/theme/index.ts
import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'

// Import only the required subset/variant of Material Symbols
// to prevent bundling multi-megabyte variable font files.
// Note: Alternatively, load via `<link rel="preload">` in config.ts head.
import 'material-symbols/outlined.css'

// Theme custom styles (order matters: base overrides first, then theme specifics)
import './style.css'
import './custom.css'

import Layout from './Layout.vue'

export default {
  extends: DefaultTheme,
  Layout,

  // enhanceApp is omitted when empty to reduce hydration overhead and avoid lint warnings.
  // Uncomment and type correctly if custom components or router hooks are needed:
  /*
  enhanceApp({ app, router }) {
    // Example: Register custom global components or navigation listeners
    // router.onBeforeRouteChange = (to) => { ... }
  },
  */
} satisfies Theme
