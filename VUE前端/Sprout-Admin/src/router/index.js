import { createRouter, createWebHistory } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

// 布局组件
import AdminLayout from '@/layout/AdminLayout.vue'

// 页面组件
import LoginView from '@/views/login/LoginView.vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'

// 用户管理
import UserManagement from '@/views/user/UserManagement.vue'
import UserDetail from '@/views/user/UserDetail.vue'

// 内容管理
import PostManagement from '@/views/content/PostManagement.vue'
import PostDetail from '@/views/content/PostDetail.vue'
import CommentManagement from '@/views/content/CommentManagement.vue'
import TagManagement from '@/views/content/TagManagement.vue'

// 植物管理
import PlantManagement from '@/views/plant/PlantManagement.vue'
import PlantDetail from '@/views/plant/PlantDetail.vue'
import PlantRecognition from '@/views/plant/PlantRecognition.vue'

// AI管理
import AiSettings from '@/views/ai/AiSettings.vue'

// 消息管理
import MessageManagement from '@/views/message/MessageManagement.vue'
import SystemMessage from '@/views/message/SystemMessage.vue'
import ReportManagement from '@/views/message/ReportManagement.vue'

// 数据分析
import DataAnalysis from '@/views/analysis/DataAnalysis.vue'
import UserAnalysis from '@/views/analysis/UserAnalysis.vue'
import ContentAnalysis from '@/views/analysis/ContentAnalysis.vue'

// 系统设置
import SystemSettings from '@/views/system/SystemSettings.vue'
import AdminManagement from '@/views/system/AdminManagement.vue'
import PermissionManagement from '@/views/system/PermissionManagement.vue'
import BillboardManagement from '@/views/system/BillboardManagement.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: LoginView,
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: DashboardView,
          meta: { title: '仪表盘', icon: 'DataBoard' }
        },
        {
          path: 'users',
          name: 'UserManagement',
          component: UserManagement,
          meta: { title: '用户管理', icon: 'User' }
        },
        // {
        //   path: 'users/:id',
        //   name: 'UserDetail',
        //   component: UserDetail,
        //   meta: { title: '用户详情', hidden: true }
        // },
        {
          path: 'posts',
          name: 'PostManagement',
          component: PostManagement,
          meta: { title: '文章管理', icon: 'Document' }
        },
        {
          path: 'posts/:id',
          name: 'PostDetail',
          component: PostDetail,
          meta: { title: '文章详情', hidden: true }
        },
        // {
        //   path: 'comments',
        //   name: 'CommentManagement',
        //   component: CommentManagement,
        //   meta: { title: '评论管理', icon: 'ChatLineRound' }
        // },
        {
          path: 'tags',
          name: 'TagManagement',
          component: TagManagement,
          meta: { title: '标签管理', icon: 'PriceTag' }
        },
        {
          path: 'plants',
          name: 'PlantManagement',
          component: PlantManagement,
          meta: { title: '植物管理', icon: 'Grape' }
        },
        {
          path: 'billboards',
          name: 'BillboardManagement',
          component: BillboardManagement,
          meta: { title: '公告管理', icon: 'Bell' }
        },
        // {
        //   path: 'plants/:id',
        //   name: 'PlantDetail',
        //   component: PlantDetail,
        //   meta: { title: '植物详情', hidden: true }
        // },
        // {
        //   path: 'plant-recognition',
        //   name: 'PlantRecognition',
        //   component: PlantRecognition,
        //   meta: { title: '识别记录', icon: 'View' }
        // },
        {
          path: 'rag-settings',
          name: 'RagSettings',
          component: AiSettings,
          meta: { title: 'RAG 配置', icon: 'Setting' }
        },
        // {
        //   path: 'messages',
        //   name: 'MessageManagement',
        //   component: MessageManagement,
        //   meta: { title: '私信管理', icon: 'Message' }
        // },
        // {
        //   path: 'system-messages',
        //   name: 'SystemMessage',
        //   component: SystemMessage,
        //   meta: { title: '系统消息', icon: 'Bell' }
        // },
        // {
        //   path: 'reports',
        //   name: 'ReportManagement',
        //   component: ReportManagement,
        //   meta: { title: '举报管理', icon: 'Warning' }
        // },
        // {
        //   path: 'analysis',
        //   name: 'DataAnalysis',
        //   component: DataAnalysis,
        //   meta: { title: '数据分析', icon: 'TrendCharts' }
        // },
        // {
        //   path: 'user-analysis',
        //   name: 'UserAnalysis',
        //   component: UserAnalysis,
        //   meta: { title: '用户分析', icon: 'UserFilled' }
        // },
        // {
        //   path: 'content-analysis',
        //   name: 'ContentAnalysis',
        //   component: ContentAnalysis,
        //   meta: { title: '内容分析', icon: 'DataAnalysis' }
        // },
        // {
        //   path: 'settings',
        //   name: 'SystemSettings',
        //   component: SystemSettings,
        //   meta: { title: '系统设置', icon: 'Tools' }
        // },
        // {
        //   path: 'admin-management',
        //   name: 'AdminManagement',
        //   component: AdminManagement,
        //   meta: { title: '管理员管理', icon: 'UserFilled' }
        // },
        // {
        //   path: 'permissions',
        //   name: 'PermissionManagement',
        //   component: PermissionManagement,
        //   meta: { title: '权限管理', icon: 'Key' }
        // }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard'
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const adminStore = useAdminStore()
  
  if (to.meta.requiresAuth === false) {
    // 不需要认证的页面
    if (adminStore.isLoggedIn && to.path === '/login') {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    // 需要认证的页面
    if (!adminStore.isLoggedIn) {
      next('/login')
    } else {
      next()
    }
  }
})

export default router
