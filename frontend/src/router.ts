import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from './views/Dashboard.vue'
import Applications from './views/Applications.vue'
import ApplicationDetail from './views/ApplicationDetail.vue'
import Resume from './views/Resume.vue'
import Reminder from './views/Reminder.vue'
import Notes from './views/Notes.vue'
import Settings from './views/Settings.vue'
import CandidateProfile from './views/CandidateProfile.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: Dashboard },
    { path: '/applications', component: Applications },
    { path: '/application/:id', component: ApplicationDetail },
    { path: '/resume', component: Resume },
    { path: '/notes', component: Notes },
    { path: '/reminder', component: Reminder },
    { path: '/profile', component: CandidateProfile },
    { path: '/settings', component: Settings }
  ]
})
