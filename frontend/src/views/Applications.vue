<template>
  <h1 class="page-title">投递管理</h1>
  <div class="panel">
    <div class="application-filters">
      <div class="filter-row">
        <el-input v-model="query.companyName" placeholder="搜索公司" clearable />
        <el-select v-model="query.currentStatus" placeholder="投递状态" clearable filterable allow-create default-first-option>
          <el-option v-for="item in dynamicStatusOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.positionType" placeholder="岗位类别" clearable filterable allow-create default-first-option>
          <el-option v-for="item in dynamicTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.recruitmentType" placeholder="投递批次" clearable filterable allow-create default-first-option>
          <el-option v-for="item in recruitmentTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.resumeCategory" placeholder="简历类别" clearable filterable allow-create default-first-option>
          <el-option v-for="item in dynamicResumeCategoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker v-model="dateRange" type="datetimerange" start-placeholder="投递开始" end-placeholder="投递结束" value-format="YYYY-MM-DDTHH:mm:ss" />
      </div>
      <div class="filter-actions">
        <el-segmented v-model="sortMode" :options="sortOptions" />
        <el-switch v-model="groupByCompany" active-text="按公司合并" :disabled="batchMode" />
        <div class="action-spacer"></div>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button @click="openCreate">新增岗位</el-button>
        <el-button :type="batchMode ? 'primary' : undefined" @click="batchMode ? exitBatchMode() : enterBatchMode()">
          {{ batchMode ? '退出批量管理' : '批量管理' }}
        </el-button>
      </div>
    </div>

    <div v-if="batchMode" class="batch-toolbar">
      <span class="batch-selection-count">已选 {{ selectedRows.length }} 条</span>
      <el-button :disabled="allCurrentPageSelected" @click="selectAllCurrentPage">全选当前页</el-button>
      <el-button :disabled="!selectedRows.length" @click="clearSelection">取消全选</el-button>
      <el-button type="danger" plain :disabled="!selectedRows.length" @click="batchRemove">批量删除</el-button>
      <div class="batch-toolbar-spacer"></div>
      <el-button @click="exitBatchMode">完成</el-button>
    </div>

    <el-table ref="applicationTableRef" :data="displayRows" row-key="rowKey" :tree-props="{ children: 'children' }" default-expand-all @selection-change="handleSelectionChange">
      <el-table-column v-if="batchMode" type="selection" width="48" :selectable="selectableRow" />
      <el-table-column prop="companyName" label="公司" min-width="150">
        <template #default="{ row }">
          <el-tooltip :content="row.companyName" placement="top" :disabled="!row.companyName">
            <strong class="table-ellipsis">{{ row.companyName }}</strong>
          </el-tooltip>
          <el-tag v-if="row.isGroup" size="small" class="company-count">{{ row.children.length }} 个岗位</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="positionName" label="岗位" min-width="180">
        <template #default="{ row }">
          <el-tooltip :content="row.isGroup ? '同公司投递合集' : row.positionName" placement="top">
            <span class="table-ellipsis">{{ row.isGroup ? '同公司投递合集' : row.positionName }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="positionType" label="岗位类别" min-width="180">
        <template #default="{ row }">
          <el-tooltip v-if="!row.isGroup" :content="row.positionType || '-'" placement="top" :disabled="!row.positionType">
            <div class="multi-tag-cell">
              <el-tag v-for="tag in visibleMultiTags(row.positionType)" :key="tag" class="category-tag" :style="tagStyle(tag)" size="small">{{ tag }}</el-tag>
              <el-tag v-if="hiddenMultiTagCount(row.positionType)" class="more-tag" size="small" type="info">+{{ hiddenMultiTagCount(row.positionType) }}</el-tag>
              <span v-if="!row.positionType" class="muted">-</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="recruitmentType" label="投递批次" width="110" show-overflow-tooltip />
      <el-table-column prop="resumeCategory" label="简历类别" min-width="150">
        <template #default="{ row }">
          <el-tooltip v-if="!row.isGroup" :content="row.resumeCategory || '-'" placement="top" :disabled="!row.resumeCategory">
            <div class="multi-tag-cell">
              <el-tag v-for="tag in visibleMultiTags(row.resumeCategory)" :key="tag" class="category-tag" :style="tagStyle(tag)" size="small">{{ tag }}</el-tag>
              <el-tag v-if="hiddenMultiTagCount(row.resumeCategory)" class="more-tag" size="small" type="info">+{{ hiddenMultiTagCount(row.resumeCategory) }}</el-tag>
              <span v-if="!row.resumeCategory" class="muted">-</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="状态" width="130">
        <template #default="{ row }">
          <span v-if="!row.isGroup" class="status-pill" :class="statusMeta(displayStatus(row)).className">
            <el-icon><component :is="statusMeta(displayStatus(row)).icon" /></el-icon>
            <span>{{ displayStatus(row) }}</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="appliedTime" label="投递时间" width="180">
        <template #default="{ row }">{{ row.isGroup ? '' : formatDate(row.appliedTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <template v-if="!row.isGroup">
            <el-button size="small" @click="$router.push(`/application/${row.id}`)">详情</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="load"
      @size-change="handlePageSizeChange"
    />
  </div>

  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑岗位' : '新增岗位'" width="780px">
    <el-form :model="form" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="公司名称"><el-input v-model="form.companyName" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="岗位名称"><el-input v-model="form.positionName" /></el-form-item></el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="岗位类别">
            <el-select v-model="formPositionTypes" multiple filterable allow-create default-first-option placeholder="可多选或自定义">
              <el-option v-for="item in dynamicTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="投递批次">
            <el-select v-model="form.recruitmentType" filterable allow-create default-first-option clearable>
              <el-option v-for="item in recruitmentTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="绑定简历">
            <div class="resume-bind-row">
              <el-select v-model="form.resumeId" clearable filterable placeholder="选择已上传简历" @change="handleResumeChange">
                <el-option v-for="resume in resumes" :key="resume.id" :label="resume.fileName" :value="resume.id" />
              </el-select>
              <el-upload
                :http-request="uploadAndBindResume"
                :show-file-list="false"
                accept=".pdf,.doc,.docx"
              >
                <el-button :loading="resumeUploading">上传新简历</el-button>
              </el-upload>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="简历类别">
            <el-select v-model="formResumeCategories" multiple filterable allow-create default-first-option clearable placeholder="可多选或自定义">
              <el-option v-for="item in dynamicResumeCategoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="个人档案版本">
        <el-select v-model="form.profileId" clearable filterable placeholder="选择本次投递使用的档案版本">
          <el-option v-for="item in profileVersions" :key="item.profileId" :label="item.defaultProfile ? `${item.name}（默认）` : item.name" :value="item.profileId" />
        </el-select>
        <div class="form-item-hint">与简历文件独立选择；浏览器扩展可优先使用此版本填写。</div>
      </el-form-item>
      <el-form-item label="投递简历名称">
        <div class="resume-alias-editor">
          <el-select v-model="namingTemplate" placeholder="选择命名模板" @change="regenerateResumeAlias">
            <el-option v-for="item in namingTemplates" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
<!--          <el-button @click="regenerateResumeAlias">按模板生成</el-button>-->
        </div>
        <el-input v-model="form.resumeAlias" clearable placeholder="选择简历并填写公司、岗位后自动生成，也可手动修改" @input="resumeAliasAuto = false" />
        <div class="form-item-hint">根据系统设置中的姓名、学校和毕业年份生成；仅影响本次投递的展示与下载文件名，不会复制或改动原简历。</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="工作地点"><el-input v-model="form.workLocation" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="薪资"><el-input v-model="form.salary" /></el-form-item></el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="投递来源"><el-input v-model="form.source" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="当前状态">
            <el-select v-model="form.currentStatus" filterable default-first-option>
              <el-option v-for="item in formStatusOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="岗位链接"><el-input v-model="form.jobLink" /></el-form-item>
      <el-form-item label="投递时间"><el-date-picker v-model="form.appliedTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      <el-form-item label="岗位 JD"><el-input v-model="form.jobDescription" type="textarea" :rows="5" placeholder="粘贴岗位职责、要求、技术栈等信息" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible=false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ChatDotRound, CircleCheck, Collection, EditPen, Medal, Promotion, Star, User, Warning } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi, candidateProfileApi, recruitmentTypeOptions, resumeApi, resumeCategoryOptions, statusOptions, storageApi, typeOptions } from '../api'
import type { CandidateProfileSummary, JobApplication, Resume } from '../types'
import { formatDateTime } from '../utils/time'
import { parseSavedResumeNamingTemplates, renderResumeName, resumeNamingPresets, templateForSelection } from '../utils/resumeNaming'

type ApplicationRow = JobApplication & { rowKey?: string; isGroup?: boolean; children?: ApplicationRow[] }

const rows = ref<ApplicationRow[]>([])
const resumes = ref<Resume[]>([])
const profileVersions = ref<CandidateProfileSummary[]>([])
const dynamicStatusOptions = ref<string[]>([...statusOptions])
const dynamicTypeOptions = ref<string[]>([...typeOptions])
const dynamicResumeCategoryOptions = ref<string[]>([...resumeCategoryOptions])
const total = ref(0)
const dialogVisible = ref(false)
const resumeUploading = ref(false)
const groupByCompany = ref(false)
const dateRange = ref<string[]>([])
const sortMode = ref('appliedTime-desc')
const batchMode = ref(false)
const selectedRows = ref<ApplicationRow[]>([])
const applicationTableRef = ref<any>()
const formPositionTypes = ref<string[]>([])
const formResumeCategories = ref<string[]>([])
const namingTemplate = ref('company')
const resumeAliasAuto = ref(true)
const namingSettings = reactive({ resumeOwnerName: '', resumeOwnerSchool: '', resumeGraduationYear: '', resumeCustomNamingTemplate: '', resumeCustomNamingTemplates: '' })
const namingTemplates = computed(() => [
  ...resumeNamingPresets,
  ...parseSavedResumeNamingTemplates(namingSettings.resumeCustomNamingTemplates, namingSettings.resumeCustomNamingTemplate)
    .map((item) => ({ value: `custom:${item.id}`, label: `自定义 · ${item.name}` }))
])
const sortOptions = [
  { label: '投递时间↓', value: 'appliedTime-desc' },
  { label: '投递时间↑', value: 'appliedTime-asc' },
  { label: '公司名 A-Z', value: 'companyName-asc' },
  { label: '公司名 Z-A', value: 'companyName-desc' }
]
const query = reactive({
  companyName: '',
  currentStatus: '',
  positionType: '',
  recruitmentType: '',
  resumeCategory: '',
  sortField: 'appliedTime',
  sortOrder: 'desc',
  pageNo: 1,
  pageSize: 10
})
const form = reactive<JobApplication>(emptyForm())
const formStatusOptions = computed(() => {
  const flowOptions = parseProgressFlow(form.progressFlow)
  return flowOptions.length ? flowOptions : statusOptions
})

const displayRows = computed<ApplicationRow[]>(() => {
  if (!groupByCompany.value) {
    return rows.value.map(row => ({ ...row, rowKey: `job-${row.id}` }))
  }
  const groups = new Map<string, ApplicationRow[]>()
  rows.value.forEach(row => {
    const key = row.companyName || '未填写公司'
    groups.set(key, [...(groups.get(key) || []), { ...row, rowKey: `job-${row.id}` }])
  })
  return Array.from(groups.entries()).map(([companyName, children]) => ({
    rowKey: `company-${companyName}`,
    isGroup: true,
    companyName,
    positionName: '',
    currentStatus: '',
    children
  } as ApplicationRow))
})

watch(sortMode, value => {
  const [field, order] = value.split('-')
  query.sortField = field
  query.sortOrder = order
  query.pageNo = 1
  load()
})

watch(groupByCompany, () => {
  query.pageNo = 1
})

onMounted(async () => {
  await Promise.all([load(), loadResumes(), loadProfileVersions(), loadFilterOptions(), loadNamingSettings()])
})

watch(() => [
  form.companyName,
  form.positionName,
  form.recruitmentType,
  form.workLocation,
  formPositionTypes.value.join('、'),
  formResumeCategories.value.join('、'),
  namingTemplate.value
], () => {
  if (dialogVisible.value && resumeAliasAuto.value) regenerateResumeAlias()
})

function emptyForm(): JobApplication {
  return {
    companyName: '',
    positionName: '',
    currentStatus: '待投递',
    recruitmentType: '秋招'
  }
}

function statusMeta(status?: string) {
  const map: Record<string, any> = {
    收藏: { className: 'status-favorite', icon: Star },
    待投递: { className: 'status-pending', icon: Collection },
    已投递: { className: 'status-submitted', icon: Promotion },
    笔试: { className: 'status-written', icon: EditPen },
    一面: { className: 'status-interview', icon: ChatDotRound },
    二面: { className: 'status-interview', icon: ChatDotRound },
    三面: { className: 'status-interview', icon: ChatDotRound },
    四面: { className: 'status-interview', icon: ChatDotRound },
    主管面: { className: 'status-interview', icon: User },
    'HR 面': { className: 'status-interview', icon: User },
    面试中: { className: 'status-interview', icon: ChatDotRound },
    Offer: { className: 'status-offer', icon: Medal },
    淘汰: { className: 'status-rejected', icon: Warning }
  }
  return map[status || ''] || { className: 'status-default', icon: CircleCheck }
}

function formatDate(value?: string) {
  return formatDateTime(value)
}

function queryParams() {
  return {
    ...query,
    appliedStartTime: dateRange.value?.[0] || '',
    appliedEndTime: dateRange.value?.[1] || ''
  }
}

async function load() {
  const page: any = await applicationApi.page(queryParams())
  rows.value = page.records
  total.value = page.total
  clearSelection()
}

async function loadResumes() {
  resumes.value = await resumeApi.list() as unknown as Resume[]
}

async function loadProfileVersions() {
  profileVersions.value = await candidateProfileApi.versions()
}

async function loadNamingSettings() {
  const settings = await storageApi.get() as any
  namingSettings.resumeOwnerName = settings.resumeOwnerName || ''
  namingSettings.resumeOwnerSchool = settings.resumeOwnerSchool || ''
  namingSettings.resumeGraduationYear = settings.resumeGraduationYear || ''
  namingSettings.resumeCustomNamingTemplate = settings.resumeCustomNamingTemplate || ''
  namingSettings.resumeCustomNamingTemplates = settings.resumeCustomNamingTemplates || ''
  const requestedTemplate = settings.resumeNamingTemplate || 'general'
  namingTemplate.value = namingTemplates.value.some((item) => item.value === requestedTemplate) ? requestedTemplate : 'general'
}

async function uploadAndBindResume(option: any) {
  resumeUploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', option.file)
    const resume = await resumeApi.upload(fd) as unknown as Resume
    await loadResumes()
    form.resumeId = resume.id
    resumeAliasAuto.value = true
    regenerateResumeAlias()
    ElMessage.success('简历已上传并自动绑定')
  } finally {
    resumeUploading.value = false
  }
}

