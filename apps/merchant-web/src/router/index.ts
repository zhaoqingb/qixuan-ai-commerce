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
    }
  ],
})

export default router
