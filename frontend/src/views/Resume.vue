<template>
  <h1 class="page-title">简历管理</h1>

  <div class="panel">
    <div class="toolbar">
      <el-button type="primary" @click="openUpload">上传简历</el-button>
      <el-button @click="$router.push('/settings')">保存位置设置</el-button>
    </div>

    <el-table :data="rows" class="resume-table" table-layout="fixed">
      <el-table-column prop="fileName" label="文件名" min-width="300" show-overflow-tooltip />
      <el-table-column prop="resumeCategory" label="简历类别" min-width="150" show-overflow-tooltip />
      <el-table-column prop="fileType" label="类型" width="80" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ Math.round(row.fileSize / 1024) }} KB</template>
      </el-table-column>
      <el-table-column label="绑定数量" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="usageOf(row.id).bindCount ? 'primary' : 'info'" effect="light">
            {{ usageOf(row.id).bindCount }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定投递" width="130" align="center">
        <template #default="{ row }">
          <div v-if="usageOf(row.id).bindCount" class="resume-usage-action">
            <el-button size="small" link type="primary" @click="openUsage(row)">查看</el-button>
          </div>
          <span v-else class="muted">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="304" fixed="right" align="center">
        <template #default="{ row }">
          <div class="resume-row-actions">
            <el-button size="small" :disabled="row.fileType !== 'pdf'" @click="preview(row)">预览</el-button>
            <el-button size="small" @click="download(row.id)">下载</el-button>
            <el-button size="small" @click="parse(row)">解析</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="uploadVisible" title="上传简历" width="560px" @closed="resetUpload">
    <el-form label-width="90px">
      <el-form-item label="简历文件" required>
        <el-upload :auto-upload="false" :limit="1" accept=".pdf,.doc,.docx" @change="selectUploadFile" @remove="uploadFile = null">
          <el-button>选择文件</el-button>
          <template #tip>支持 PDF、DOC、DOCX 格式</template>
        </el-upload>
      </el-form-item>
      <el-form-item label="简历类别" required>
        <el-select v-model="uploadCategory" multiple filterable allow-create default-first-option placeholder="选择、输入简历类别，可多选">
          <el-option v-for="item in resumeCategoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="uploadVisible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="upload">上传</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dialogVisible" title="编辑简历信息" width="520px">
    <el-form label-width="90px">
      <el-form-item label="简历类别">
        <el-select v-model="form.resumeCategory" multiple filterable allow-create default-first-option placeholder="选择、输入简历类别，可多选">
          <el-option v-for="item in resumeCategoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="previewVisible" :title="previewTitle" width="86%" top="4vh" destroy-on-close>
    <iframe v-if="previewUrl" class="pdf-preview" :src="previewUrl"></iframe>
  </el-dialog>

  <el-dialog v-model="usageVisible" :title="`${usageResumeName} - 绑定投递`" width="960px" class="resume-usage-dialog">
    <el-table :data="selectedUsage?.applications || []">
      <el-table-column label="公司" min-width="150">
        <template #default="{ row }">{{ row.companyName || '-' }}</template>
      </el-table-column>
      <el-table-column label="岗位" min-width="190">
        <template #default="{ row }">{{ row.positionName || '-' }}</template>
      </el-table-column>
      <el-table-column label="投递简历名称" min-width="230" show-overflow-tooltip>
        <template #default="{ row }">{{ row.resumeAlias || '沿用原文件名' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">{{ row.currentStatus || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="88">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="goApplication(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <el-dialog v-model="parseResultVisible" title="简历解析结果" width="min(920px, calc(100vw - 32px))" class="resume-parse-dialog">
    <el-alert title="解析用于读取简历文本，并生成可核对的档案草稿；不会直接覆盖个人档案。" type="info" :closable="false" show-icon />
    <el-descriptions v-if="parseResult" :column="2" border class="parse-summary">
      <el-descriptions-item label="简历">{{ parsedResume?.fileName }}</el-descriptions-item>
      <el-descriptions-item label="解析状态">{{ parseStatusLabel(parseResult.status) }}</el-descriptions-item>
      <el-descriptions-item label="提取字符数">{{ parseResult.extractedLength }}</el-descriptions-item>
      <el-descriptions-item label="解析记录">#{{ parseResult.parseRecordId }}</el-descriptions-item>
    </el-descriptions>
    <div v-if="parseHistory.length" class="parse-history-picker">
      <div><strong>最新解析结果</strong><span>每份简历仅保留最新一次本地文本提取和最新一份成功的 AI 草稿。</span></div>
      <div class="parse-history-list"><div v-for="item in parseHistory" :key="item.parseRecordId" class="parse-history-row" :class="{ active: item.parseRecordId === selectedParseRecordId }"><button type="button" class="parse-history-main" @click="selectParseRecord(item.parseRecordId)"><strong>最新 · {{ parseStatusLabel(item.status) }}</strong><span>{{ formatHistoryTime(item.createdAt) }}</span></button><el-button size="small" :disabled="!historyDraft(item)" @click="selectParseRecord(item.parseRecordId)">{{ historyDraft(item) ? '查看 JSON' : '无 JSON' }}</el-button><el-button size="small" :disabled="!historyDraft(item)" @click="copyHistoryDraft(item)">复制</el-button></div></div>
    </div>
    <el-alert v-if="parseResult?.visionRecommended" title="提取到的文本较少，可能是扫描版或复杂排版 PDF；当前版本尚未启用视觉解析。" type="warning" :closable="false" show-icon />
    <div v-if="!parseResult?.visionRecommended" class="ai-model-picker">
      <div><strong>本次使用的模型</strong><span>仅影响本次解析，不修改 YAML 默认路由</span></div>
      <el-select v-model="selectedAiProvider" :disabled="aiParsing" placeholder="自动选择">
        <el-option label="自动选择（按 YAML 路由）" value="" />
        <el-option v-for="item in modelProviderOptions" :key="item.id" :label="providerOptionLabel(item)" :value="item.id" :disabled="!item.usable" />
      </el-select>
      <el-button :loading="providerLoading" :disabled="aiParsing" @click="loadAiProviders">刷新</el-button>
    </div>
    <el-alert v-if="providerHint" class="provider-hint" :title="providerHint" type="warning" :closable="false" show-icon />
    <div v-if="parsingId === parsedResume?.id" class="parse-local-progress"><span class="parse-progress-dot"></span><div><strong>正在提取本地简历文本</strong><span>解析页面已打开，完成后会自动刷新结果 · 已用 {{ formatElapsed(localParseElapsed) }}</span></div></div>
    <div v-if="aiParsing || aiStreamText" class="ai-conversation">
      <div class="chat-row user-row">
        <div class="chat-avatar user-avatar">我</div>
        <div class="chat-bubble user-bubble">请使用{{ selectedProviderLabel }}解析“{{ parsedResume?.fileName }}”，生成一份可核对的个人档案草稿。</div>
      </div>
      <div class="chat-row assistant-row">
        <div class="chat-avatar assistant-avatar">AI</div>
        <div class="chat-bubble assistant-bubble">
          <div class="assistant-meta"><strong>ApplyMate 助手</strong><span>{{ aiStreamStatus }}</span><span>{{ formatElapsed(aiParseElapsed) }}</span></div>
          <pre v-if="aiStreamText">{{ aiStreamText }}<i v-if="aiParsing" class="typing-cursor"></i></pre>
          <div v-else class="thinking-line"><i></i><i></i><i></i><span>正在等待模型返回第一段内容</span></div>
        </div>
      </div>
    </div>
    <div v-if="currentDraft" class="parse-draft">
      <div class="parse-draft-heading"><strong>格式化档案草稿</strong><el-button link type="primary" @click="copyParseDraft">复制 JSON</el-button></div>
      <details open><summary>结构化 JSON（可收起）</summary><pre>{{ JSON.stringify(currentDraft, null, 2) }}</pre></details>
      <div class="profile-import-bar">
        <div><strong>导入个人档案</strong><span>{{ profileImportMode === 'merge' ? '保留目标档案已有非空内容，补充新内容；无变化时不创建修订。' : '创建一个可独立切换的新档案，不修改当前档案。' }}</span></div>
        <el-select v-model="profileImportMode"><el-option label="合并已有档案" value="merge" /><el-option label="新建独立档案" value="new" /></el-select>
        <el-select v-if="profileImportMode === 'merge'" v-model="profileImportTarget" placeholder="选择目标档案"><el-option v-for="item in profileVersions" :key="item.profileId" :label="item.defaultProfile ? `${item.name}（默认）` : item.name" :value="item.profileId" /></el-select>
        <el-input v-else v-model="newProfileName" maxlength="80" placeholder="输入新档案名称" />
        <el-button type="primary" :loading="profileImporting" @click="importCurrentDraft">确认导入</el-button>
      </div>
    </div>
    <div v-else-if="!aiParsing && !aiStreamText && parsingId !== parsedResume?.id" class="parse-empty-state">当前记录只完成了本地文本提取。可选择最新 AI 草稿，或使用 AI 生成新的档案草稿。</div>
    <template #footer>
      <span v-if="aiParsing" class="parse-timer">AI 解析已用 {{ formatElapsed(aiParseElapsed) }}</span>
      <el-button @click="parseResultVisible = false">关闭</el-button>
      <el-button type="primary" :loading="aiParsing" :disabled="parseResult?.visionRecommended" @click="runAiParse">使用 AI 生成档案草稿</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, candidateProfileApi, resumeApi, resumeCategoryOptions } from '../api'
