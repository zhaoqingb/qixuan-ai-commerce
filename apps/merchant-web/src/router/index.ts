import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/http'


const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: "/",
      redirect:"/categories"
    },
    {
      path: "/categories",
      name: "category",
      component: () => import('../views/category/CategoryView.vue')
    },
    {
      path: '/products',
      name: "product",
      component: () => import('../views/product/ProductView.vue')
    },
    {
      path: "/products/new",
      name: "product-create",
      component:()=>import('../views/product/ProductCreateView.vue')
    },
    {
      path:"/login",
      name:"login",
      component:()=>import('../views/login/LoginView.vue')
    },
    {
      path: "/seckill",
      name: "seckill-admin",
      component: () => import('../views/seckill/SeckillAdminView.vue')
    },
    {
      path: "/ai",
      name: "ai-chat",
      component: () => import('../views/ai/AiChatView.vue')
    }
  ],
})

/**
 * 登录守卫。
 *
 * 只挡"没登录"这一种情况：有 token 就放行，至于 token 是否过期、有没有权限，
 * 由后端在接口层判断（401 会由 http 拦截器踢回登录页）。
 * 前端不重复做权限判断 —— 它永远不可信，真正的校验必须在服务端。
 */
router.beforeEach((to) => {
  const needLogin = to.name !== 'login'
  if (needLogin && !getToken()) {
    // 带上原目标，登录成功后可以直接回去
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
