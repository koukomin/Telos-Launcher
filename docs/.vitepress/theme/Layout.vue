<script setup>
  import DefaultTheme from 'vitepress/theme'
  import Footer from './Footer.vue'

  const { Layout } = DefaultTheme
</script>

<template>
  <Layout>
    <template #layout-top>
      <div class="keep-android-open-banner">
        <a href="https://keepandroidopen.org/" target="_blank" rel="noopener">
          🌐 <strong>Keep Android Open</strong> — Join the Campaign for Software Freedom &amp; User Choice!
        </a>
      </div>
    </template>
    <template #doc-bottom><Footer /></template>
  </Layout>
</template>

<style scoped>
.keep-android-open-banner {
  background: linear-gradient(90deg, #1e3c72 0%, #2a5298 100%);
  color: #ffffff;
  text-align: center;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
}
.keep-android-open-banner a {
  color: #ffffff;
  text-decoration: none;
}
.keep-android-open-banner a:hover {
  text-decoration: underline;
}
</style>
