export default defineContentScript({ matches: ['<all_urls>'], main() {
  browser.runtime.onMessage.addListener((message) => message?.type === 'APPLYMATE_SCAN' ? scan() : undefined)
} })

function scan() {
  const elements = [...document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input:not([type=password]), textarea, select')]
    .filter(element => !element.disabled && element.offsetParent !== null)
    .map(element => ({ tagName: element.tagName.toLowerCase(), inputType: element instanceof HTMLInputElement ? element.type : undefined, labelText: labelFor(element), placeholder: element.getAttribute('placeholder') || undefined, name: element.getAttribute('name') || undefined, id: element.id || undefined, required: element.required, options: element instanceof HTMLSelectElement ? [...element.options].map(option => option.text.trim()).filter(Boolean) : undefined }))
  return Promise.resolve({ fields: elements })
}
function labelFor(element: Element) {
  const id = element.id
  const explicit = id ? document.querySelector(`label[for="${CSS.escape(id)}"]`)?.textContent : undefined
  return (explicit || element.closest('label')?.textContent || element.getAttribute('aria-label') || '').trim() || undefined
}
