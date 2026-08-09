<template>
  <el-container class="layout" :class="{ collapsed: sidebarCollapsed, 'has-warning': unfinishedCount > 0 }">
    <el-aside :width="sidebarCollapsed ? '76px' : '248px'" class="sidebar">
      <div class="brand">
        <img class="brand-mark" src="/favicon.svg" alt="" aria-hidden="true" />
        <span class="brand-copy" :aria-hidden="sidebarCollapsed">
          <strong class="brand-text">JobTracker</strong>
          <small>求职进度管理</small>
        </span>
      </div>
      <el-menu
        router
        :default-active="$route.path"
      >
        <el-menu-item index="/dashboard" :title="sidebarCollapsed ? 'Dashboard' : ''"><el-icon><DataBoard /></el-icon><template #title><span class="menu-label">Dashboard</span></template></el-menu-item>
        <el-menu-item index="/applications" :title="sidebarCollapsed ? '投递管理' : ''"><el-icon><FolderOpened /></el-icon><template #title><span class="menu-label">投递管理</span></template></el-menu-item>
        <el-menu-item index="/resume" :title="sidebarCollapsed ? '简历管理' : ''"><el-icon><Document /></el-icon><template #title><span class="menu-label">简历管理</span></template></el-menu-item>
        <el-menu-item index="/notes" :title="sidebarCollapsed ? '笔记管理' : ''"><el-icon><Memo /></el-icon><template #title><span class="menu-label">笔记管理</span></template></el-menu-item>
        <el-menu-item index="/profile" :title="sidebarCollapsed ? '个人档案' : ''"><el-icon><User /></el-icon><template #title><span class="menu-label">个人档案</span></template></el-menu-item>
        <el-menu-item index="/reminder" :title="sidebarCollapsed ? '日程管理' : ''"><el-icon><Bell /></el-icon><template #title><span class="menu-label">日程管理</span></template></el-menu-item>
        <el-menu-item index="/settings" :title="sidebarCollapsed ? '系统设置' : ''"><el-icon><Setting /></el-icon><template #title><span class="menu-label">系统设置</span></template></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <el-button
          class="collapse-btn"
          text
          :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
        </el-button>
        <span class="topbar-divider"></span>
        <div class="topbar-context">
          <small>Workspace</small>
          <strong>个人求职助手</strong>
        </div>
      </el-header>
      <nav class="workspace-tabs" aria-label="已打开页面">
        <div class="workspace-tabs-scroll">
          <button
            v-for="tab in workspaceTabs"
            :key="tab.key"
            class="workspace-tab"
            :class="{ active: tab.key === activeTabKey }"
            :title="tab.label"
            @click="openTab(tab)"
          >
            <span>{{ tab.label }}</span>
            <el-icon v-if="tab.closable" class="workspace-tab-close" @click.stop="closeTab(tab)"><Close /></el-icon>
          </button>
        </div>
      </nav>
      <button v-if="unfinishedCount > 0" class="global-warning" @click="$router.push('/reminder?quick=pending')">
        <el-icon><Bell /></el-icon>
        <strong>未完成预警</strong>
        <span>当前还有 {{ unfinishedCount }} 个未完成日程，请及时处理。</span>
      </button>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Close, DataBoard, Document, Expand, Fold, FolderOpened, Memo, Setting, User } from '@element-plus/icons-vue'
import { reminderApi } from './api'
import type { Reminder } from './types'

const route = useRoute()
const router = useRouter()
const sidebarCollapsed = ref(false)
const reminders = ref<Reminder[]>([])
type WorkspaceTab = { key: string; path: string; fullPath: string; label: string; closable: boolean }
const workspaceTabs = ref<WorkspaceTab[]>([{ key: '/dashboard', path: '/dashboard', fullPath: '/dashboard', label: 'Dashboard', closable: false }])
let timer: number | undefined

const unfinishedCount = computed(() => reminders.value.filter((item) => item.status === '未完成').length)

async function loadReminderWarning() {
  try {
    reminders.value = await reminderApi.list() as unknown as Reminder[]
  } catch {
    reminders.value = []
  }
}

onMounted(() => {
  loadReminderWarning()
  window.addEventListener('reminders-updated', loadReminderWarning)
  timer = window.setInterval(loadReminderWarning, 30000)
})

onBeforeUnmount(() => {
  window.removeEventListener('reminders-updated', loadReminderWarning)
  if (timer) window.clearInterval(timer)
})

watch(() => route.fullPath, loadReminderWarning)

function tabLabel(path: string) {
  if (path.startsWith('/application/')) return '岗位详情'
  return ({ '/dashboard': 'Dashboard', '/applications': '投递管理', '/resume': '简历管理', '/notes': '笔记管理', '/reminder': '日程管理', '/profile': '个人档案', '/settings': '系统设置' } as Record<string, string>)[path] || '工作页'
}

function syncWorkspaceTab() {
  const key = route.path
  const existing = workspaceTabs.value.find((tab) => tab.key === key)
  if (existing) {
    existing.fullPath = route.fullPath
    return
  }
  workspaceTabs.value.push({ key, path: route.path, fullPath: route.fullPath, label: tabLabel(route.path), closable: key !== '/dashboard' })
}

function openTab(tab: WorkspaceTab) { router.push(tab.fullPath) }

function closeTab(tab: WorkspaceTab) {
  const index = workspaceTabs.value.findIndex((item) => item.key === tab.key)
  if (index < 0 || !tab.closable) return
  const isActive = tab.key === route.path
  workspaceTabs.value.splice(index, 1)
  if (isActive) {
    const next = workspaceTabs.value[index - 1] || workspaceTabs.value[index] || workspaceTabs.value[0]
    router.push(next.fullPath)
  }
}

const activeTabKey = computed(() => route.path)
watch(() => route.fullPath, syncWorkspaceTab, { immediate: true })
</script>
