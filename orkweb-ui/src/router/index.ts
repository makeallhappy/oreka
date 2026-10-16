import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', name: 'Segments', component: () => import('@/views/SegmentsView.vue') },
        { path: 'segments/:id', name: 'SegmentDetail', component: () => import('@/views/SegmentDetailView.vue') },
        { path: 'users', name: 'Users', component: () => import('@/views/UsersView.vue') },
        { path: 'programs', name: 'Programs', component: () => import('@/views/ProgramsView.vue') },
        { path: 'services', name: 'Services', component: () => import('@/views/ServicesView.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'Login' && auth.isAuthenticated) {
    return { name: 'Segments' }
  }
})

export default router
