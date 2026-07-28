<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">
        <a
          href="https://www.baidu.com/"
          target="_blank"
          rel="noopener noreferrer"
          class="author-link"
        >
          Positive
        </a>
        <span class="sep">·</span>
        <span class="slogan">一句话呈所想</span>
        <a
          class="github-link"
          href="https://github.com/2871273484/Positive-ai-code"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="GitHub 仓库"
          title="GitHub"
        >
          <GithubOutlined />
        </a>
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
import { GithubOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.footer {
  width: 100% !important;
  max-width: none !important;
  display: flex !important;
  justify-content: center !important;
  align-items: center !important;
  background: transparent !important;
  text-align: center !important;
  padding: 12px 20px 28px !important;
  margin: 0 !important;
  border-top: none;
}

.footer-content {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.copyright {
  margin: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
  font-family: 'Nunito Sans', 'PingFang SC', sans-serif;
}

.author-link {
  color: #0f172a;
  text-decoration: none;
  font-weight: 700;
}

.author-link:hover {
  color: #0ea5e9;
}

.sep {
  color: #94a3b8;
}

.slogan {
  color: #94a3b8;
}

.github-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 4px;
  color: #64748b;
  font-size: 16px;
  line-height: 1;
  transition: color 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

.github-link:hover {
  color: #0f172a;
}
</style>
