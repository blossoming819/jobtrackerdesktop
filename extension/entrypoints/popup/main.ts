const status = document.querySelector<HTMLParagraphElement>('#status')!
const result = document.querySelector<HTMLPreElement>('#result')!
const apiBase = 'http://127.0.0.1:18080/api/applymate/v1'
const connect = document.querySelector<HTMLButtonElement>('#connect')!
connect.addEventListener('click', async () => {
  status.textContent = '正在请求桌面端配对…'
  try {
    const request = await fetch(`${apiBase}/pairing/requests`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ extensionId: browser.runtime.id, displayName: 'ApplyMate 浏览器扩展' }) })
    const body = await request.json()
    await browser.storage.local.set({ applymatePairingRequestId: body.data.requestId, applymateClaimSecret: body.data.claimSecret })
    status.textContent = `配对请求 #${body.data.requestId} 已创建，请在个人档案页批准。`
  } catch { status.textContent = '桌面端未启动，无法配对。' }
})
document.querySelector<HTMLButtonElement>('#scan')!.addEventListener('click', async () => {
  status.textContent = '正在扫描…'
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true })
  if (!tab.id) { status.textContent = '无法读取当前标签页'; return }
  const scan = await browser.tabs.sendMessage(tab.id, { type: 'APPLYMATE_SCAN' }) as { fields: unknown[] }
  status.textContent = `发现 ${scan.fields.length} 个可填写字段`
  result.textContent = JSON.stringify(scan.fields, null, 2)
})

fetch(`${apiBase}/health`).catch(() => { status.textContent = '桌面端未启动：仍可扫描页面，无法读取档案或填写' })