import type { CandidateProfileSummary, Resume, ResumeParseHistory, ResumeUsage, ResumeUsageApplication } from '../types'

const rows = ref<Resume[]>([])
const usageRows = ref<ResumeUsage[]>([])
const dialogVisible = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const previewVisible = ref(false)
const usageVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('PDF 预览')
const usageResumeName = ref('')
const selectedUsage = ref<ResumeUsage | null>(null)
const parsingId = ref<number | null>(null)
const parseResultVisible = ref(false)
const aiParsing = ref(false)
const parseResult = ref<any>(null)
const parseHistory = ref<ResumeParseHistory[]>([])
const selectedParseRecordId = ref<number>()
const profileVersions = ref<CandidateProfileSummary[]>([])
const profileImportMode = ref<'merge' | 'new'>('merge')
const profileImportTarget = ref('default')
const newProfileName = ref('')
const profileImporting = ref(false)
const parsedResume = ref<Resume | null>(null)
const localParseElapsed = ref(0)
const aiParseElapsed = ref(0)
const aiStreamText = ref('')
const aiStreamStatus = ref('准备连接模型')
const aiProviders = ref<Record<string, { enabled: boolean, model: string, credentialConfigured: boolean }>>({})
const selectedAiProvider = ref('')
const providerLoading = ref(false)
const providerLoadError = ref('')
const modelProviderOptions = computed(() => Object.entries(aiProviders.value).map(([id, value]) => ({
  id, model: value.model, enabled: value.enabled, credentialConfigured: value.credentialConfigured,
  usable: value.enabled && value.credentialConfigured,
})))
const availableAiProviders = computed(() => Object.entries(aiProviders.value)
  .filter(([, value]) => value.enabled && value.credentialConfigured)
  .map(([id, value]) => ({ id, model: value.model })))
