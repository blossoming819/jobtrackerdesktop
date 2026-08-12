import { matchField, type FieldSignals } from '../utils/field-registry'

type FormControl = HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
type FillItem = { index: number, value: string, alternatives?: string[], fieldKey?: string }

export default defineContentScript({ matches: ['<all_urls>'], main() {
  browser.runtime.onMessage.addListener((message) => message?.type === 'APPLYMATE_SCAN'
    ? scan()
    : message?.type === 'APPLYMATE_FILL' ? fill(message.items || []) : undefined)
} })

function controls() {
  return [...document.querySelectorAll<FormControl>('input, textarea, select')]
    .filter(element => !element.disabled && element.offsetParent !== null
      && !(element instanceof HTMLInputElement && ['file', 'hidden', 'password', 'checkbox', 'radio'].includes(element.type)))
}

function scan() {
  const occurrences = new Map<string, number>()
  const fields = controls().map((element, index) => {
    const signals = signalsFor(element)
    const matched = matchField(signals)
    const collection = matched?.match(/^(education|work|projects|honors)\.(.+)$/)
    let fieldKey = matched
    if (collection) {
      const occurrence = occurrences.get(matched!) || 0
      occurrences.set(matched!, occurrence + 1)
      fieldKey = `${collection[1]}.${occurrence}.${collection[2]}`
    }
    return {
      index,
      tagName: element.tagName.toLowerCase(),
      inputType: element instanceof HTMLInputElement ? element.type : undefined,
      labelText: signals.primary[0],
      fieldKey,
      context: signals.context.slice(0, 8),
      signals: [...signals.primary, ...signals.attributes, ...signals.context].slice(0, 12),
      placeholder: element.getAttribute('placeholder') || undefined,
      name: element.getAttribute('name') || undefined,
      id: element.id || undefined,
      required: element.required,
      options: element instanceof HTMLSelectElement ? [...element.options].map(option => option.text.trim()).filter(Boolean) : undefined,
    }
  })

  const labelGroups = new Map<string, number[]>()
  for (const field of fields) {
    const signature = `${field.context[0] || ''}\u0000${field.labelText || ''}`
    const group = labelGroups.get(signature) || []
    group.push(field.index)
    labelGroups.set(signature, group)
  }
  return Promise.resolve({ fields: fields.map(field => {
    const group = labelGroups.get(`${field.context[0] || ''}\u0000${field.labelText || ''}`) || [field.index]
    return { ...field, positionAmongSameLabel: group.indexOf(field.index), sameLabelCount: group.length }
  }) })
}

async function fill(items: FillItem[]) {
  const elements = controls()
  const results = []
  for (const item of items) {
    const element = elements[item.index]
    if (!element) { results.push({ index: item.index, fieldKey: item.fieldKey, status: 'ELEMENT_NOT_FOUND' }); continue }
    results.push(await fillOne(element, item))
  }
  return { results }
}

async function fillOne(element: FormControl, item: FillItem) {
  const candidates = [...new Set([item.value, ...(item.alternatives || [])].map(value => value.trim()).filter(Boolean))]
  if (element instanceof HTMLSelectElement) {
    const option = [...element.options].find(option => candidates.some(value => normalized(option.text) === normalized(value) || option.value === value))
    if (!option) return { index: item.index, fieldKey: item.fieldKey, status: 'OPTION_NOT_FOUND', expected: candidates, actual: element.value }
    setNativeValue(element, option.value)
    dispatchValueEvents(element)
    await settle()
    return { index: item.index, fieldKey: item.fieldKey, status: element.value === option.value ? 'SUCCESS' : 'VALUE_MISMATCH', expected: option.text, actual: selectedText(element), attempts: 1 }
  }

  const customSelect = element.getAttribute('role') === 'combobox'
    || (element instanceof HTMLInputElement && (element.type === 'search' || element.readOnly))
  if (customSelect) {
    const selected = await selectCustomOption(element, candidates)
    if (!selected) return { index: item.index, fieldKey: item.fieldKey, status: 'OPTION_NOT_FOUND', expected: candidates, actual: controlValue(element), attempts: candidates.length }
    const actual = controlValue(element)
    const verified = candidates.some(value => normalized(actual).includes(normalized(value)) || normalized(value).includes(normalized(actual)))
    return { index: item.index, fieldKey: item.fieldKey, status: verified ? 'SUCCESS' : 'REVIEW_REQUIRED', expected: selected, actual, attempts: 1 }
  }

  for (let attempt = 0; attempt < candidates.length; attempt += 1) {
    setNativeValue(element, candidates[attempt])
    dispatchValueEvents(element)
    await settle()
    if (normalized(element.value) === normalized(candidates[attempt])) {
      return { index: item.index, fieldKey: item.fieldKey, status: 'SUCCESS', expected: candidates[attempt], actual: element.value, attempts: attempt + 1 }
    }
  }
  return { index: item.index, fieldKey: item.fieldKey, status: 'VALUE_MISMATCH', expected: candidates, actual: element.value, attempts: candidates.length }
}

