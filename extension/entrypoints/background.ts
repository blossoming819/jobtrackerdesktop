type ScannedField = {
  index: number
  fieldKey?: string
  labelText?: string
  tagName?: string
  inputType?: string
  placeholder?: string
  options?: string[]
  context?: string[]
  positionAmongSameLabel?: number
  sameLabelCount?: number
}

type AiMapping = { index: number, fieldKey: string, confidence: number, reason?: string }
type FillPlanItem = { index: number, value: string, alternatives?: string[], fieldKey: string, label?: string, inputType?: string }
type TaskPhase = 'scanning' | 'mapping' | 'reading' | 'ready' | 'filling' | 'success' | 'error'
type ScanTask = {
  phase: TaskPhase
  message: string
  result?: unknown
  fillPlan?: FillPlanItem[]
  tabId?: number
  tabUrl?: string
  startedAt?: number
  updatedAt: number
}

const TASK_KEY = 'applymateScanTask'
let activeController: AbortController | undefined
let activeRunId = 0

export default defineBackground(() => {
  browser.runtime.onMessage.addListener((message: any) => {
    if (message?.type === 'APPLYMATE_START_SCAN') {
      return startScan(message)
    }
    if (message?.type === 'APPLYMATE_CONFIRM_FILL') {
      return runFill().catch(error => fail(error))
    }
    if (message?.type === 'APPLYMATE_STOP_TASK') return stopTask()
  })
})

async function startScan(message: any) {
  activeController?.abort()
  const controller = new AbortController()
  activeController = controller
  const runId = ++activeRunId
  try { return await runScan(message, controller.signal, runId) }
  catch (error) {
    if (runId !== activeRunId || controller.signal.aborted) return { ok: false, stopped: true }
    return fail(error)
  } finally {
    if (runId === activeRunId) activeController = undefined
  }
}

async function stopTask() {
  activeRunId += 1
  activeController?.abort()
  activeController = undefined
  await updateTask('error', '任务已手动停止。可以点击“重新扫描”再次尝试 AI。', { result: { stopped: true } })
  return { ok: true }
}

function ensureActive(runId: number, signal: AbortSignal) {
  if (runId !== activeRunId || signal.aborted) throw new DOMException('TASK_STOPPED', 'AbortError')
}

async function updateTask(phase: TaskPhase, message: string, extra: Partial<ScanTask> = {}) {
  const stored = await browser.storage.local.get(TASK_KEY)
  const previous = stored[TASK_KEY] as ScanTask | undefined
  const startedAt = phase === 'scanning' ? Date.now() : extra.startedAt || previous?.startedAt || Date.now()
  const task: ScanTask = { ...extra, phase, message, startedAt, updatedAt: Date.now() }
  await browser.storage.local.set({ [TASK_KEY]: task })
  return task
}

async function fail(error: unknown) {
  const message = error instanceof Error ? error.message : '未知错误'
  await updateTask('error', friendlyError(message), { result: { error: message } })
  return { ok: false, error: message }
}

