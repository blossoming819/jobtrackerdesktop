<template>
  <h1 class="page-title">{{ application.companyName }} - {{ application.positionName }}</h1>

  <div class="panel">
    <div class="application-summary-grid">
      <div class="summary-label">当前状态</div>
      <div class="summary-value">
        <span class="status-pill" :class="statusMeta(application.currentStatus).className">
          <el-icon><component :is="statusMeta(application.currentStatus).icon" /></el-icon>
          <span>{{ application.currentStatus || '-' }}</span>
        </span>
      </div>
      <div class="summary-label">进度结果</div>
      <div class="summary-value">
        <el-tag v-if="isTerminalStatus(application.currentStatus)" :type="terminalMeta(application.currentStatus).type">
          {{ terminalMeta(application.currentStatus).label }}
        </el-tag>
        <el-tag v-else :type="resultMeta(application.progressResult).type">{{ application.progressResult || '进行中' }}</el-tag>
      </div>
      <div class="summary-label">操作时间</div>
      <div class="summary-value summary-value-last">{{ formatDate(application.progressOperatedTime) || '-' }}</div>

      <div class="summary-label">岗位类别</div>
      <div class="summary-value">{{ application.positionType || '-' }}</div>
      <div class="summary-label">投递批次</div>
      <div class="summary-value">{{ application.recruitmentType || '-' }}</div>
      <div class="summary-label">简历类别</div>
      <div class="summary-value summary-value-last">{{ application.resumeCategory || '-' }}</div>

      <div class="summary-label">关联档案</div>
      <div class="summary-value">
        <div class="bound-resume-info">
          <span class="bound-resume-name" :title="boundProfileName">{{ boundProfileName }}</span>
          <el-button v-if="application.profileId" class="bound-resume-preview" size="small" link type="primary" @click="previewProfile">预览</el-button>
        </div>
      </div>
      <div class="summary-label">投递简历名称</div>
      <div class="summary-value">
        <span class="application-resume-alias" :title="application.resumeAlias || detail.resume?.fileName || '未设置'">
          {{ application.resumeAlias || detail.resume?.fileName || '未设置' }}
        </span>
      </div>
      <div class="summary-label">绑定简历</div>
      <div class="summary-value summary-value-last">
        <div class="bound-resume-info">
          <span class="bound-resume-name" :title="detail.resume?.fileName || '未绑定'">
            {{ detail.resume?.fileName || '未绑定' }}
          </span>
          <el-button
            v-if="detail.resume?.fileType === 'pdf'"
            class="bound-resume-preview"
            size="small"
            link
            type="primary"
            @click="previewResume"
          >
            预览
          </el-button>
          <el-button
            v-if="detail.resume?.id"
            class="bound-resume-preview"
            size="small"
            link
            type="primary"
            @click="downloadResumeForApplication"
          >
            下载
          </el-button>
        </div>
      </div>
      <div class="summary-label">地点</div>
      <div class="summary-value">{{ application.workLocation || '-' }}</div>

      <div class="summary-label">来源</div>
      <div class="summary-value">{{ application.source || '-' }}</div>
      <div class="summary-label">薪资</div>
      <div class="summary-value summary-value-last">{{ application.salary || '-' }}</div>
      <div class="summary-label">投递时间</div>
      <div class="summary-value summary-value-wide">{{ formatDate(application.appliedTime) || '-' }}</div>

      <div class="summary-label">链接</div>
      <div class="summary-value summary-value-wide">
        <el-tooltip v-if="application.jobLink" :content="application.jobLink" placement="top-start" :show-after="300">
          <a class="application-link" :href="application.jobLink" target="_blank" rel="noopener noreferrer">{{ application.jobLink }}</a>
        </el-tooltip>
        <span v-else>-</span>
      </div>
      <div class="summary-label">备注</div>
      <div class="summary-value summary-value-wide">{{ application.remark || '-' }}</div>
    </div>
  </div>

  <div class="panel detail-section">
    <div class="section-heading section-heading-toggle" @click="toggleSection('progress')">
      <div>
        <h3>投递进度</h3>
        <span>查看当前岗位的流程节点、结果和操作时间。</span>
      </div>
      <div class="section-actions" @click.stop>
        <el-button text type="primary" @click="openProgressDialog">编辑流程</el-button>
        <el-button text class="collapse-toggle" @click="toggleSection('progress')">
          {{ sectionOpen.progress ? '收起' : '展开' }}
        </el-button>
      </div>
    </div>

    <el-timeline v-show="sectionOpen.progress" class="status-timeline compact-timeline">
      <el-timeline-item
        v-for="item in progressItems"
        :key="item.name"
        :timestamp="item.timestamp"
        :color="item.color"
        :icon="item.icon"
        :hollow="!item.active"
      >
        <div
          class="timeline-row timeline-clickable"
          :class="{ inactive: !item.active, selected: item.name === selectedProgressName }"
          @click="openProgressManager(item.name)"
        >
          <div class="timeline-title">
            <strong>{{ item.name }}</strong>
            <el-tag v-if="item.terminalLabel" size="small" :type="item.terminalType">
              {{ item.terminalLabel }}
            </el-tag>
            <el-tag v-else-if="item.result" size="small" :type="resultMeta(item.result).type">
              {{ item.result }}
            </el-tag>
          </div>
          <span>{{ item.description }}</span>
          <span v-if="item.operatedTime" class="muted">
            操作时间：{{ formatDate(item.operatedTime) }}
          </span>
          <span v-else-if="item.active" class="muted">
            操作时间：未记录
          </span>
          <div v-if="item.name === selectedProgressName" class="progress-manager inline-progress-manager" @click.stop>
            <el-select v-model="progressForm.currentStatus" filterable placeholder="当前进度">
              <el-option
                v-for="option in selectableProgressSteps"
                :key="option.name"
                :label="option.disabled ? `${option.name}（由失败自动产生）` : option.name"
                :value="option.name"
                :disabled="option.disabled"
              />
            </el-select>
            <el-segmented
              v-if="!isTerminalStatus(progressForm.currentStatus)"
              v-model="progressForm.progressResult"
              :options="progressResultOptions"
            />
            <el-tag v-else :type="terminalMeta(progressForm.currentStatus).type" effect="light">
              {{ terminalMeta(progressForm.currentStatus).label }}
            </el-tag>
            <el-date-picker
              v-model="progressForm.progressOperatedTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="操作时间"
            />
            <el-button type="primary" @click="saveCurrentProgress">保存进度</el-button>
            <el-button @click="closeProgressManager">取消</el-button>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>

  <div class="panel detail-section" v-if="application.jobDescription">
    <div class="section-heading section-heading-toggle" @click="toggleSection('jd')">
      <div>
        <h3>岗位 JD</h3>
        <span>岗位职责、任职要求和加分项。</span>
      </div>
      <el-button text class="collapse-toggle" @click.stop="toggleSection('jd')">
        {{ sectionOpen.jd ? '收起' : '展开' }}
      </el-button>
    </div>
    <div v-show="sectionOpen.jd" class="jd-content markdown-preview" v-html="jobDescriptionHtml"></div>
  </div>

  <div class="panel detail-section">
    <div class="section-heading section-heading-toggle" @click="toggleSection('schedules')">
      <div>
        <h3>关联日程</h3>
        <span>展示和当前投递记录关联的面试、笔试、复盘或截止事项。</span>
      </div>
      <div class="section-actions" @click.stop>
        <el-button text type="primary" @click="goRelatedSchedules">查看日程</el-button>
        <el-button text class="collapse-toggle" @click="toggleSection('schedules')">
          {{ sectionOpen.schedules ? '收起' : '展开' }}
        </el-button>
      </div>
    </div>
    <div v-show="sectionOpen.schedules" v-if="detail.relatedSchedules?.length" class="application-schedule-list">
      <button
        v-for="item in detail.relatedSchedules"
        :key="item.id"
        class="application-schedule-item"
        :class="scheduleClass(item)"
        @click="goRelatedSchedules"
      >
        <span>{{ scheduleTimeLabel(item) }}</span>
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.scheduleType || '自定义' }} · {{ item.priority || '中' }} · {{ item.importance || '普通' }} · {{ normalizeScheduleStatus(item.status) }}</p>
        </div>
      </button>
    </div>
    <div v-show="sectionOpen.schedules" v-else class="empty-state application-schedule-empty">
      暂无关联日程
    </div>
  </div>

  <div class="panel detail-section">
    <div class="section-heading section-heading-toggle" @click="toggleSection('interviews')">
      <div>
        <h3>面试流程</h3>
        <span>记录面试轮次、问题、难度和总结。</span>
      </div>
      <div class="section-actions" @click.stop>
        <el-button text type="primary" @click="openInterview">新增面试记录</el-button>
        <el-button text class="collapse-toggle" @click="toggleSection('interviews')">
          {{ sectionOpen.interviews ? '收起' : '展开' }}
        </el-button>
      </div>
    </div>
    <el-timeline v-show="sectionOpen.interviews">
      <el-timeline-item
        v-for="item in detail.interviewRecords"
        :key="item.id"
        :timestamp="formatDate(item.interviewTime)"
        color="#409eff"
        :icon="ChatDotRound"
      >
        <strong>{{ item.round }}</strong> - {{ item.result || '待记录' }} - 难度 {{ item.difficulty || '-' }}/5
        <p class="muted">{{ item.questions }}</p>
        <p>{{ item.summary }}</p>
        <el-button size="small" @click="editInterview(item)">编辑</el-button>
        <el-button size="small" type="danger" @click="removeInterview(item.id!)">删除</el-button>
      </el-timeline-item>
    </el-timeline>
  </div>

  <div class="panel detail-section">
    <div class="section-heading section-heading-toggle" @click="toggleSection('notes')">
      <div>
        <h3>Markdown 面试笔记</h3>
        <span>沉淀面试复盘、题目和后续准备。</span>
      </div>
      <div class="section-actions" @click.stop>
        <el-button text type="primary" @click="openNote">新增笔记</el-button>
        <el-button text class="collapse-toggle" @click="toggleSection('notes')">
          {{ sectionOpen.notes ? '收起' : '展开' }}
        </el-button>
      </div>
    </div>
    <el-table v-show="sectionOpen.notes" :data="detail.interviewNotes">
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
      <el-table-column prop="updatedTime" label="更新时间" width="180">
        <template #default="{ row }">{{ formatDate(row.updatedTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button size="small" @click="previewNote(row)">预览</el-button>
          <el-button size="small" @click="editNote(row)">编辑</el-button>
          <el-button size="small" @click="download(noteApi.downloadUrl(row.id))">下载</el-button>
          <el-button size="small" type="danger" @click="removeNote(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="progressVisible" title="自定义投递流程" width="680px">
    <p class="muted">每行一个阶段，格式为：阶段名称 | 阶段说明。流程会同步为当前岗位可选状态。</p>
    <el-input v-model="progressText" type="textarea" :rows="12" />
    <el-alert
      v-if="progressMismatch"
      class="progress-alert"
      type="warning"
      :closable="false"
      show-icon
      title="当前状态不在新流程中"
      description="保存前请选择一个流程阶段同步为当前状态，避免详情状态和进度条点亮位置不一致。"
    />
    <el-form label-width="120px" class="progress-sync-form">
      <el-form-item label="当前状态">
        <el-select v-model="progressStatusAfterSave" filterable placeholder="选择流程中的阶段">
          <el-option v-for="item in progressDialogSteps" :key="item.name" :label="item.name" :value="item.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="resetProgressFlow">恢复默认</el-button>
      <el-button @click="progressVisible = false">取消</el-button>
      <el-button type="primary" @click="saveProgressFlow">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="interviewVisible" title="面试记录" width="680px">
    <el-form :model="interviewForm" label-width="100px">
      <el-form-item label="面试轮次"><el-input v-model="interviewForm.round" placeholder="一面 / 二面 / 主管面 / HR 面" /></el-form-item>
      <el-form-item label="面试时间"><el-date-picker v-model="interviewForm.interviewTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      <el-form-item label="面试官"><el-input v-model="interviewForm.interviewer" /></el-form-item>
      <el-form-item label="面试问题"><el-input v-model="interviewForm.questions" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="面试结果"><el-input v-model="interviewForm.result" /></el-form-item>
      <el-form-item label="难度评价"><el-rate v-model="interviewForm.difficulty" /></el-form-item>
      <el-form-item label="总结备注"><el-input v-model="interviewForm.summary" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="interviewVisible = false">取消</el-button>
      <el-button type="primary" @click="saveInterview">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="noteVisible" title="Markdown 笔记" width="90%">
    <el-input v-model="noteForm.title" placeholder="笔记标题" style="margin-bottom: 12px" />
    <div class="markdown-layout">
      <el-input v-model="noteForm.content" type="textarea" :rows="18" />
      <div class="markdown-preview" v-html="preview"></div>
    </div>
    <template #footer>
      <el-button @click="noteVisible = false">取消</el-button>
      <el-button type="primary" @click="saveNote">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="notePreviewVisible" :title="notePreviewTitle" width="86%" top="4vh" destroy-on-close>
    <div class="markdown-preview note-dialog-preview" v-html="notePreviewHtml"></div>
  </el-dialog>

  <el-dialog v-model="resumePreviewVisible" :title="detail.resume?.fileName || 'PDF 预览'" width="86%" top="4vh" destroy-on-close>
    <iframe v-if="resumePreviewUrl" class="pdf-preview" :src="resumePreviewUrl"></iframe>
  </el-dialog>

  <el-dialog v-model="profilePreviewVisible" :title="`档案预览 · ${profilePreview?.name || boundProfileName}`" width="min(900px, 92vw)" top="5vh" class="profile-preview-dialog" destroy-on-close>
    <el-scrollbar max-height="72vh">
      <p v-if="profilePreview?.description" class="profile-preview-description">{{ profilePreview.description }}</p>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="姓名">{{ profileContent.basic?.nameCn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="英文姓名">{{ profileContent.basic?.nameEn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ displayGender(profileContent.basic?.gender) }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ profileContent.basic?.birthDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前城市">{{ profileContent.contact?.currentCity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ profileContent.contact?.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profileContent.contact?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="微信">{{ profileContent.contact?.wechat || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修订版本">v{{ profilePreview?.revision || 0 }}</el-descriptions-item>
      </el-descriptions>
      <section v-for="section in profilePreviewSections" :key="section.key" class="profile-preview-section">
        <h3>{{ section.label }}（{{ section.items.length }}）</h3>
        <el-empty v-if="!section.items.length" description="暂无内容" :image-size="42" />
        <div v-else class="profile-preview-items">
          <article v-for="(item, index) in section.items" :key="index" class="profile-preview-item">
            <strong>{{ previewItemTitle(section.key, item, index) }}</strong>
            <div class="profile-preview-field-grid">
              <div v-for="field in previewItemFields(section.key, item)" :key="field.label" :class="{ wide: field.wide }">
                <span>{{ field.label }}</span><p>{{ field.value || '未填写' }}</p>
              </div>
            </div>
          </article>
        </div>
      </section>
    </el-scrollbar>
    <template #footer><el-button @click="profilePreviewVisible = false">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Collection, EditPen, Files, Medal, Promotion, Star, User, Warning } from '@element-plus/icons-vue'
import { applicationApi, candidateProfileApi, interviewApi, noteApi, resumeApi } from '../api'
import type { CandidateProfileResponse, CandidateProfileSummary, InterviewNote, InterviewRecord, JobApplication, Reminder } from '../types'
import { formatDateTime } from '../utils/time'

type ProgressStep = { name: string; description: string; result?: string; operatedTime?: string }

const route = useRoute()
const router = useRouter()
const jobId = Number(route.params.id)
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const detail = ref<any>({ application: {}, resume: null, interviewRecords: [], interviewNotes: [] })
const application = computed<JobApplication>(() => detail.value.application || {})
const profileVersions = ref<CandidateProfileSummary[]>([])
const profilePreviewVisible = ref(false)
const profilePreview = ref<CandidateProfileResponse | null>(null)
const profileContent = computed<any>(() => profilePreview.value?.content || {})
const profilePreviewSections = computed(() => [
  { key: 'education', label: '教育经历', items: profileContent.value.education || [] },
  { key: 'work', label: '实习 / 工作经历', items: profileContent.value.work || [] },
  { key: 'projects', label: '项目经历', items: profileContent.value.projects || [] },
  { key: 'honors', label: '荣誉与奖项', items: profileContent.value.honors || [] },
])
const boundProfileName = computed(() => {
  if (!application.value.profileId) return '未关联'
  const profile = profileVersions.value.find(item => item.profileId === application.value.profileId)
  return profile ? (profile.defaultProfile ? `${profile.name}（默认）` : profile.name) : '档案版本已不存在'
})
const sectionOpen = reactive({
  progress: true,
  jd: false,
  schedules: true,
  interviews: false,
  notes: false
})
const interviewVisible = ref(false)
const noteVisible = ref(false)
const notePreviewVisible = ref(false)
const progressVisible = ref(false)
const progressText = ref('')
const progressStatusAfterSave = ref('')
const selectedProgressName = ref('')
const resumePreviewVisible = ref(false)
const resumePreviewUrl = ref('')
const notePreviewTitle = ref('')
const notePreviewContent = ref('')
const interviewForm = reactive<InterviewRecord>({ jobId, round: '' })
const noteForm = reactive<InterviewNote>({
  jobId,
  title: '',
  content: '# 面试复盘\n\n## 面试问题\n\n## 自我评价\n\n## 后续准备\n'
})
const progressForm = reactive({
  currentStatus: '',
  progressResult: '进行中',
  progressOperatedTime: ''
})
const progressResultOptions = ['进行中', '成功', '失败']
const terminalStatuses = ['Offer', '淘汰']
const preview = computed(() => md.render(noteForm.content || ''))
const notePreviewHtml = computed(() => md.render(notePreviewContent.value || ''))
const jobDescriptionHtml = computed(() => md.render(application.value.jobDescription || ''))

const defaultProgress: ProgressStep[] = [
  { name: '收藏', description: '值得关注，后续可安排投递' },
  { name: '待投递', description: '信息已记录，等待投递' },
  { name: '已投递', description: '简历已投递，等待反馈' },
  { name: '笔试', description: '进入笔试或测评阶段' },
  { name: '面试中', description: '正在推进面试流程' },
  { name: '一面', description: '第一轮面试' },
  { name: '二面', description: '第二轮面试' },
  { name: '三面', description: '第三轮面试' },
  { name: '四面', description: '第四轮面试' },
  { name: '主管面', description: '主管或业务负责人面试' },
  { name: 'HR 面', description: 'HR 沟通或终面' },
  { name: 'Offer', description: '已获得 Offer' },
  { name: '淘汰', description: '流程结束，已淘汰或主动放弃' }
]

const statusConfig: Record<string, any> = {
  收藏: { color: '#f59e0b', icon: Star, className: 'status-favorite' },
  待投递: { color: '#667085', icon: Collection, className: 'status-pending' },
  已投递: { color: '#409eff', icon: Promotion, className: 'status-submitted' },
  笔试: { color: '#7c3aed', icon: EditPen, className: 'status-written' },
  一面: { color: '#0e7490', icon: ChatDotRound, className: 'status-interview' },
  二面: { color: '#0e7490', icon: ChatDotRound, className: 'status-interview' },
  三面: { color: '#0e7490', icon: ChatDotRound, className: 'status-interview' },
  四面: { color: '#0e7490', icon: ChatDotRound, className: 'status-interview' },
  主管面: { color: '#0e7490', icon: User, className: 'status-interview' },
  'HR 面': { color: '#0e7490', icon: User, className: 'status-interview' },
  面试中: { color: '#06b6d4', icon: ChatDotRound, className: 'status-interview' },
  Offer: { color: '#16a34a', icon: Medal, className: 'status-offer' },
  淘汰: { color: '#dc2626', icon: Warning, className: 'status-rejected' }
}

const flowSteps = computed(() => parseProgressFlow(application.value.progressFlow))
const selectableProgressSteps = computed(() => flowSteps.value.map((item) => ({
  ...item,
  disabled: item.name === '淘汰'
})))
const progressDialogSteps = computed(() => parseProgressFlow(progressText.value))
const progressMismatch = computed(() => {
  const current = application.value.currentStatus
  return Boolean(current && progressDialogSteps.value.length && !progressDialogSteps.value.some((item) => item.name === current))
})
const progressItems = computed(() => {
  const current = application.value.currentStatus || '待投递'
  const currentIndex = flowSteps.value.findIndex((item) => item.name === current)
  const failedIndex = findFailedStepIndex(flowSteps.value)
  const activeEndIndex = current === '淘汰' ? failedIndex : current === 'Offer' ? findLastRecordedNormalIndex(flowSteps.value) : currentIndex
  return flowSteps.value.map((item, index) => {
    const meta = statusMeta(item.name)
    const isCurrent = item.name === current
    const isReached = activeEndIndex >= 0 && index <= activeEndIndex && !isTerminalStatus(item.name)
    // A historical timestamp (such as the filled-in application time) must not make
    // later steps look completed when the current status has been moved backward.
    const operatedTime = isReached || isCurrent
      ? item.operatedTime || rawTimestampFor(item.name) || (isCurrent ? application.value.progressOperatedTime : '')
      : ''
    const hasRecordedTime = Boolean(operatedTime)
    const result = isTerminalStatus(item.name) || (!isReached && !isCurrent)
      ? ''
      : item.result || (isCurrent ? application.value.progressResult || '进行中' : '')
    const active = isCurrent || hasRecordedTime || isReached
    const terminal = isTerminalStatus(item.name) && (isCurrent || hasRecordedTime)
    return {
      ...item,
      result,
      operatedTime,
      terminalLabel: terminal ? terminalMeta(item.name).label : '',
      terminalType: terminal ? terminalMeta(item.name).type : '',
      active,
      current: isCurrent,
      color: timelineColor(active, meta.color, result, item.name),
      icon: meta.icon,
      timestamp: formatDate(operatedTime)
    }
  })
})

onMounted(load)

watch(progressText, () => {
  const steps = parseProgressFlow(progressText.value)
  if (!steps.length) {
    progressStatusAfterSave.value = ''
    return
  }
  if (!steps.some((item) => item.name === progressStatusAfterSave.value)) {
    progressStatusAfterSave.value = steps[0].name
  }
})

async function load() {
  const [applicationDetail, versions] = await Promise.all([applicationApi.detail(jobId), candidateProfileApi.versions()])
  detail.value = applicationDetail
  profileVersions.value = versions
}

async function previewProfile() {
  if (!application.value.profileId) return
  profilePreview.value = await candidateProfileApi.version(application.value.profileId)
  profilePreviewVisible.value = true
}

function displayGender(value?: string) {
  return ({ MALE: '男', FEMALE: '女', UNSPECIFIED: '未填写' } as Record<string, string>)[value || ''] || value || '-'
}

function previewItemTitle(section: string, item: any, index: number) {
  if (section === 'education') return item.school || `教育经历 ${index + 1}`
  if (section === 'work') return item.company || `工作经历 ${index + 1}`
  if (section === 'projects') return item.name || `项目经历 ${index + 1}`
  return item.name || `荣誉奖项 ${index + 1}`
}

function previewItemFields(section: string, item: any) {
  const configs: Record<string, Array<[string, string, boolean?]>> = {
    education: [['学校', 'school'], ['学历', 'degree'], ['专业', 'major'], ['学院', 'college'], ['入学时间', 'startDate'], ['毕业时间', 'expectedGraduation'], ['学历形式', 'studyMode'], ['GPA / 排名', 'gpa']],
    work: [['公司', 'company'], ['岗位', 'role'], ['部门', 'department'], ['城市', 'city'], ['开始时间', 'startDate'], ['结束时间', 'endDate'], ['工作类型', 'employmentType'], ['行业', 'industry'], ['工作职责', 'responsibilities', true], ['成果与量化', 'achievements', true], ['技术 / 工具', 'technologies', true]],
    projects: [['项目名称', 'name'], ['角色', 'role'], ['开始时间', 'startDate'], ['结束时间', 'endDate'], ['项目类型', 'type'], ['团队规模', 'teamSize'], ['项目链接', 'url'], ['技术栈', 'technologies'], ['项目简介', 'description', true], ['个人贡献', 'contributions', true]],
    honors: [['奖项名称', 'name'], ['奖项等级', 'level'], ['获奖日期', 'date'], ['奖项类型', 'category'], ['奖项描述', 'description', true]],
  }
  const enums: Record<string, string> = { BACHELOR: '学士', MASTER: '硕士', DOCTOR: '博士', FULL_TIME: '全日制', PART_TIME: '非全日制', INTERNSHIP: '实习', COMPETITION: '竞赛获奖', HONOR: '荣誉称号', SCHOLARSHIP: '奖学金', CERTIFICATE: '证书' }
  return (configs[section] || []).map(([label, key, wide]) => ({ label, value: enums[item[key]] || item[key] || '', wide: Boolean(wide) }))
}

function syncProgressForm(statusName = application.value.currentStatus) {
  const isCurrent = statusName === application.value.currentStatus
  const step = flowSteps.value.find((item) => item.name === statusName)
  progressForm.currentStatus = statusName || flowSteps.value[0]?.name || '待投递'
  progressForm.progressResult = isTerminalStatus(progressForm.currentStatus) ? '' : step?.result || (isCurrent ? application.value.progressResult || '进行中' : '进行中')
  progressForm.progressOperatedTime = step?.operatedTime || rawTimestampFor(progressForm.currentStatus) || (isCurrent ? application.value.progressOperatedTime || nowValue() : nowValue())
}

function statusMeta(status?: string) {
  return statusConfig[status || '待投递'] || { color: '#667085', icon: Files, className: 'status-default' }
}

function toggleSection(key: keyof typeof sectionOpen) {
  sectionOpen[key] = !sectionOpen[key]
}

function resultMeta(result?: string) {
  if (result === '成功') return { type: 'success', color: '#16a34a' }
  if (result === '失败') return { type: 'danger', color: '#dc2626' }
  return { type: 'info', color: '#409eff' }
}

function terminalMeta(status?: string) {
  if (status === 'Offer') return { type: 'success', color: '#16a34a', label: '已获得 Offer' }
  if (status === '淘汰') return { type: 'danger', color: '#dc2626', label: '流程结束' }
  return { type: 'info', color: '#409eff', label: '' }
}

function isTerminalStatus(status?: string) {
  return terminalStatuses.includes(status || '')
}

function timelineColor(active: boolean, fallback: string, result?: string, status?: string) {
  if (!active) return '#d0d5dd'
  if (isTerminalStatus(status)) return terminalMeta(status).color
  if (result) return resultMeta(result).color
  return fallback
}

function parseProgressFlow(value?: string): ProgressStep[] {
  if (!value?.trim()) return cloneProgress(defaultProgress)
  const raw = value.trim()
  if (raw.startsWith('[')) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        const steps = parsed
          .map((item) => ({
            name: String(item?.name || '').trim(),
            description: String(item?.description || '').trim() || '自定义流程节点',
            result: String(item?.result || '').trim() || undefined,
            operatedTime: String(item?.operatedTime || '').trim() || undefined
          }))
          .filter((item) => item.name)
        if (steps.length) return steps
      }
    } catch {
      // 兼容旧文本格式，JSON 解析失败时继续按文本流程读取。
    }
  }
  return parseProgressText(value)
}

