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
document.querySelector<HTMLButtonElement>('#scan')!.addEventListener('click', async () => {
  status.textContent = '正在扫描…'
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true })
  if (!tab.id) { status.textContent = '无法读取当前标签页'; return }
  const scan = await browser.tabs.sendMessage(tab.id, { type: 'APPLYMATE_SCAN' }) as { fields: unknown[] }
  status.textContent = `发现 ${scan.fields.length} 个可填写字段`
  result.textContent = JSON.stringify(scan.fields, null, 2)
})

void checkService()
