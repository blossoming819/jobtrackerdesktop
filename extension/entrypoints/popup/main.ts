import './style.css'

const status = document.querySelector<HTMLParagraphElement>('#status')!
const connectionCard = document.querySelector<HTMLElement>('.connection-card')!
const connectionState = document.querySelector<HTMLSpanElement>('.connection-state')!
const result = document.querySelector<HTMLPreElement>('#result')!
const service = document.querySelector<HTMLSelectElement>('#service')!
const profileSelect = document.querySelector<HTMLSelectElement>('#profile-version')!
const aiProviderSelect = document.querySelector<HTMLSelectElement>('#ai-provider')!
const endpoints = {
  web: 'http://127.0.0.1:8080/api/applymate/v1',
  desktop: 'http://127.0.0.1:18080/api/applymate/v1',
} as const
let activeEndpoint = ''
let profileReady = false

type StatusTone = 'ok' | 'info' | 'error'

function setStatus(message: string, tone: StatusTone = 'info', state = '已检测') {
  status.textContent = message
  connectionCard.dataset.tone = tone
  connectionState.textContent = state
}

async function isApplyMateService(endpoint: string) {
  try {
    const response = await fetch(`${endpoint}/health`)
    const body = await response.json()
    return response.ok && body?.code === 200 && body?.data?.status === 'UP'
  } catch { return false }
}

async function getApiBase() {
  const selected = service.value as keyof typeof endpoints | 'auto'
  if (selected !== 'auto') {
    const endpoint = endpoints[selected]
    if (await isApplyMateService(endpoint)) return endpoint
    throw new Error('LOCAL_SERVICE_OFFLINE')
  }
  for (const endpoint of Object.values(endpoints)) {
    if (await isApplyMateService(endpoint)) return endpoint
  }
  throw new Error('LOCAL_SERVICE_OFFLINE')
}

function serviceName(endpoint: string) { return endpoint === endpoints.web ? 'Web 后端（8080）' : '桌面端服务（18080）' }

async function tokenFor(endpoint: string) {
  const saved = await browser.storage.local.get(['applymateTokens', 'applymateToken', 'applymateApiBase'])
  const tokens = saved.applymateTokens && typeof saved.applymateTokens === 'object' ? saved.applymateTokens as Record<string, string> : {}
  if (tokens[endpoint]) return tokens[endpoint]
  return saved.applymateApiBase === endpoint && typeof saved.applymateToken === 'string' ? saved.applymateToken : ''
}

async function activateToken(endpoint: string, token: string) {
  const saved = await browser.storage.local.get('applymateTokens')
  const tokens = saved.applymateTokens && typeof saved.applymateTokens === 'object' ? saved.applymateTokens as Record<string, string> : {}
  await browser.storage.local.set({ applymateTokens: { ...tokens, [endpoint]: token }, applymateApiBase: endpoint, applymateToken: token })
}

async function clearToken(endpoint: string) {
  const saved = await browser.storage.local.get(['applymateTokens', 'applymateApiBase'])
  const tokens = saved.applymateTokens && typeof saved.applymateTokens === 'object' ? { ...saved.applymateTokens as Record<string, string> } : {}
  delete tokens[endpoint]
  await browser.storage.local.set({ applymateTokens: tokens })
  if (saved.applymateApiBase === endpoint) await browser.storage.local.remove(['applymateToken', 'applymateApiBase', 'applymateProfileId'])
}

function showProfilePlaceholder(text: string) {
  profileReady = false
  profileSelect.replaceChildren(new Option(text, ''))
  profileSelect.disabled = true
  scanButton.disabled = true
}