const selectedProviderLabel = computed(() => {
  const selected = availableAiProviders.value.find(item => item.id === selectedAiProvider.value)
  return selected ? `${providerName(selected.id)}（${selected.model}）` : '自动路由模型'
})
const currentDraft = computed<Record<string, any> | null>(() => {
  const value = parseResult.value?.draft
  if (!value) return null
  if (typeof value === 'string') { try { return JSON.parse(value) } catch { return null } }
  return typeof value === 'object' && !Array.isArray(value) ? value : null
})
const providerHint = computed(() => {
  if (providerLoadError.value) return providerLoadError.value
  if (!modelProviderOptions.value.length) return '当前后端没有返回模型配置，请确认连接的是已重新构建并重启的后端。'
  if (!availableAiProviders.value.length) return '当前后端没有可用模型。模型名称已列出，但需要让正在运行的后端进程重新加载 ENABLED 和 API_KEY 环境变量。'
  return ''
})
let parseTimer: ReturnType<typeof setInterval> | undefined
const form = reactive({ id: 0, fileName: '', resumeCategory: [] as string[], remark: '' })
const uploadFile = ref<any>(null)
const uploadCategory = ref<string[]>([])
const router = useRouter()
const usageMap = computed(() => new Map(usageRows.value.map(item => [item.resumeId, item])))

onMounted(load)

async function load() {
  const [resumeList, usageList] = await Promise.all([
    resumeApi.list(),
    resumeApi.usage()
  ])
  rows.value = resumeList as unknown as Resume[]
  usageRows.value = usageList as unknown as ResumeUsage[]
}

function openUpload() {
  uploadVisible.value = true
}

function selectUploadFile(file: any) {
  uploadFile.value = file.raw
}

function resetUpload() {
  uploadFile.value = null
  uploadCategory.value = []
}