function parseProgressText(value?: string): ProgressStep[] {
  if (!value?.trim()) return cloneProgress(defaultProgress)
  return value.split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [name, ...desc] = line.split('|')
      return { name: name.trim(), description: desc.join('|').trim() || '自定义流程节点' }
    })
}

function stringifyProgressFlow(items: ProgressStep[] = defaultProgress) {
  return JSON.stringify(items.map((item) => ({
    name: item.name,
    description: item.description,
    result: item.result || undefined,
    operatedTime: item.operatedTime || undefined
  })))
}

function stringifyProgressText(items: ProgressStep[] = defaultProgress) {
  return items.map((item) => `${item.name} | ${item.description}`).join('\n')
}

function openProgressDialog() {
  progressText.value = stringifyProgressText(flowSteps.value)
  const current = application.value.currentStatus || ''
  const steps = parseProgressFlow(progressText.value)
  progressStatusAfterSave.value = steps.some((item) => item.name === current) ? current : steps[0]?.name || ''
  progressVisible.value = true
}

function resetProgressFlow() {
  progressText.value = stringifyProgressText(defaultProgress)
  const current = application.value.currentStatus || ''
  progressStatusAfterSave.value = defaultProgress.some((item) => item.name === current) ? current : defaultProgress[0].name
}