async function checkService() {
  try {
    const endpoint = await getApiBase()
    activeEndpoint = endpoint
    const token = await tokenFor(endpoint)
    if (token) {
      await activateToken(endpoint, token)
      const loaded = await loadProfileVersions(endpoint, token)
      if (loaded === 'invalid') {
        await clearToken(endpoint)
        setStatus(`${serviceName(endpoint)}的授权已失效，请重新创建配对请求。`, 'info', '需重新配对')
        connect.disabled = false
        claim.disabled = false
        showProfilePlaceholder('授权已失效，请重新配对')
      } else {
        if (loaded === 'empty') setStatus(`已配对${serviceName(endpoint)}，但当前没有个人档案。请先在桌面端创建或导入。`, 'info', '暂无档案')
        else setStatus(`已配对${serviceName(endpoint)}。可选择已有档案并生成填写计划。`, 'ok', '服务正常')
        connect.disabled = true
        claim.disabled = true
      }
    } else {
      setStatus(`已连接${serviceName(endpoint)}。扫描前请先完成配对。`, 'info', '等待配对')
      connect.disabled = false
      claim.disabled = false
      showProfilePlaceholder('请先完成配对')
    }
    await loadAiProviders(endpoint)
  } catch { setStatus('本地服务未启动：无法读取档案或填写。', 'error', '服务异常'); showProfilePlaceholder('本地服务未启动') }
}

async function loadAiProviders(endpoint: string) {
  try {
    const response = await fetch(`${endpoint}/ai/providers`)
    const body = await response.json()
    if (!response.ok || body?.code !== 200 || !body?.data) throw new Error('PROVIDERS_FAILED')
    const saved = await browser.storage.local.get('applymateAiProvider')
    const options = [new Option('自动选择（按 YAML 路由）', '')]
    for (const [id, value] of Object.entries(body.data) as Array<[string, any]>) {
      const usable = Boolean(value.enabled && value.credentialConfigured)
      const reasons = [!value.enabled ? '未启用' : '', !value.credentialConfigured ? '未检测到 Key' : ''].filter(Boolean)
      const option = new Option(`${providerName(id)} · ${value.model}${reasons.length ? `（${reasons.join('、')}）` : ''}`, id)
      option.disabled = !usable
      options.push(option)
    }
    aiProviderSelect.replaceChildren(...options)
    const requested = typeof saved.applymateAiProvider === 'string' ? saved.applymateAiProvider : ''
    aiProviderSelect.value = [...aiProviderSelect.options].some(option => option.value === requested && !option.disabled) ? requested : ''
    await browser.storage.local.set({ applymateAiProvider: aiProviderSelect.value })
    aiProviderSelect.disabled = false
  } catch {
    aiProviderSelect.replaceChildren(new Option('无法读取模型状态', ''))
    aiProviderSelect.disabled = true
  }
}

function providerName(id: string) {
  return ({ deepseek: 'DeepSeek', qwen: '通义千问', local: '本地模型' } as Record<string, string>)[id] || id
}

async function loadProfileVersions(endpoint: string, token: string): Promise<'ready' | 'empty' | 'invalid'> {
  try {
    const response = await fetch(`${endpoint}/profile/values/versions`, { headers: { Authorization: `Bearer ${token}` } })
    const body = await response.json()
    if (!response.ok || body?.code !== 200) {
      if (String(body?.message || '').includes('EXTENSION_TOKEN')) return 'invalid'
      throw new Error('PROFILE_VERSIONS_FAILED')
    }
    const saved = await browser.storage.local.get(['applymateProfileId', 'applymateProfileIds'])
    const versions = Array.isArray(body.data) ? body.data : []
    if (!versions.length) {
      showProfilePlaceholder('暂无档案，请先在桌面端创建或导入')
      return 'empty'
    }
    profileSelect.replaceChildren(...versions.map((item: any) => {
      const option = document.createElement('option')
      option.value = item.profileId
      option.textContent = item.defaultProfile ? `${item.name}（默认）` : item.name
      return option
    }))
    const ids = saved.applymateProfileIds && typeof saved.applymateProfileIds === 'object' ? saved.applymateProfileIds as Record<string, string> : {}
    const requested = ids[endpoint] || (typeof saved.applymateProfileId === 'string' ? saved.applymateProfileId : '')
    const selected = [...profileSelect.options].some(option => option.value === requested) ? requested : profileSelect.options[0]?.value || ''
    profileSelect.value = selected
    await browser.storage.local.set({ applymateProfileId: selected, applymateProfileIds: { ...ids, [endpoint]: selected } })
    profileReady = Boolean(selected)
    profileSelect.disabled = false
    scanButton.disabled = !profileReady
    return 'ready'
  } catch { showProfilePlaceholder('档案读取失败，请重新配对'); return 'invalid' }
}

