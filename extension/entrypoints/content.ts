export default defineContentScript({ matches: ['<all_urls>'], main() {
  browser.runtime.onMessage.addListener((message) => message?.type === 'APPLYMATE_SCAN' ? scan() : message?.type === 'APPLYMATE_FILL' ? fill(message.items || []) : undefined)
} })

function scan() {
  const elements = [...document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>('input:not([type=password]), textarea, select')]
    .filter(element => !element.disabled && element.offsetParent !== null)
    .map((element, index) => { const labelText = labelFor(element); return { index, tagName: element.tagName.toLowerCase(), inputType: element instanceof HTMLInputElement ? element.type : undefined, labelText, fieldKey: match(labelText), placeholder: element.getAttribute('placeholder') || undefined, name: element.getAttribute('name') || undefined, id: element.id || undefined, required: element.required, options: element instanceof HTMLSelectElement ? [...element.options].map(option => option.text.trim()).filter(Boolean) : undefined } })
  return Promise.resolve({ fields: elements })
}
function match(label?: string) { const value=(label||'').toLowerCase(); if(/姓名|name/.test(value))return 'basic.nameCn'; if(/手机|电话|mobile/.test(value))return 'contact.phone'; if(/邮箱|email/.test(value))return 'contact.email'; if(/学历|degree|教育程度/.test(value))return 'education.degree'; if(/学校|院校|university/.test(value))return 'education.school'; if(/专业|major/.test(value))return 'education.major'; return undefined }
function fill(items: Array<{index:number,value:string}>) { const elements=[...document.querySelectorAll<HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement>('input:not([type=password]), textarea, select')].filter(e=>!e.disabled&&e.offsetParent!==null); return { results:items.map(item=>{const e=elements[item.index]; if(!e)return {index:item.index,status:'ELEMENT_NOT_FOUND'}; const setter=e instanceof HTMLSelectElement?Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value')?.set:Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')?.set; setter?.call(e,item.value); e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));e.dispatchEvent(new Event('blur',{bubbles:true}));return {index:item.index,status:e.value===item.value?'SUCCESS':'VALUE_MISMATCH'}}) } }
function labelFor(element: Element) {
  const id = element.id
  const explicit = id ? document.querySelector(`label[for="${CSS.escape(id)}"]`)?.textContent : undefined
  return (explicit || element.closest('label')?.textContent || element.getAttribute('aria-label') || '').trim() || undefined
}