async function saveProgressFlow() {
  const steps = mergeProgressMetadata(parseProgressText(progressText.value), flowSteps.value)
  if (!steps.length) {
    ElMessage.warning('请至少保留一个流程阶段')
    return
  }
  const currentStatus = progressStatusAfterSave.value
  if (!currentStatus || !steps.some((item) => item.name === currentStatus)) {
    ElMessage.warning('请选择一个流程中的阶段作为当前状态')
    return
  }
  const target = steps.find((item) => item.name === currentStatus)!
  if (currentStatus === '淘汰' && !findFailedStep(steps)) {
    ElMessage.warning('没有失败环节时，不能将流程标记为淘汰。请先在实际失败的环节选择“失败”。')
    return
  }
  if (currentStatus === 'Offer' && findFailedStep(steps)) {
    ElMessage.warning('当前流程已有失败环节，不能再标记为 Offer。请先修改失败环节。')
    return
  }
  target.result = isTerminalStatus(currentStatus) ? undefined : target.result || (currentStatus === application.value.currentStatus ? application.value.progressResult || '进行中' : '进行中')
  target.operatedTime = target.operatedTime || (currentStatus === application.value.currentStatus ? application.value.progressOperatedTime : '') || nowValue()
  const timeError = validateProgressTimes(steps)
  if (timeError) {
    ElMessage.warning(timeError)
    return
  }
  const nextStatus = resolveCurrentStatus(currentStatus, steps)
  const statusStep = steps.find((item) => item.name === nextStatus)
  await applicationApi.update(jobId, {
    ...application.value,
    currentStatus: nextStatus,
    progressResult: statusStep?.result || '',
    progressOperatedTime: statusStep?.operatedTime || target.operatedTime,
    progressFlow: stringifyProgressFlow(steps)
  })
  progressVisible.value = false
  await load()
}