async function runScan(request: { tabId: number, tabUrl?: string, tabTitle?: string, profileName?: string }, signal: AbortSignal, runId: number) {
  await updateTask('scanning', '正在读取当前页面的表单控件，请稍候…', { tabId: request.tabId, tabUrl: request.tabUrl })
  const scan = await Promise.race([
    browser.tabs.sendMessage(request.tabId, { type: 'APPLYMATE_SCAN' }) as Promise<{ fields: ScannedField[] }>,
    new Promise<never>((_, reject) => setTimeout(() => reject(new Error('SCAN_TIMEOUT')), 12000)),
  ])
  ensureActive(runId, signal)

  const stored = await browser.storage.local.get(['applymateToken', 'applymateApiBase', 'applymateProfileId', 'applymateAiProvider'])
  if (!stored.applymateToken || !stored.applymateApiBase) {
    await updateTask('error', `发现 ${scan.fields.length} 个可填写字段；请先完成配对，再生成填写计划。`, {
      tabId: request.tabId, tabUrl: request.tabUrl, result: scan.fields,
    })
    return { ok: false }
  }
  if (!stored.applymateProfileId) {
    await updateTask('error', `发现 ${scan.fields.length} 个可填写字段；当前服务没有可用个人档案，请先在桌面端创建或导入。`, {
      tabId: request.tabId, tabUrl: request.tabUrl, result: scan.fields,
    })
    return { ok: false }
  }

  await updateTask('mapping', `已发现 ${scan.fields.length} 个控件，正在理解字段含义…`, { tabId: request.tabId, tabUrl: request.tabUrl })
  let aiMappings: AiMapping[] = []
  let aiNote = 'AI 已参与字段映射'
  try {
    const providerParam = stored.applymateAiProvider ? `?provider=${encodeURIComponent(String(stored.applymateAiProvider))}` : ''
    const mappingResponse = await fetch(`${stored.applymateApiBase}/ai/field-mappings${providerParam}`, {
      method: 'POST',
      signal,
      headers: { Authorization: `Bearer ${stored.applymateToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        host: request.tabUrl ? new URL(request.tabUrl).hostname : '',
        title: request.tabTitle || '',
        fields: scan.fields.map(field => ({
          index: field.index, label: field.labelText || '', tagName: field.tagName || '',
          inputType: field.inputType || '', placeholder: field.placeholder || '',
          options: field.options || [], context: field.context || [], suggestedFieldKey: field.fieldKey,
          positionAmongSameLabel: field.positionAmongSameLabel || 0, sameLabelCount: field.sameLabelCount || 1,
        })),
      }),
    })
    ensureActive(runId, signal)
    const body = await mappingResponse.json()
    if (!mappingResponse.ok || body?.code !== 200 || !Array.isArray(body.data)) throw new Error(body?.message || `HTTP_${mappingResponse.status}`)
    aiMappings = body.data
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    aiNote = /NOT_CONFIGURED|UNAVAILABLE/.test(message) ? 'AI 未配置，已使用本地规则' : 'AI 暂不可用，已使用本地规则'
  }

  const aiByIndex = new Map(aiMappings.filter(item => item.confidence >= 0.65).map(item => [item.index, item]))
  ensureActive(runId, signal)
  const resolvedFields = scan.fields.map(field => ({ ...field, fieldKey: aiByIndex.get(field.index)?.fieldKey || field.fieldKey }))
  const keys = [...new Set(resolvedFields.map(field => field.fieldKey).filter((key): key is string => Boolean(key)))]
  if (!keys.length) {
    await updateTask('error', `扫描了 ${scan.fields.length} 个控件，但没有得到可靠字段映射。${aiNote}。`, {
      tabId: request.tabId, tabUrl: request.tabUrl, result: { fields: scan.fields, aiMappings },
    })
    return { ok: false }
  }

  await updateTask('reading', `已识别 ${keys.length} 类字段，正在从个人档案生成填写计划…`, { tabId: request.tabId, tabUrl: request.tabUrl })
  let selectedProfileId = String(stored.applymateProfileId)
  let contextProfileName = ''
  if (request.tabUrl) {
    try {
      const response = await fetch(`${stored.applymateApiBase}/profile/values/context?url=${encodeURIComponent(request.tabUrl)}`, { headers: { Authorization: `Bearer ${stored.applymateToken}` }, signal })
      const body = await response.json()
      if (response.ok && body?.data?.profileId) {
        selectedProfileId = body.data.profileId
        contextProfileName = body.data.profileName || ''
      }
    } catch { /* Use the manually selected profile. */ }
  }

  const profileParam = `profileId=${encodeURIComponent(selectedProfileId)}`
  ensureActive(runId, signal)
  const response = await fetch(`${stored.applymateApiBase}/profile/values?${profileParam}&${keys.map(key => `keys=${encodeURIComponent(key)}`).join('&')}`, { headers: { Authorization: `Bearer ${stored.applymateToken}` }, signal })
  const body = await response.json()
  ensureActive(runId, signal)
  if (!response.ok || !body?.data) {
    if (String(body?.message || '').includes('EXTENSION_TOKEN')) await browser.storage.local.remove(['applymateToken', 'applymateApiBase'])
    throw new Error(`PROFILE_READ_FAILED:${response.status}:${body?.message || ''}`)
  }
  const profileValues = normalizeEducationDatePairs({ ...body.data })
  const proposed = resolvedFields.flatMap(field => {
    if (!field.fieldKey || profileValues[field.fieldKey] === undefined) return []
    const raw = String(profileValues[field.fieldKey])
    const option = field.options?.find(item => item === raw || item.toLowerCase() === raw.toLowerCase())
    const display = displayCandidates(field.fieldKey, option || raw)
    return [{ index: field.index, value: display[0], alternatives: display.slice(1), fieldKey: field.fieldKey, label: field.labelText, inputType: field.inputType }]
  })
  const uniqueByKey = new Map<string, FillPlanItem>()
  for (const item of proposed) {
    const current = uniqueByKey.get(item.fieldKey)
    if (!current || (current.inputType === 'search' && item.inputType !== 'search')) uniqueByKey.set(item.fieldKey, item)
  }
  const fillPlan = [...uniqueByKey.values()]
  const result = fillPlan.map(({ index, fieldKey, label, value, alternatives }) => ({ index, fieldKey, label, value, alternatives, confidence: aiByIndex.get(index)?.confidence }))
  await updateTask('ready', `${aiNote}；已使用${contextProfileName ? `投递绑定的“${contextProfileName}”` : `档案版本“${request.profileName || '默认档案'}”`}生成 ${fillPlan.length} 项计划，请核对后确认填写。`, {
    tabId: request.tabId, tabUrl: request.tabUrl, fillPlan, result,
  })
  return { ok: true }
}

async function runFill() {
  const stored = await browser.storage.local.get(TASK_KEY)
  const task = stored[TASK_KEY] as ScanTask | undefined
  if (!task?.tabId || !task.fillPlan?.length) throw new Error('NO_FILL_PLAN')
  await updateTask('filling', `正在向目标页面填写 ${task.fillPlan.length} 项内容…`, task)
  const response = await browser.tabs.sendMessage(task.tabId, { type: 'APPLYMATE_FILL', items: task.fillPlan }) as { results: Array<{ index: number, status: string }> }
  const succeeded = response.results.filter(item => item.status === 'SUCCESS').length
  const failedIndexes = new Set(response.results.filter(item => item.status !== 'SUCCESS').map(item => item.index))
  const remaining = task.fillPlan.filter(item => failedIndexes.has(item.index))
  if (remaining.length) {
    await updateTask('ready', `已验证成功 ${succeeded}/${task.fillPlan.length} 项；另有 ${remaining.length} 项需要复核。可查看实际值后再次确认填写，扩展不会自动提交。`, {
      tabId: task.tabId, tabUrl: task.tabUrl, fillPlan: remaining, result: response.results,
    })
  } else {
    await updateTask('success', `已填写并验证 ${succeeded}/${task.fillPlan.length} 项；请在网页中做最终复核，扩展不会自动提交表单。`, {
      tabId: task.tabId, tabUrl: task.tabUrl, fillPlan: task.fillPlan, result: response.results,
    })
  }
  return { ok: true }
}

function friendlyError(message: string) {
  if (message.includes('SCAN_TIMEOUT')) return '扫描超时。请刷新目标网页；若刚更新扩展，请先在扩展管理页重新加载。'
  if (message.includes('Could not establish connection') || message.includes('Receiving end does not exist')) return '目标网页尚未加载扩展脚本。请刷新网页后重新扫描。'
  if (message.includes('NO_FILL_PLAN')) return '没有可填写的计划，请先重新扫描。'
  if (message.includes('401') || message.includes('403') || message.includes('EXTENSION_TOKEN')) return '当前服务的配对令牌已失效，请打开扩展重新完成配对。'
  return `任务未完成：${message || '网络请求失败'}。你可以修复问题后重新扫描。`
}

function displayCandidates(fieldKey: string, raw: string) {
  const aliases: Record<string, string[]> = {
    'basic.gender:MALE': ['男', '男性', 'Male'], 'basic.gender:FEMALE': ['女', '女性', 'Female'],
    'education.degree:BACHELOR': ['本科', '学士', 'Bachelor'], 'education.degree:MASTER': ['硕士', 'Master'],
    'education.degree:DOCTOR': ['博士', 'Doctor', 'PhD'], 'education.studyMode:FULL_TIME': ['全日制', 'Full-time'],
    'education.studyMode:PART_TIME': ['非全日制', 'Part-time'], 'work.employmentType:INTERNSHIP': ['实习', 'Internship'],
    'work.employmentType:FULL_TIME': ['全职', 'Full-time'], 'work.employmentType:PART_TIME': ['兼职', 'Part-time'],
  }
  return aliases[`${fieldKey.replace(/\.\d+\./, '.')}:${raw}`] || [raw]
}

function normalizeEducationDatePairs(values: Record<string, unknown>) {
  const indexes = new Set<number>()
  for (const key of Object.keys(values)) {
    const match = key.match(/^education\.(\d+)\.(?:startDate|expectedGraduation)$/)
    if (match) indexes.add(Number(match[1]))
  }
  for (const index of indexes) {
    const startKey = `education.${index}.startDate`
    const endKey = `education.${index}.expectedGraduation`
    const start = String(values[startKey] || '')
    const end = String(values[endKey] || '')
    if (start && end && start.slice(0, 10) > end.slice(0, 10)) {
      values[startKey] = end
      values[endKey] = start
    }
  }
  return values
}
