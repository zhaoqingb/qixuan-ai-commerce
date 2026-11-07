import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// 引入 Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'


const app = createApp(App)

// 状态管理
app.use(createPinia())


// 路由管理
app.use(router)

// Element Plus组件库
app.use(ElementPlus)

app.mount('#app')
