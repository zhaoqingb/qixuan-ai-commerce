<template>
  <div class="login">
    <el-card class="login_card">
      <h2 class="login_title">商家后台</h2>

      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input type="password" v-model="form.password" placeholder="请输入密码"></el-input>
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit"
          >登录</el-button
        >
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { saveToken } from '@/api/http'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.error('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    saveToken(res.token)
    ElMessage.success('登录成功')

    // 被路由守卫拦下来的，登录后回到原来的目标页
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      router.replace(redirect)
    } else {
      router.push('/seckill')
    }
  } finally {
    loading.value = false
  }
}
</script>
<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.login__card {
  width: 360px;
}

.login__title {
  margin: 0 0 20px;
  font-size: 18px;
  text-align: center;
}
</style>