function openProgressManager(statusName: string) {
  selectedProgressName.value = statusName
  syncProgressForm(statusName)
}

function closeProgressManager() {
  selectedProgressName.value = ''
}

async function saveCurrentProgress() {
  if (!progressForm.currentStatus) {
    ElMessage.warning('请选择当前进度')
    return
  }
  const steps = cloneProgress(flowSteps.value)
  const target = steps.find((item) => item.name === progressForm.currentStatus)
  if (!target) {
    ElMessage.warning('当前进度不在流程中，请先编辑流程')
    return
  }
  if (progressForm.currentStatus === '淘汰') {
    ElMessage.warning('淘汰不能直接选择。请在实际失败的环节选择“失败”，系统会自动标记为淘汰。')
    return
  }
  if (progressForm.currentStatus === 'Offer' && findFailedStep(steps)) {
    ElMessage.warning('当前流程已有失败环节，不能再标记为 Offer。请先修改失败环节。')
    return
  }
  target.result = isTerminalStatus(progressForm.currentStatus) ? undefined : progressForm.progressResult || '进行中'
  target.operatedTime = progressForm.progressOperatedTime || nowValue()
  const timeError = validateProgressTimes(steps)
  if (timeError) {
    ElMessage.warning(timeError)
    return
  }
  const nextStatus = resolveCurrentStatus(progressForm.currentStatus, steps)
  const statusStep = steps.find((item) => item.name === nextStatus)
  await applicationApi.update(jobId, {
    ...application.value,
    currentStatus: nextStatus,
    progressResult: statusStep?.result || '',
    progressOperatedTime: statusStep?.operatedTime || target.operatedTime,
    progressFlow: stringifyProgressFlow(steps)
  })
  ElMessage.success('进度已更新')
  selectedProgressName.value = ''
  await load()
}