async function loadStatusOptions() {
  const options = await applicationApi.statusOptions() as unknown as string[]
  dynamicStatusOptions.value = mergeOptions(statusOptions, options)
}

async function loadFilterOptions() {
  const [statuses, positionTypes, resumeCategories] = await Promise.all([
    applicationApi.statusOptions(),
    applicationApi.positionTypeOptions(),
    applicationApi.resumeCategoryOptions()
  ])
  dynamicStatusOptions.value = mergeOptions(statusOptions, statuses as unknown as string[])
  dynamicTypeOptions.value = mergeOptions(typeOptions, positionTypes as unknown as string[])
  dynamicResumeCategoryOptions.value = mergeOptions(resumeCategoryOptions, resumeCategories as unknown as string[])
}

function mergeOptions(...groups: string[][]) {
  return Array.from(new Set(groups.flat().map((item) => item?.trim()).filter(Boolean)))
}

function resetQuery() {
  Object.assign(query, {
    companyName: '',
    currentStatus: '',
    positionType: '',
    recruitmentType: '',
    resumeCategory: '',
    sortField: 'appliedTime',
    sortOrder: 'desc',
    pageNo: 1
  })
  sortMode.value = 'appliedTime-desc'
  dateRange.value = []
  load()
}

function selectableRow(row: ApplicationRow) {
  return !row.isGroup
}