async function upload() {
  if (!uploadFile.value) {
    ElMessage.warning('请选择简历文件')
    return
  }
  if (!uploadCategory.value.length) {
    ElMessage.warning('请选择至少一个简历类别')
    return
  }
  uploading.value = true
  const fd = new FormData()
  fd.append('file', uploadFile.value)
  fd.append('resumeCategory', uploadCategory.value.join('、'))
  try {
    await resumeApi.upload(fd)
    uploadVisible.value = false
    ElMessage.success('上传成功')
    await load()
  } finally {
    uploading.value = false
  }
}

function preview(row: Resume) {
  if (row.fileType !== 'pdf') {
    ElMessage.warning('当前仅支持 PDF 简历预览')
    return
  }
  previewTitle.value = row.fileName
  previewUrl.value = resumeApi.previewUrl(row.id)
  previewVisible.value = true
}

function openEdit(row: Resume) {
  Object.assign(form, row, { resumeCategory: splitCategories(row.resumeCategory) })
  dialogVisible.value = true
}

async function save() {
  await resumeApi.update(form.id, form.resumeCategory.join('、'), form.remark)
  dialogVisible.value = false
  await load()
}

async function parse(row: Resume) {
  if (parsingId.value === row.id) return
  parsedResume.value = row
  parseResult.value = null
  selectedParseRecordId.value = undefined
  parseResultVisible.value = true
  parsingId.value = row.id
  startParseTimer(localParseElapsed)
  try {
    const supportingData = Promise.all([loadAiProviders(), loadParseHistory(row.id), loadProfileVersions()])
    const extraction = resumeApi.parse(row.id) as Promise<any>
    await supportingData
    parseResult.value = await extraction
    await loadParseHistory(row.id)
    selectedParseRecordId.value = parseResult.value?.parseRecordId
  } finally { parsingId.value = null; stopParseTimer() }
}

