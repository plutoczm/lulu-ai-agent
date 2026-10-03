import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser } from '../api'

const publicDemo = import.meta.env.VITE_PUBLIC_DEMO === 'true'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录 - 噜噜', public: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '噜噜 - 超级智能体应用平台' }
  },
  {
    path: '/apps',
    redirect: '/'
  },
  {
    path: '/chat-coach',
    name: 'ChatCoach',
    component: () => import('../views/ChatCoach.vue'),
    meta: { title: 'AI对话军师 - 噜噜' }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: { title: 'AI超级智能体 - 噜噜' }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('../views/Knowledge.vue'),
    meta: { title: '关系知识库 - 噜噜' }
  },
  {
    path: '/status',
    name: 'SystemStatus',
    component: () => import('../views/SystemStatus.vue'),
    meta: { title: '模型与系统状态 - 噜噜' }
  },
  {
    path: '/models',
    name: 'ModelSettings',
    component: () => import('../views/ModelSettings.vue'),
    meta: { title: '模型配置 - 噜噜', adminOnly: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  if (to.meta.title) document.title = to.meta.title
  if (publicDemo) return true
  if (to.meta.public) return true

  try {
    const response = await getCurrentUser()
    const user = response.data?.user
    if (to.meta.adminOnly && !user?.admin) return '/'
    return true
  } catch {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
})

export default router
