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
        · 一句话创建网站
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts"></script>

<style scoped>
.footer {
  background: transparent;
  text-align: center;
  padding: 12px 20px 28px;
  margin-top: 0;
  border-top: none;
}

.copyright {
  margin: 0;
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
  text-decoration: underline;
}
</style>