async function runAiParse() {
  if (!parsedResume.value) return
  aiParsing.value = true
  aiStreamText.value = ''
  aiStreamStatus.value = '正在连接模型'
  startParseTimer(aiParseElapsed)
  try {
    const providerParam = selectedAiProvider.value ? `?provider=${encodeURIComponent(selectedAiProvider.value)}` : ''
    const response = await fetch(`/api/applymate/v1/resumes/${parsedResume.value.id}/parse/stream${providerParam}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/x-ndjson' },
      body: JSON.stringify({ allowCloudAi: true, allowVisionFallback: false }),
    })
    if (!response.ok || !response.body) throw new Error(`模型服务请求失败（HTTP ${response.status}）`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let pending = ''
    while (true) {
      const { done, value } = await reader.read()
      pending += decoder.decode(value || new Uint8Array(), { stream: !done })
      const lines = pending.split('\n')
      pending = done ? '' : lines.pop() || ''
      for (const line of lines) handleAiStreamEvent(line)
      if (done) {
        if (pending.trim()) handleAiStreamEvent(pending)
        break
      }
    }
    if (!parseResult.value?.draft) throw new Error('模型流已结束，但没有返回完整档案草稿')
    await loadParseHistory(parsedResume.value.id)
    selectedParseRecordId.value = parseResult.value.parseRecordId
    ElMessage.success('AI 档案草稿已生成，请核对内容后再导入个人档案')
  } catch (error) {
    aiStreamStatus.value = '解析失败'
    ElMessage.error(error instanceof Error ? error.message : 'AI 流式解析失败')
  } finally { aiParsing.value = false; stopParseTimer() }
}

async function loadAiProviders() {
  providerLoading.value = true
  providerLoadError.value = ''
  try { aiProviders.value = await aiApi.providers() }
  catch { aiProviders.value = {}; providerLoadError.value = '读取模型状态失败，请确认当前后端仍在运行。' }
  finally { providerLoading.value = false }
  if (selectedAiProvider.value && !availableAiProviders.value.some(item => item.id === selectedAiProvider.value)) selectedAiProvider.value = ''
}

async function loadParseHistory(resumeId: number) {
  try {
    const records = await resumeApi.parseRecords(resumeId, 50)
    const latestLocal = records.find(item => item.status === 'TEXT_EXTRACTED' || item.status === 'VISION_RECOMMENDED')
    const latestDraft = records.find(item => item.status === 'DRAFT_READY' && historyDraft(item))
    parseHistory.value = [latestLocal, latestDraft].filter((item): item is ResumeParseHistory => Boolean(item)).sort((a, b) => b.parseRecordId - a.parseRecordId)
  } catch { parseHistory.value = [] }
}
async function loadProfileVersions() {
  try {
    profileVersions.value = await candidateProfileApi.versions()
    if (!profileVersions.value.some(item => item.profileId === profileImportTarget.value)) profileImportTarget.value = profileVersions.value.find(item => item.defaultProfile)?.profileId || profileVersions.value[0]?.profileId || 'default'
  } catch { profileVersions.value = [] }
}
function selectParseRecord(recordId: number) {
  const item = parseHistory.value.find(row => row.parseRecordId === recordId)
  if (!item) return
  selectedParseRecordId.value = recordId
  parseResult.value = { ...item, draft: historyDraft(item), visionRecommended: item.status === 'VISION_RECOMMENDED' }
  aiStreamText.value = ''
  aiStreamStatus.value = historyDraft(item) ? '历史草稿已载入' : '历史记录无档案草稿'
}
function historyDraft(item: ResumeParseHistory): Record<string, any> | null { if (!item.draft) return null; if (typeof item.draft === 'string') { try { return JSON.parse(item.draft) } catch { return null } }; return typeof item.draft === 'object' && !Array.isArray(item.draft) ? item.draft : null }
const formatHistoryTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '时间未知'
async function copyHistoryDraft(item: ResumeParseHistory) { const draft = historyDraft(item); if (!draft) return ElMessage.warning('该记录没有档案 JSON'); await copyDraftValue(draft, `历史记录 #${item.parseRecordId} 的 JSON 已复制`) }

function providerName(id: string) {
  return ({ deepseek: 'DeepSeek', qwen: '通义千问', local: '本地模型' } as Record<string, string>)[id] || id
}

function providerOptionLabel(item: { id: string, model: string, enabled: boolean, credentialConfigured: boolean }) {
  const problems = [!item.enabled ? '未启用' : '', !item.credentialConfigured ? '未检测到 API Key' : ''].filter(Boolean)
  return `${providerName(item.id)} · ${item.model}${problems.length ? `（${problems.join('、')}）` : ''}`
}

function handleAiStreamEvent(line: string) {
  if (!line.trim()) return
  const event = JSON.parse(line) as { type: string, data: any }
  if (event.type === 'status') aiStreamStatus.value = String(event.data)
  if (event.type === 'delta') {
    aiStreamStatus.value = '正在生成档案草稿'
    aiStreamText.value += String(event.data || '')
  }
  if (event.type === 'complete') {
    parseResult.value = event.data
    aiStreamStatus.value = '草稿生成完成'
  }
  if (event.type === 'error') throw new Error(String(event.data || '模型调用失败'))
}

function startParseTimer(target: { value: number }) {
  stopParseTimer()
  target.value = 0
  const startedAt = Date.now()
  parseTimer = setInterval(() => { target.value = Math.floor((Date.now() - startedAt) / 1000) }, 1000)
}

function stopParseTimer() {
  if (parseTimer) clearInterval(parseTimer)
  parseTimer = undefined
}

function formatElapsed(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0')
  return `${minutes}:${(seconds % 60).toString().padStart(2, '0')}`
}

onUnmounted(stopParseTimer)

function parseStatusLabel(value?: string) {
  return ({ TEXT_EXTRACTED: '本地文本已提取', VISION_RECOMMENDED: '建议视觉解析', DRAFT_READY: 'AI 草稿已生成' } as Record<string, string>)[value || ''] || value || '-'
}

async function copyParseDraft() {
  if (!currentDraft.value) return ElMessage.warning('当前解析记录没有可复制的档案 JSON')
  await copyDraftValue(currentDraft.value, '当前解析记录的 JSON 已复制')
}
async function copyDraftValue(draft: Record<string, any>, successMessage: string) {
  const text = JSON.stringify(draft, null, 2)
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else throw new Error('Clipboard API unavailable')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.cssText = 'position:fixed;left:-9999px;top:0'
    document.body.appendChild(textarea)
    textarea.focus(); textarea.select()
    const copied = document.execCommand('copy')
    textarea.remove()
    if (!copied) return ElMessage.error('复制失败，请展开 JSON 后手动复制')
  }
  ElMessage.success(successMessage)
}

const importEmpty = (value: any) => value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
function fillMissing(current: any, incoming: any): any {
  if (importEmpty(current)) return structuredClone(incoming)
  if (Array.isArray(current) || Array.isArray(incoming)) return current
  if (current && incoming && typeof current === 'object' && typeof incoming === 'object') {
    const result = { ...current }
    Object.entries(incoming).forEach(([key, value]) => { result[key] = fillMissing(result[key], value) })
    return result
  }
  return current
}
function mergeDraftEntries(current: any[], incoming: any[], keys: string[]) {
  const result = current.map(item => structuredClone(item))
  incoming.forEach(item => {
    const match = result.find(existing => keys.every(key => !item?.[key] || existing?.[key] === item[key]))
    if (match) Object.assign(match, fillMissing(match, item)); else result.push(structuredClone(item))
  })
  return result
}
function normalizedDraft(draft: Record<string, any>) {
  return { ...draft, basic: draft.basic || {}, contact: draft.contact || {}, education: Array.isArray(draft.education) ? draft.education : [], work: Array.isArray(draft.work) ? draft.work : [], projects: Array.isArray(draft.projects) ? draft.projects : [], honors: Array.isArray(draft.honors) ? draft.honors : [], skills: Array.isArray(draft.skills) ? draft.skills : [], fieldMeta: draft.fieldMeta || {} }
}
function mergeDraftIntoProfile(current: Record<string, any>, incoming: Record<string, any>) {
  const merged = fillMissing(current, incoming)
  merged.education = mergeDraftEntries(current.education || [], incoming.education || [], ['school'])
  merged.work = mergeDraftEntries(current.work || [], incoming.work || [], ['company', 'role'])
  merged.projects = mergeDraftEntries(current.projects || [], incoming.projects || [], ['name'])
  merged.honors = mergeDraftEntries(current.honors || [], incoming.honors || [], ['name'])
  merged.skills = mergeDraftEntries(current.skills || [], incoming.skills || [], ['name'])
  merged.fieldMeta = { ...(current.fieldMeta || {}), ...(incoming.fieldMeta || {}) }
  return merged
}
async function importCurrentDraft() {
  if (!currentDraft.value) return ElMessage.warning('当前解析记录没有可导入的档案草稿')
  if (profileImportMode.value === 'merge' && !profileImportTarget.value) return ElMessage.warning('请选择目标档案')
  if (profileImportMode.value === 'new' && !newProfileName.value.trim()) return ElMessage.warning('请填写新档案名称')
  profileImporting.value = true
  try {
    const incoming = normalizedDraft(currentDraft.value)
    if (profileImportMode.value === 'merge') {
      const target = await candidateProfileApi.version(profileImportTarget.value)
      const saved = await candidateProfileApi.saveVersion(profileImportTarget.value, mergeDraftIntoProfile(target.content || {}, incoming))
      ElMessage.success(saved.revision === target.revision ? '档案内容没有变化，未创建新修订' : `已合并到“${target.name}”，生成修订 v${saved.revision}`)
    } else {
      const created = await candidateProfileApi.createVersion({ name: newProfileName.value.trim(), description: `由简历“${parsedResume.value?.fileName || ''}”解析导入`, content: incoming })
      profileImportTarget.value = created.profileId
      ElMessage.success(`已新建独立档案“${created.name}”`)
    }
    await loadProfileVersions()
  } catch (error: any) { ElMessage.error(error?.message || '导入个人档案失败') }
  finally { profileImporting.value = false }
}

function splitCategories(value?: string) {
  return value ? value.split(/[、,，]/).map(item => item.trim()).filter(Boolean) : []
}

async function remove(id: number) {
  const usage = usageOf(id)
  try {
    if (usage.bindCount > 0) {
      await ElMessageBox.confirm(
        `该简历已被 ${usage.bindCount} 条投递记录绑定。删除后，这些投递记录会显示为未绑定简历，是否继续？`,
        '删除已绑定简历',
        { confirmButtonText: '继续删除', cancelButtonText: '取消', type: 'warning' }
      )
    } else {
      await ElMessageBox.confirm('确认删除这份简历？', '删除简历', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      })
    }
  } catch {
    return
  }
  await resumeApi.remove(id)
  ElMessage.success('删除成功')
  await load()
}

function download(id: number) {
  window.open(resumeApi.downloadUrl(id), '_blank')
}

function usageOf(id: number) {
  return usageMap.value.get(id) || { resumeId: id, bindCount: 0, applications: [] }
}

function applicationLabel(item: ResumeUsageApplication) {
  return [item.companyName, item.positionName].filter(Boolean).join(' - ') || `投递 #${item.id}`
}

function openUsage(row: Resume) {
  selectedUsage.value = usageOf(row.id)
  usageResumeName.value = row.fileName
  usageVisible.value = true
}

function goApplication(id: number) {
  usageVisible.value = false
  router.push(`/application/${id}`)
}
</script>
