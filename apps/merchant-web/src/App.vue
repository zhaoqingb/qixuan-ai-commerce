<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <el-container class="app-layout">
    <!-- 左侧导航 -->
    <el-aside width="220px" class="sidebar">
      <div class="logo">栖选 · 商家中心</div>

      <el-menu
        router
        default-active="/categories"
        background-color="#18212f"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/categories"> 商品分类 </el-menu-item>
        <el-menu-item index="/products"> 商品管理 </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主体 -->
    <el-container>
      <el-header class="header">
        <span>栖选 AI 智能电商平台</span>
        <span class="header-right">商家管理后台</span>
      </el-header>

      <el-main class="main-content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: Arial, 'Microsoft YaHei', sans-serif;
}

#app {
  width: 100%;
  min-height: 100vh;
}

.app-layout {
  min-height: 100vh;
}

.sidebar {
  background: #18212f;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 18px;
}

.sidebar .el-menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: white;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
}

.header-right {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.main-content {
  background: #f5f7fa;
  padding: 0;
}
</style>
