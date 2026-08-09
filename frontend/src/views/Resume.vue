<template>
  <h1 class="page-title">简历管理</h1>

  <div class="panel">
    <div class="toolbar">
      <el-button type="primary" @click="openUpload">上传简历</el-button>
      <el-button @click="$router.push('/settings')">保存位置设置</el-button>
    </div>

    <el-table :data="rows" class="resume-table">
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
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <div class="resume-row-actions">
            <el-button size="small" :disabled="row.fileType !== 'pdf'" @click="preview(row)">预览</el-button>
            <el-button size="small" @click="download(row.id)">下载</el-button>
            <el-button size="small" :loading="parsingId === row.id" @click="parse(row)">解析</el-button>
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
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resumeApi, resumeCategoryOptions } from '../api'
import type { Resume, ResumeUsage, ResumeUsageApplication } from '../types'

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
  parsingId.value = row.id
  try {
    const result = await resumeApi.parse(row.id) as any
    ElMessage.success(result.status === 'VISION_RECOMMENDED' ? '文本提取较少，后续可启用视觉解析' : '已完成本地文本提取')
  } finally { parsingId.value = null }
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
