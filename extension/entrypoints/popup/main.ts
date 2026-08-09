const status = document.querySelector<HTMLParagraphElement>('#status')!
const result = document.querySelector<HTMLPreElement>('#result')!
document.querySelector<HTMLButtonElement>('#scan')!.addEventListener('click', async () => {
  status.textContent = '正在扫描…'
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true })
  if (!tab.id) { status.textContent = '无法读取当前标签页'; return }
  const scan = await browser.tabs.sendMessage(tab.id, { type: 'APPLYMATE_SCAN' }) as { fields: unknown[] }
  status.textContent = `发现 ${scan.fields.length} 个可填写字段`
  result.textContent = JSON.stringify(scan.fields, null, 2)
})