function formatDate(value?: string) {
  return formatDateTime(value)
}

function formatClock(value?: string) {
  return value ? value.slice(11, 16) : ''
}

function scheduleTimeLabel(row: Reminder) {
  const start = formatDate(row.remindTime)
  if (!row.endTime) return start
  if (row.endTime <= row.remindTime) return `${start} - 结束时间异常`
  return row.endTime.slice(0, 10) === row.remindTime.slice(0, 10)
    ? `${start} - ${formatClock(row.endTime)}`
    : `${start} - ${formatDate(row.endTime)}`
}

function normalizeScheduleStatus(status?: string) {
  if (status === '已完成' || status === '已取消') return status
  return '未完成'
}

function scheduleClass(item: Reminder) {
  if (normalizeScheduleStatus(item.status) === '已完成') return 'schedule-done'
  if (normalizeScheduleStatus(item.status) === '已取消') return 'schedule-cancelled'
  const priorityClass: Record<string, string> = {
    低: 'schedule-low',
    中: 'schedule-medium',
    高: 'schedule-high',
    紧急: 'schedule-urgent'
  }
  return priorityClass[item.priority || '中'] || 'schedule-medium'
}

function goRelatedSchedules() {
  router.push(`/reminder?quick=application-${jobId}`)
}

function nowValue() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function cloneProgress(items: ProgressStep[]) {
  return items.map((item) => ({ ...item }))
}

