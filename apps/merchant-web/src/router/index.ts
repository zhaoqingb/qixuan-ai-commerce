import { createRouter, createWebHistory } from 'vue-router'


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
    }
  ],
})

export default router