function handleSelectionChange(selection: ApplicationRow[]) {
  selectedRows.value = selection.filter((row) => !row.isGroup)
}

function clearSelection() {
  selectedRows.value = []
  applicationTableRef.value?.clearSelection()
}

const allCurrentPageSelected = computed(() => {
  const currentRows = displayRows.value.filter((row) => !row.isGroup)
  return currentRows.length > 0 && selectedRows.value.length === currentRows.length
})

function enterBatchMode() {
  groupByCompany.value = false
  query.pageNo = 1
  batchMode.value = true
  clearSelection()
  void load()
}

function exitBatchMode() {
  clearSelection()
  batchMode.value = false
}

function selectAllCurrentPage() {
  const table = applicationTableRef.value
  if (!table) return
  table.clearSelection()
  displayRows.value
    .filter((row) => !row.isGroup)
    .forEach((row) => table.toggleRowSelection(row, true))
}

function handlePageSizeChange() {
  query.pageNo = 1
  load()
}

function selectedIds() {
  return selectedRows.value.map((row) => row.id).filter((id): id is number => typeof id === 'number')
}

async function batchRemove() {
  const ids = selectedIds()
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条投递记录吗？此操作无法恢复。`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  await applicationApi.batchRemove(ids)
  ElMessage.success('投递记录已批量删除')
  if (rows.value.length === ids.length && query.pageNo > 1) query.pageNo -= 1
  await Promise.all([load(), loadFilterOptions()])
}

function splitMultiValue(value?: string) {
  return value ? value.split(/[、,，;；]/).map(item => item.trim()).filter(Boolean) : []
}

function visibleMultiTags(value?: string) {
  return splitMultiValue(value).slice(0, 2)
}

function hiddenMultiTagCount(value?: string) {
  return Math.max(splitMultiValue(value).length - 2, 0)
}

function tagStyle(value: string) {
  const palettes = [
    { color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
    { color: '#047857', bg: '#ecfdf5', border: '#a7f3d0' },
    { color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
    { color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe' },
    { color: '#be123c', bg: '#fff1f2', border: '#fecdd3' },
    { color: '#0e7490', bg: '#ecfeff', border: '#a5f3fc' }
  ]
  const hash = Array.from(value).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  const item = palettes[hash % palettes.length]
  return { color: item.color, backgroundColor: item.bg, borderColor: item.border }
}

type ProgressStep = { name: string; result?: string; operatedTime?: string }

function parseProgressSteps(value?: string): ProgressStep[] {
  if (!value?.trim()) return []
  const raw = value.trim()
  if (raw.startsWith('[')) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => ({
            name: String(item?.name || '').trim(),
            result: String(item?.result || '').trim() || undefined,
            operatedTime: String(item?.operatedTime || '').trim() || undefined
          }))
          .filter((item) => item.name)
      }
    } catch {
      // 兼容旧文本格式，失败后继续按行解析。
    }
  }
  return raw.split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => ({ name: line.split('|')[0].trim() }))
    .filter((item) => item.name)
}

function parseProgressFlow(value?: string) {
  return parseProgressSteps(value).map((item) => item.name)
}

function statusFromProgressJson(value?: string) {
  const steps = parseProgressSteps(value)
  if (!steps.length) return ''
  return [...steps].reverse().find((item) => item.operatedTime || item.result)?.name || steps[0].name
}

function normalizedCurrentStatus(row: JobApplication) {
  const value = row.currentStatus || ''
  if (!value.trim().startsWith('[')) return value
  return statusFromProgressJson(value) || statusFromProgressJson(row.progressFlow) || '待投递'
}

function displayStatus(row: JobApplication) {
  return normalizedCurrentStatus(row) || '待投递'
}

function openCreate() {
  Object.assign(form, emptyForm(), {
    id: undefined,
    positionType: '',
    resumeCategory: '',
    workLocation: '',
    salary: '',
    source: '',
    jobLink: '',
    jobDescription: '',
    appliedTime: '',
    resumeId: undefined,
    profileId: undefined,
    resumeAlias: '',
    remark: ''
  })
  formPositionTypes.value = []
  formResumeCategories.value = []
  resumeAliasAuto.value = true
  dialogVisible.value = true
}

function openEdit(row: JobApplication) {
  Object.assign(form, row)
  form.currentStatus = normalizedCurrentStatus(row)
  const options = parseProgressFlow(row.progressFlow)
  if (options.length && !options.includes(form.currentStatus)) {
    form.currentStatus = statusFromProgressJson(row.progressFlow) || options[0]
  }
  formPositionTypes.value = splitMultiValue(row.positionType)
  formResumeCategories.value = splitMultiValue(row.resumeCategory)
  resumeAliasAuto.value = false
  dialogVisible.value = true
}

function handleResumeChange(resumeId?: number) {
  resumeAliasAuto.value = true
  if (!resumeId) {
    form.resumeAlias = ''
    return
  }
  regenerateResumeAlias()
}

function regenerateResumeAlias() {
  const resume = resumes.value.find((item) => item.id === form.resumeId)
  const extension = fileExtension(resume?.fileName)
  const customTemplates = parseSavedResumeNamingTemplates(namingSettings.resumeCustomNamingTemplates, namingSettings.resumeCustomNamingTemplate)
  const template = templateForSelection(namingTemplate.value, customTemplates)
  const name = renderResumeName(template, {
    ownerName: namingSettings.resumeOwnerName,
    ownerSchool: namingSettings.resumeOwnerSchool,
    graduationYear: namingSettings.resumeGraduationYear,
    companyName: form.companyName,
    positionName: form.positionName,
    positionType: formPositionTypes.value.join('、'),
    recruitmentType: form.recruitmentType,
    resumeCategory: formResumeCategories.value.join('、'),
    workLocation: form.workLocation
  })
  form.resumeAlias = name ? `${name}${extension}` : (resume?.fileName || '')
  resumeAliasAuto.value = true
}

function fileExtension(fileName?: string) {
  const index = fileName?.lastIndexOf('.') ?? -1
  return index >= 0 && fileName ? fileName.slice(index) : ''
}

function profileVersionName(profileId?: string) {
  if (!profileId) return '未关联'
  const item = profileVersions.value.find(profile => profile.profileId === profileId)
  return item ? (item.defaultProfile ? `${item.name}（默认）` : item.name) : '档案版本已不存在'
}

async function save() {
  form.positionType = formPositionTypes.value.join('、')
  form.resumeCategory = formResumeCategories.value.join('、')
  if (!formStatusOptions.value.includes(form.currentStatus)) {
    form.currentStatus = formStatusOptions.value[0] || '待投递'
  }
  form.id ? await applicationApi.update(form.id, form) : await applicationApi.create(form)
  dialogVisible.value = false
  await Promise.all([load(), loadFilterOptions()])
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('确认删除这条投递记录？', '删除投递记录', { type: 'warning' })
  } catch {
    return
  }
  await applicationApi.remove(id)
  await load()
}
</script>
