import http from './http'
import type { CandidateProfileResponse, ExtensionPairing, InterviewNote, InterviewRecord, JobApplication, NoteItem, ProfileSnapshot, Reminder } from '../types'

export const statusOptions = ['收藏', '待投递', '已投递', '笔试', '面试中', '一面', '二面', '三面', '四面', '主管面', 'HR 面', 'Offer', '淘汰']
export const typeOptions = [
  'Java 后端',
  'Go 后端',
  'C++ 后端',
  '算法工程师',
  'AI / 大模型',
  '数据开发',
  '数据分析',
  '前端开发',
  '客户端开发',
  '测试开发',
  '产品经理',
  '运营',
  '银行金融科技',
  '国企管培 / 综合岗',
  '硬件 / 嵌入式'
]
export const recruitmentTypeOptions = ['提前批', '实习', '秋招', '春招', '社招']
export const resumeCategoryOptions = ['Java 简历', '算法简历', 'AI 简历', '前端简历', '国企简历', '通用简历']

export const applicationApi = {
  page: (params: Record<string, unknown>) => http.get('/applications', { params }),
  statusOptions: () => http.get('/applications/status-options'),
  positionTypeOptions: () => http.get('/applications/position-type-options'),
  resumeCategoryOptions: () => http.get('/applications/resume-category-options'),
  detail: (id: number) => http.get(`/applications/${id}`),
  create: (data: JobApplication) => http.post('/applications', data),
  update: (id: number, data: JobApplication) => http.put(`/applications/${id}`, data),
  remove: (id: number) => http.delete(`/applications/${id}`),
  batchRemove: (ids: number[]) => http.delete('/applications/batch', { data: { ids } }),
  resumeDownloadUrl: (id: number) => `/api/applications/${id}/resume/download`,
  status: (id: number, currentStatus: string) => http.patch(`/applications/${id}/status`, { currentStatus })
}

export const interviewApi = {
  create: (data: InterviewRecord) => http.post('/interviews', data),
  update: (id: number, data: InterviewRecord) => http.put(`/interviews/${id}`, data),
  remove: (id: number) => http.delete(`/interviews/${id}`)
}

export const noteApi = {
  create: (data: InterviewNote) => http.post('/notes', data),
  update: (id: number, data: InterviewNote) => http.put(`/notes/${id}`, data),
  remove: (id: number) => http.delete(`/notes/${id}`),
  downloadUrl: (id: number) => `/api/notes/${id}/download`
}

export const noteItemApi = {
  list: () => http.get('/note-items'),
  createFolder: (data: Partial<NoteItem>) => http.post('/note-items/folder', data),
  createNote: (data: Partial<NoteItem>) => http.post('/note-items/note', data),
  upload: (data: FormData) => http.post('/note-items/upload', data),
  update: (id: number, data: Partial<NoteItem>) => http.put(`/note-items/${id}`, data),
  move: (id: number, parentId?: number | null) => http.patch(`/note-items/${id}/move`, null, { params: { parentId: parentId ?? 'root' } }),
  moveRoot: (id: number) => http.patch(`/note-items/${id}/move-root`),
  remove: (id: number) => http.delete(`/note-items/${id}`),
  downloadUrl: (id: number) => `/api/note-items/${id}/download`,
  previewUrl: (id: number) => `/api/note-items/${id}/preview`
}

export const resumeApi = {
  list: () => http.get('/resumes'),
  usage: () => http.get('/resumes/usage'),
  upload: (data: FormData) => http.post('/resumes/upload', data),
  update: (id: number, resumeCategory: string, remark?: string) => http.put(`/resumes/${id}`, null, { params: { resumeCategory, remark } }),
  remove: (id: number) => http.delete(`/resumes/${id}`),
  downloadUrl: (id: number) => `/api/resumes/${id}/download`,
  previewUrl: (id: number) => `/api/resumes/${id}/preview`
  ,parse: (id: number, options = { allowCloudAi: false, allowVisionFallback: false }) => http.post(`/applymate/v1/resumes/${id}/parse`, options)
}

export const reminderApi = {
  list: () => http.get('/reminders'),
  create: (data: Reminder) => http.post('/reminders', data),
  update: (id: number, data: Reminder) => http.put(`/reminders/${id}`, data),
  complete: (id: number) => http.patch(`/reminders/${id}/complete`),
  remove: (id: number) => http.delete(`/reminders/${id}`)
}

export const dashboardApi = { overview: () => http.get('/dashboard') }
export const storageApi = {
  get: () => http.get('/storage'),
  update: (data: { resumeDir: string; noteDir: string; generalNoteDir: string }) => http.put('/storage', data)
}

export const candidateProfileApi = {
  get: () => http.get('/applymate/v1/profile') as Promise<CandidateProfileResponse>,
  save: (content: Record<string, unknown>) => http.put('/applymate/v1/profile', content) as Promise<CandidateProfileResponse>,
  snapshots: (limit = 5) => http.get('/applymate/v1/profile/snapshots', { params: { limit } }) as Promise<ProfileSnapshot[]>,
  restore: (id: number) => http.put(`/applymate/v1/profile/snapshots/${id}/restore`) as Promise<CandidateProfileResponse>
}

export const pairingApi = {
  pending: () => http.get('/applymate/v1/pairing/pending') as Promise<ExtensionPairing[]>,
  approve: (id: number) => http.post(`/applymate/v1/pairing/${id}/approve`) as Promise<{ requestId: number, status: string }>
}
