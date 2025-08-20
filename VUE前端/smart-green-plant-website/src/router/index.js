import { createRouter, createWebHistory } from 'vue-router'
import NotFound from '../views/NotFound/NotFound.vue'
import { useUserStore } from '../stores'
console.log(import.meta.env.DEV)

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      component: () => import('@/views/login/LoginPage.vue')
    },
    {
      path: '/',
      component: () => import('@/views/layout/LayoutContainer.vue'),
      redirect: '/home',
      children: [
        {
          path: '/home',
          name: 'home',
          component: () => import('@/views/home/HomePage.vue')
        },
        {
          path: '/control',
          component: () => import('@/views/control/DeviceControl.vue')
        },
        {
          path: '/ai',
          component: () => import('@/views/ai/AiPage.vue')
        },
        {
          path: '/history',
          component: () => import('@/views/history/HistoryPage.vue')
        },
        {
          path: '/user',
          component: () => import('@/views/user/UserPage.vue')
        },
        {
          path: '/plant',
          component: () => import('@/views/plant/PlantManage.vue')
        },
        {
          path: '/categories',
          component: () => import('@/views/plant/PlantCategories.vue')
        },
      ]
    },
    { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound }
  ]
})

// 登录拦截  路由守卫
router.beforeEach((to) => {
  const useStore = useUserStore()
  if (!useStore.token && to.path !== '/login') return '/login' 
  //已登录或者已在登录页则直接放行
})

export default router