profileSelect.addEventListener('change', async () => {
  const saved = await browser.storage.local.get('applymateProfileIds')
  const ids = saved.applymateProfileIds && typeof saved.applymateProfileIds === 'object' ? saved.applymateProfileIds as Record<string, string> : {}
  profileReady = Boolean(profileSelect.value)
  await browser.storage.local.set({ applymateProfileId: profileSelect.value, applymateProfileIds: { ...ids, [activeEndpoint]: profileSelect.value } })
})
aiProviderSelect.addEventListener('change', async () => browser.storage.local.set({ applymateAiProvider: aiProviderSelect.value }))

const stored = await browser.storage.local.get('applymateService')
service.value = typeof stored.applymateService === 'string' ? stored.applymateService : 'auto'
service.addEventListener('change', async () => {
  await browser.storage.local.set({ applymateService: service.value })
  await checkService()
})

const connect = document.querySelector<HTMLButtonElement>('#connect')!
const claim = document.querySelector<HTMLButtonElement>('#claim')!
const fillButton = document.querySelector<HTMLButtonElement>('#fill')!
const scanButton = document.querySelector<HTMLButtonElement>('#scan')!
const stopButton = document.querySelector<HTMLButtonElement>('#stop-task')!
const progress = document.querySelector<HTMLDivElement>('#task-progress')!
const progressText = document.querySelector<HTMLSpanElement>('#task-progress-text')!
const copyButton = document.querySelector<HTMLButtonElement>('#copy-result')!
type ScanTask = { phase: string, message: string, result?: unknown, fillPlan?: unknown[], startedAt?: number, updatedAt: number }
let activeTask: ScanTask | undefined
let taskClock: ReturnType<typeof setInterval> | undefined
connect.addEventListener('click', async () => {
  status.textContent = '正在创建配对请求…'
  try {
    const apiBase = await getApiBase()
    const existingToken = await tokenFor(apiBase)
    if (existingToken) { status.textContent = `已配对${serviceName(apiBase)}，无需重复申请。`; return }
    const request = await fetch(`${apiBase}/pairing/requests`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ extensionId: browser.runtime.id, displayName: 'ApplyMate' }) })
    const body = await request.json()
    await browser.storage.local.set({ applymatePairingRequestId: body.data.requestId, applymateClaimSecret: body.data.claimSecret, applymateApiBase: apiBase })
    status.textContent = `配对请求 #${body.data.requestId} 已创建，目标：${serviceName(apiBase)}。请在该服务的个人档案页批准。`
  } catch { status.textContent = '选定的本地服务未启动，无法配对。' }
})

