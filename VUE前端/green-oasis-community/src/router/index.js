import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores'
import HomeView from '@/views/HomeView.vue'
import SearchView from '@/components/search/SearchView.vue'
import LayoutContainer from '@/views/layout/LayoutContainer.vue'
import LoginView from '@/views/login/LoginView.vue'
import Post from '@/views/posts/PostDetail.vue'
import UserView from '@/views/user/UserView.vue'
import SettingView from '@/views/user/SettingView.vue'
import PostCreate from '@/views/posts/PostCreate.vue'
import PlantsView from '@/views/plants/PlantsView.vue'
import AiView from '@/views/ai/AiView.vue'
import MessageView from '@/views/message/MessageView.vue'
import TopicList from '@/components/user/TopicList.vue'
import FollowerList from '@/components/user/FollowerList.vue'
import FollowingList from '@/components/user/FollowingList.vue'
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: LayoutContainer,
      redirect: '/home',
      children: [
        {
          path: '/home',
          name: 'home',
          component: HomeView,
        },
        {
          path: '/post/create',
          name: 'postCreate',
          component: PostCreate,
        },
        {
          path: '/post/:id',
          name: 'post',
          component: Post,
        },
        {
          path: '/search',
          name: 'search',
          component: SearchView,
        },
        {
          path: '/user',
          name: 'user',
          component: UserView,
          redirect: '/user/topics',
          children: [
            {
              path: 'topics',
              name: 'userTopics',
              component: TopicList,
            },
            {
              path: 'followers',
              name: 'userFollowers',
              component: FollowerList,
            },
            {
              path: 'following',
              name: 'userFollowing',
              component: FollowingList,
            }
          ]
        },
        {
          path: '/user/:username',
          name: 'userDetail',
          component: UserView,
          redirect: to => `/user/${to.params.username}/topics`,
          children: [
            {
              path: 'topics',
              name: 'userDetailTopics',
              component: TopicList,
            },
            {
              path: 'followers',
              name: 'userDetailFollowers',
              component: FollowerList,
            },
            {
              path: 'following',
              name: 'userDetailFollowing',
              component: FollowingList,
            }
          ]
        },
        {
          path: '/setting',
          name: 'setting',
          component: SettingView,
        },
        {
          path: '/plants',
          name: 'plants',
          component: PlantsView,
        },
        {
          path: '/ai',
          name: 'ai',
          component: AiView,
        },
        {
          path: '/message',
          name: 'message',
          component: MessageView,
        }
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home'
    }
  ],
})

// 登录拦截
router.beforeEach((to, from, next) => {
  const useStore = useUserStore()
  
  // 处理登录拦截
  if (!useStore.token && to.path !== '/login') {
    next('/login')
    return
  }

  // 处理路由参数
  if (to.path === '/message') {
    // 如果跳转到消息页面时带有 tab 参数，确保它是有效的
    const validTabs = ['notification', 'chat']
    if (to.query.tab && !validTabs.includes(to.query.tab)) {
      next({ path: '/message', query: { tab: 'notification' } })
      return
    }
  }

  next()
})

export default router
