import { createRouter, createWebHistory } from 'vue-router'

import CategoryView from "../views/category/CategoryView.vue"

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
      component:CategoryView
    }
  ],
})

export default router