function mergeProgressMetadata(nextSteps: ProgressStep[], oldSteps: ProgressStep[]) {
  return nextSteps.map((step) => {
    const old = oldSteps.find((item) => item.name === step.name)
    return {
      ...step,
      result: old?.result,
      operatedTime: old?.operatedTime
    }
  })
}

function validateProgressTimes(steps: ProgressStep[]) {
  let previous: { name: string; time: number } | null = null
  for (const step of steps) {
    if (isTerminalStatus(step.name)) continue
    if (!step.operatedTime) continue
    const time = new Date(step.operatedTime).getTime()
    if (Number.isNaN(time)) return `${step.name} 的操作时间格式不正确`
    if (previous && time <= previous.time) {
      return `${step.name} 的操作时间必须晚于 ${previous.name}`
    }
    previous = { name: step.name, time }
  }
  return ''
}

function findFailedStep(steps: ProgressStep[]) {
  return [...steps].reverse().find((step) => !isTerminalStatus(step.name) && step.result === '失败')
}

function findFailedStepIndex(steps: ProgressStep[]) {
  for (let index = steps.length - 1; index >= 0; index -= 1) {
    const step = steps[index]
    if (!isTerminalStatus(step.name) && step.result === '失败') return index
  }
  return -1
}

function findLastRecordedNormalIndex(steps: ProgressStep[]) {
  for (let index = steps.length - 1; index >= 0; index -= 1) {
    const step = steps[index]
    if (isTerminalStatus(step.name)) continue
    if (step.operatedTime || rawTimestampFor(step.name)) return index
  }
  return -1
}

