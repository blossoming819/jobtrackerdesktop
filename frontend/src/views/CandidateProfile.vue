<template>
  <section class="profile-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">ApplyMate</p>
        <h1>个人档案</h1>
        <p>这是一份跨公司复用的求职信息源。保存时会自动创建快照，后续简历解析只能生成待确认变更。</p>
      </div>
      <div class="profile-actions">
        <el-tag type="info">Schema {{ profile.schemaVersion }}</el-tag>
        <el-tag type="success">修订 {{ profile.revision }}</el-tag>
        <el-button :loading="loading" @click="load">重新读取</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存档案</el-button>
      </div>
    </div>

    <el-alert title="0.1 基础编辑器" type="info" :closable="false" show-icon>
      当前先以可校验 JSON 维护档案，后续会替换成基础资料、教育经历、项目经历等分区表单。
    </el-alert>

    <el-card class="profile-editor-card">
      <template #header>
        <div class="card-header"><span>Candidate Profile</span><small>profileId: {{ profile.profileId }}</small></div>
      </template>
      <el-input v-model="contentText" type="textarea" :rows="24" class="profile-json" spellcheck="false" />
    </el-card>

    <el-card class="snapshot-card">
      <template #header><div class="card-header"><span>版本快照</span><small>恢复快照会生成一条新的修订记录</small></div></template>
      <el-empty v-if="snapshots.length === 0" description="尚未保存档案快照" :image-size="72" />
      <el-table v-else :data="snapshots" size="small">
        <el-table-column prop="revision" label="修订" width="100"><template #default="{ row }">v{{ row.revision }}</template></el-table-column>
        <el-table-column prop="createdAt" label="保存时间" min-width="180" />
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button text type="primary" @click="restore(row)">恢复</el-button></template></el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { candidateProfileApi } from '../api'
import type { CandidateProfileResponse, ProfileSnapshot } from '../types'

const loading = ref(false)
const saving = ref(false)
const contentText = ref('{}')
const snapshots = ref<ProfileSnapshot[]>([])
const profile = ref<CandidateProfileResponse>({ profileId: 'default', schemaVersion: '0.1', revision: 0, content: {} })

function renderContent(content: Record<string, unknown>) {
  contentText.value = JSON.stringify(content, null, 2)
}

async function load() {
  loading.value = true
  try {
    profile.value = await candidateProfileApi.get()
    renderContent(profile.value.content)
    snapshots.value = await candidateProfileApi.snapshots()
  } finally {
    loading.value = false
  }
}

async function save() {
  let content: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(contentText.value)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error()
    content = parsed as Record<string, unknown>
  } catch {
    ElMessage.error('请输入合法的 JSON 对象')
    return
  }
  saving.value = true
  try {
    profile.value = await candidateProfileApi.save(content)
    renderContent(profile.value.content)
    snapshots.value = await candidateProfileApi.snapshots()
    ElMessage.success(`档案已保存为修订 v${profile.value.revision}`)
  } finally {
    saving.value = false
  }
}

async function restore(snapshot: ProfileSnapshot) {
  await ElMessageBox.confirm(`恢复 v${snapshot.revision} 后会创建一个新的修订版本，是否继续？`, '恢复档案快照', { type: 'warning' })
  profile.value = await candidateProfileApi.restore(snapshot.id)
  renderContent(profile.value.content)
  snapshots.value = await candidateProfileApi.snapshots()
  ElMessage.success(`已恢复为新修订 v${profile.value.revision}`)
}

onMounted(load)
</script>

<style scoped>
.profile-page { display: grid; gap: 18px; max-width: 1180px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.page-heading h1 { margin: 4px 0 8px; font-size: 28px; }
.page-heading p { margin: 0; color: var(--el-text-color-secondary); line-height: 1.7; }
.eyebrow { color: var(--el-color-primary) !important; font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.profile-actions { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.profile-editor-card, .snapshot-card { border-radius: 14px; }
.card-header { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; font-weight: 700; }
.card-header small { color: var(--el-text-color-secondary); font-weight: 400; }
.profile-json :deep(textarea) { font-family: Consolas, 'Courier New', monospace; line-height: 1.55; }
@media (max-width: 760px) { .page-heading { display: grid; } .profile-actions { justify-content: flex-start; } }
</style>
