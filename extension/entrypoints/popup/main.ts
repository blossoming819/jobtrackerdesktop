import './style.css'

const status = document.querySelector<HTMLParagraphElement>('#status')!
const result = document.querySelector<HTMLPreElement>('#result')!
const service = document.querySelector<HTMLSelectElement>('#service')!
const endpoints = {
  web: 'http://127.0.0.1:8080/api/applymate/v1',
  desktop: 'http://127.0.0.1:18080/api/applymate/v1',
} as const

async function getApiBase() {
  const selected = service.value as keyof typeof endpoints | 'auto'
  if (selected !== 'auto') return endpoints[selected]
  for (const [kind, endpoint] of Object.entries(endpoints)) {
    try {
      const response = await fetch(`${endpoint}/health`)
      if (response.ok) return endpoint
    } catch { /* Continue to the next local service. */ }
  }
  throw new Error('LOCAL_SERVICE_OFFLINE')
}

function serviceName(endpoint: string) { return endpoint === endpoints.web ? 'Web 后端（8080）' : '桌面端服务（18080）' }

async function checkService() {
  try {
    const endpoint = await getApiBase()
    status.textContent = `已连接${serviceName(endpoint)}。扫描后可申请配对。`
  } catch { status.textContent = '本地服务未启动：仍可扫描页面，无法读取档案或填写。' }
}

const stored = await browser.storage.local.get('applymateService')
service.value = typeof stored.applymateService === 'string' ? stored.applymateService : 'auto'
service.addEventListener('change', async () => {
  await browser.storage.local.set({ applymateService: service.value })
  await checkService()
})

const connect = document.querySelector<HTMLButtonElement>('#connect')!
const claim = document.querySelector<HTMLButtonElement>('#claim')!
const fillButton = document.querySelector<HTMLButtonElement>('#fill')!
type ScannedField = { index: number, fieldKey?: string, labelText?: string, options?: string[] }
let fillPlan: Array<{ index: number, value: string, fieldKey: string, label?: string }> = []
connect.addEventListener('click', async () => {
  status.textContent = '正在创建配对请求…'
  try {
    const apiBase = await getApiBase()
    const request = await fetch(`${apiBase}/pairing/requests`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ extensionId: browser.runtime.id, displayName: 'ApplyMate 浏览器扩展' }) })
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
    await browser.storage.local.set({ applymateToken: body.data.token })
    await browser.storage.local.remove(['applymateClaimSecret'])
    status.textContent = `已配对${serviceName(stored.applymateApiBase)}，可以安全读取允许填写的档案字段。`
  } catch { status.textContent = '尚未获得批准或令牌领取失败，请返回个人档案页确认请求状态。' }
})
document.querySelector<HTMLButtonElement>('#scan')!.addEventListener('click', async () => {
  status.textContent = '正在扫描…'
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true })
  if (!tab.id) { status.textContent = '无法读取当前标签页'; return }
  const scan = await browser.tabs.sendMessage(tab.id, { type: 'APPLYMATE_SCAN' }) as { fields: ScannedField[] }
  const stored = await browser.storage.local.get(['applymateToken', 'applymateApiBase'])
  const keys = [...new Set(scan.fields.map(field => field.fieldKey).filter((key): key is string => Boolean(key)))]
  if (!stored.applymateToken || !stored.applymateApiBase) {
    status.textContent = `发现 ${scan.fields.length} 个可填写字段；请先完成配对后再生成填写计划。`
    result.textContent = JSON.stringify(scan.fields, null, 2)
    return
  }
  try {
    const response = await fetch(`${stored.applymateApiBase}/profile/values?${keys.map(key => `keys=${encodeURIComponent(key)}`).join('&')}`, { headers: { Authorization: `Bearer ${stored.applymateToken}` } })
    const body = await response.json()
    if (!response.ok || !body?.data) throw new Error('PROFILE_READ_FAILED')
    fillPlan = scan.fields.flatMap(field => {
      if (!field.fieldKey || body.data[field.fieldKey] === undefined) return []
      const raw = String(body.data[field.fieldKey])
      const option = field.options?.find(item => item === raw || item.toLowerCase() === raw.toLowerCase())
      return [{ index: field.index, value: option || raw, fieldKey: field.fieldKey, label: field.labelText }]
    })
    fillButton.disabled = fillPlan.length === 0
    status.textContent = `已生成 ${fillPlan.length} 项填写计划；请核对后点击“确认填写”。`
    result.textContent = JSON.stringify(fillPlan.map(({ index, fieldKey, label, value }) => ({ index, fieldKey, label, value })), null, 2)
  } catch { status.textContent = '无法读取档案字段。请确认扩展已配对且目标服务仍在运行。' }
})

fillButton.addEventListener('click', async () => {
  if (fillPlan.length === 0) return
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true })
  if (!tab.id) { status.textContent = '无法读取当前标签页'; return }
  const response = await browser.tabs.sendMessage(tab.id, { type: 'APPLYMATE_FILL', items: fillPlan }) as { results: Array<{ status: string }> }
  const succeeded = response.results.filter(item => item.status === 'SUCCESS').length
  status.textContent = `已填写 ${succeeded}/${fillPlan.length} 项；请在网页中复核，扩展不会自动提交表单。`
  result.textContent = JSON.stringify(response.results, null, 2)
})

void checkService()