function resolveCurrentStatus(selectedStatus: string, steps: ProgressStep[]) {
  const failed = findFailedStep(steps)
  const rejected = steps.find((item) => item.name === '淘汰')
  const offer = steps.find((item) => item.name === 'Offer')
  if (failed) {
    if (rejected) {
      rejected.result = undefined
      rejected.operatedTime = failed.operatedTime || nowValue()
    }
    if (offer) {
      offer.result = undefined
      offer.operatedTime = undefined
    }
    return '淘汰'
  }
  if (rejected) {
    rejected.result = undefined
    rejected.operatedTime = undefined
  }
  if (selectedStatus !== 'Offer' && offer) {
    offer.result = undefined
    offer.operatedTime = undefined
  }
  return selectedStatus
}

function rawTimestampFor(status: string) {
  if (status === '已投递') return application.value.appliedTime || ''
  const interview = detail.value.interviewRecords?.find((item: InterviewRecord) => item.round === status)
  return interview?.interviewTime || ''
}

function timestampFor(status: string) {
  return formatDate(rawTimestampFor(status))
}

function openInterview() {
  Object.assign(interviewForm, { id: undefined, jobId, round: '', interviewTime: '', questions: '', result: '', difficulty: 0, summary: '' })
  interviewVisible.value = true
}