async function selectCustomOption(element: HTMLInputElement | HTMLTextAreaElement, candidates: string[]) {
  element.click()
  element.focus()
  if (!element.readOnly && element instanceof HTMLInputElement && element.type === 'search') {
    setNativeValue(element, candidates[0] || '')
    element.dispatchEvent(new Event('input', { bubbles: true }))
  }
  await new Promise(resolve => setTimeout(resolve, 180))
  const optionElements = [...document.querySelectorAll<HTMLElement>('[role="option"], li, [class*="option" i]')]
    .filter(option => option.offsetParent !== null && option !== element && option.textContent && option.textContent.trim().length <= 120)
  const option = optionElements.find(option => candidates.some(value => normalized(option.textContent || '') === normalized(value)))
  if (!option) { element.blur(); return undefined }
  const selected = option.textContent?.trim() || candidates[0]
  option.click()
  await settle()
  dispatchValueEvents(element)
  return selected
}

function controlValue(element: FormControl) {
  return cleanText(element.getAttribute('aria-valuetext')) || element.value || cleanText(element.textContent) || cleanText(element.getAttribute('aria-label')) || ''
}
function selectedText(element: HTMLSelectElement) { return element.selectedOptions[0]?.text.trim() || element.value }
function settle() { return new Promise(resolve => setTimeout(resolve, 120)) }

function setNativeValue(element: FormControl, value: string) {
  const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype
    : element instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
  Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(element, value)
}

function dispatchValueEvents(element: FormControl) {
  element.dispatchEvent(new Event('input', { bubbles: true }))
  element.dispatchEvent(new Event('change', { bubbles: true }))
  element.dispatchEvent(new Event('blur', { bubbles: true }))
}

function normalized(value: string) { return value.replace(/\s+/g, '').toLowerCase() }
function cleanText(value?: string | null) { return value?.replace(/\s+/g, ' ').replace(/^[*：:\s]+|[*：:\s]+$/g, '').trim() || undefined }
function pushUnique(target: string[], value?: string | null) { const cleaned = cleanText(value); if (cleaned && cleaned.length <= 120 && !target.includes(cleaned)) target.push(cleaned) }

function signalsFor(element: Element): FieldSignals {
  const primary: string[] = [], attributes: string[] = [], context: string[] = []
  const id = element.id
  if ('labels' in element) for (const label of [...((element as HTMLInputElement).labels || [])]) pushUnique(primary, label.textContent)
  if (id) pushUnique(primary, document.querySelector(`label[for="${CSS.escape(id)}"]`)?.textContent)
  pushUnique(primary, element.closest('label')?.textContent)
  pushUnique(primary, element.getAttribute('aria-label'))
  pushUnique(primary, element.getAttribute('placeholder'))
  for (const labelledBy of (element.getAttribute('aria-labelledby') || '').split(/\s+/).filter(Boolean)) pushUnique(primary, document.getElementById(labelledBy)?.textContent)
  for (const attribute of ['name', 'id', 'autocomplete', 'data-label', 'data-field', 'data-field-name', 'title']) pushUnique(attributes, element.getAttribute(attribute))

  let cursor: Element | null = element.parentElement
  for (let depth = 0; cursor && depth < 8; depth += 1, cursor = cursor.parentElement) {
    pushUnique(primary, cursor.previousElementSibling?.textContent)
    for (const sibling of [...cursor.children]) {
      if (sibling === element || sibling.contains(element)) continue
      const marker = `${sibling.tagName} ${sibling.className || ''} ${sibling.getAttribute('role') || ''}`.toLowerCase()
      if (/label|title|name|caption|legend/.test(marker)) pushUnique(primary, sibling.textContent)
    }
    for (const attribute of ['data-label', 'data-field', 'data-field-name', 'title', 'aria-label']) pushUnique(primary, cursor.getAttribute(attribute))
    const heading = cursor.querySelector(':scope > legend, :scope > h1, :scope > h2, :scope > h3, :scope > h4, :scope > [class*="section" i] > [class*="title" i]')
    pushUnique(context, heading?.textContent)
    const previousHeading = cursor.previousElementSibling?.matches('h1,h2,h3,h4,h5,h6,legend,[class*="title" i]') ? cursor.previousElementSibling.textContent : undefined
    pushUnique(context, previousHeading)
  }
  return { primary, attributes, context }
}
