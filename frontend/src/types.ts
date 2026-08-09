export interface JobApplication {
  id?: number
  companyName: string
  positionName: string
  positionType?: string
  recruitmentType?: string
  resumeCategory?: string
  resumeAlias?: string
  workLocation?: string
  salary?: string
  source?: string
  jobLink?: string
  jobDescription?: string
  currentStatus: string
  progressFlow?: string
  progressResult?: string
  progressOperatedTime?: string
  appliedTime?: string
  resumeId?: number
  remark?: string
}

export interface InterviewRecord {
  id?: number
  jobId: number
  round: string
  interviewTime?: string
  interviewer?: string
  questions?: string
  result?: string
  difficulty?: number
  summary?: string
}

export interface InterviewNote {
  id?: number
  jobId: number
  title: string
  fileName?: string
  filePath?: string
  content: string
  createdTime?: string
  updatedTime?: string
}

export interface Resume {
  id: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  versionName: string
  resumeCategory?: string
  remark?: string
}

export interface ResumeUsage {
  resumeId: number
  bindCount: number
  applications: ResumeUsageApplication[]
}

export interface ResumeUsageApplication {
  id: number
  companyName?: string
  positionName?: string
  resumeAlias?: string
  currentStatus?: string
}

export interface Reminder {
  id?: number
  title: string
  content?: string
  remindTime: string
  endTime?: string
  scheduleType?: string
  priority?: string
  importance?: string
  relatedApplicationId?: number
  status: string
}

export interface NoteItem {
  id?: number
  parentId?: number | null
  type: 'FOLDER' | 'NOTE'
  title: string
  fileName?: string
  filePath?: string
  content?: string
  createdTime?: string
  updatedTime?: string
  children?: NoteItem[]
}

export interface CandidateProfileResponse {
  profileId: string
  schemaVersion: string
  revision: number
  content: Record<string, unknown>
  updatedAt?: string
}

export interface ProfileSnapshot {
  id: number
  revision: number
  createdAt?: string
}

export interface ExtensionPairing {
  id: number
  extensionId: string
  displayName?: string
  status: 'PENDING' | 'APPROVED' | 'CLAIMED'
  createdTime?: string
}