function editInterview(row: InterviewRecord) {
  Object.assign(interviewForm, row)
  interviewVisible.value = true
}

async function saveInterview() {
  interviewForm.id ? await interviewApi.update(interviewForm.id, interviewForm) : await interviewApi.create(interviewForm)
  interviewVisible.value = false
  await load()
}

async function removeInterview(id: number) {
  await interviewApi.remove(id)
  await load()
}

function openNote() {
  Object.assign(noteForm, {
    id: undefined,
    jobId,
    title: '',
    content: '# 面试复盘\n\n## 面试问题\n\n## 自我评价\n\n## 后续准备\n'
  })
  noteVisible.value = true
}

function editNote(row: InterviewNote) {
  Object.assign(noteForm, row)
  noteVisible.value = true
}

function previewNote(row: InterviewNote) {
  notePreviewTitle.value = row.title || row.fileName || 'Markdown 笔记预览'
  notePreviewContent.value = row.content || ''
  notePreviewVisible.value = true
}

async function saveNote() {
  noteForm.id ? await noteApi.update(noteForm.id, noteForm) : await noteApi.create(noteForm)
  noteVisible.value = false
  await load()
}

async function removeNote(id: number) {
  await noteApi.remove(id)
  await load()
}

function download(url: string) {
  window.open(url, '_blank')
}

function previewResume() {
  if (!detail.value.resume?.id) return
  resumePreviewUrl.value = resumeApi.previewUrl(detail.value.resume.id)
  resumePreviewVisible.value = true
}

function downloadResumeForApplication() {
  window.open(applicationApi.resumeDownloadUrl(jobId), '_blank')
}
</script>