claim.addEventListener('click', async () => {
  const stored = await browser.storage.local.get(['applymatePairingRequestId', 'applymateClaimSecret', 'applymateApiBase'])
  if (!stored.applymatePairingRequestId || !stored.applymateClaimSecret || !stored.applymateApiBase) {
    status.textContent = '请先创建配对请求，并在个人档案页批准。'
    return
  }
  status.textContent = '正在领取本机访问令牌…'
  try {
    const response = await fetch(`${stored.applymateApiBase}/pairing/${stored.applymatePairingRequestId}/claim`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ claimSecret: stored.applymateClaimSecret }),
    })
    const body = await response.json()
    if (!response.ok || !body?.data?.token) throw new Error('CLAIM_FAILED')
    await activateToken(String(stored.applymateApiBase), body.data.token)
    await browser.storage.local.remove(['applymateClaimSecret'])
    status.textContent = `已配对${serviceName(stored.applymateApiBase)}，可以安全读取允许填写的档案字段。`
    connect.disabled = true
    claim.disabled = true
    await loadProfileVersions(String(stored.applymateApiBase), body.data.token)
  } catch { status.textContent = '尚未获得批准或令牌领取失败，请返回个人档案页确认请求状态。' }
})
scanButton.addEventListener('click', async () => {
  if (!profileReady || !profileSelect.value) { status.textContent = '当前服务没有可用个人档案，请先在桌面端创建或导入。'; return }
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true })
  if (!tab.id) { status.textContent = '无法读取当前标签页'; return }
  await renderTask({ phase: 'scanning', message: '正在启动后台扫描，关闭此窗口也不会中断…', updatedAt: Date.now() })
  void browser.runtime.sendMessage({
    type: 'APPLYMATE_START_SCAN', tabId: tab.id, tabUrl: tab.url, tabTitle: tab.title,
    profileName: profileSelect.selectedOptions[0]?.textContent || '所选档案',
  }).catch(() => renderTask({ phase: 'error', message: '后台任务启动失败，请重新加载扩展后再试。', updatedAt: Date.now() }))
})

fillButton.addEventListener('click', async () => {
  fillButton.disabled = true
  void browser.runtime.sendMessage({ type: 'APPLYMATE_CONFIRM_FILL' })
    .catch(() => renderTask({ phase: 'error', message: '后台填写任务启动失败，请重新扫描。', updatedAt: Date.now() }))
})

stopButton.addEventListener('click', async () => {
  stopButton.disabled = true
  stopButton.textContent = '正在停止…'
  try { await browser.runtime.sendMessage({ type: 'APPLYMATE_STOP_TASK' }) }
  catch { await renderTask({ phase: 'error', message: '停止请求未送达，请重新加载扩展。', updatedAt: Date.now() }) }
})

copyButton.addEventListener('click', async event => {
  event.preventDefault()
  event.stopPropagation()
  try {
    await navigator.clipboard.writeText(result.textContent || '')
    copyButton.textContent = '已复制'
    setTimeout(() => { copyButton.textContent = '复制' }, 1400)
  } catch { status.textContent = '复制失败，请展开结果后手动选择文本。' }
})

async function renderTask(task?: ScanTask) {
  if (!task) return
  activeTask = task
  const running = ['scanning', 'mapping', 'reading', 'filling'].includes(task.phase)
  const needsReview = task.phase === 'ready' && task.message.includes('需要复核')
  progress.hidden = false
  progress.dataset.running = String(running)
  progress.dataset.tone = task.phase === 'error' ? 'error' : needsReview ? 'warning' : task.phase === 'success' ? 'success' : 'info'
  updateTaskClock()
  if (running && !taskClock) taskClock = setInterval(updateTaskClock, 1000)
  if (!running && taskClock) { clearInterval(taskClock); taskClock = undefined }
  scanButton.disabled = running || !profileReady
  scanButton.textContent = running ? '任务正在后台进行…' : '重新扫描（再次尝试 AI）'
  stopButton.hidden = !running
  stopButton.disabled = false
  stopButton.textContent = '停止当前任务'
  fillButton.disabled = task.phase !== 'ready' || !task.fillPlan?.length
  if (task.result !== undefined) result.textContent = JSON.stringify(task.result, null, 2)
}

function updateTaskClock() {
  if (!activeTask) return
  const running = ['scanning', 'mapping', 'reading', 'filling'].includes(activeTask.phase)
  const seconds = Math.max(0, Math.floor((Date.now() - (activeTask.startedAt || activeTask.updatedAt)) / 1000))
  progressText.textContent = running ? `${activeTask.message}（已用 ${formatElapsed(seconds)}）` : activeTask.message
}

function formatElapsed(seconds: number) {
  return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}

browser.storage.onChanged.addListener(changes => {
  if (changes.applymateScanTask?.newValue) void renderTask(changes.applymateScanTask.newValue as ScanTask)
})

const savedTask = await browser.storage.local.get('applymateScanTask')
await checkService()
await renderTask(savedTask.applymateScanTask as ScanTask | undefined)
