import { readFile } from 'node:fs/promises'
const rootUrl = new URL('../', import.meta.url)

async function readJson(relativePath) {
  return JSON.parse(await readFile(new URL(relativePath, rootUrl), 'utf8'))
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function isMonth(value) {
  return typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

function validateProfile(profile) {
  for (const key of ['schemaVersion', 'profileId', 'basic', 'contact', 'education', 'fieldMeta']) {
    assert(key in profile, `Candidate Profile 缺少必填字段：${key}`)
  }
  assert(profile.schemaVersion === '0.1', 'Candidate Profile schemaVersion 必须为 0.1')
  assert(Array.isArray(profile.education), 'education 必须是数组')
  assert(typeof profile.fieldMeta === 'object' && profile.fieldMeta !== null, 'fieldMeta 必须是对象')

  const educationIds = new Set()
  for (const entry of profile.education) {
    assert(typeof entry.id === 'string' && entry.id.length > 0, '每条教育经历必须有稳定 id')
    assert(!educationIds.has(entry.id), `教育经历 id 重复：${entry.id}`)
    educationIds.add(entry.id)
    assert(typeof entry.school === 'string' && entry.school.length > 0, `教育经历 ${entry.id} 缺少 school`)
    for (const key of ['startDate', 'endDate', 'expectedGraduation']) {
      if (entry[key] !== undefined) assert(isMonth(entry[key]), `${entry.id}.${key} 必须为 YYYY-MM`)
    }
  }

  for (const [fieldPath, meta] of Object.entries(profile.fieldMeta)) {
    assert(fieldPath.length > 0, 'fieldMeta 不允许空路径')
    assert(['RESUME', 'MANUAL', 'FORM_HISTORY', 'AI_SUGGESTED'].includes(meta.source), `${fieldPath} 的 source 非法`)
    assert(['CONFIRMED', 'UNCONFIRMED', 'CONFLICT'].includes(meta.confirmationState), `${fieldPath} 的 confirmationState 非法`)
    assert(['NORMAL', 'PERSONAL', 'SENSITIVE', 'HIGHLY_SENSITIVE'].includes(meta.sensitivity), `${fieldPath} 的 sensitivity 非法`)
  }
}

const [profileSchema, registry, providerSchema, profile] = await Promise.all([
  readJson('candidate-profile.schema.json'),
  readJson('field-registry.json'),
  readJson('llm-provider-config.schema.json'),
  readJson('examples/candidate-profile.example.json')
])

assert(profileSchema.$schema.includes('2020-12'), 'Candidate Profile Schema 必须使用 JSON Schema 2020-12')
assert(providerSchema.properties.capabilities.required.includes('structuredOutput'), 'Provider 契约必须声明 structuredOutput 能力')
assert(Array.isArray(registry.fields) && registry.fields.length > 0, 'Field Registry 不能为空')

const fieldKeys = new Set()
for (const field of registry.fields) {
  assert(typeof field.key === 'string' && field.key.includes('.'), '每个字段必须使用分组.fieldName key')
  assert(!fieldKeys.has(field.key), `Field Registry key 重复：${field.key}`)
  fieldKeys.add(field.key)
  assert(Array.isArray(field.aliases) && field.aliases.length > 0, `${field.key} 必须至少有一个 alias`)
  assert(['NORMAL', 'PERSONAL', 'SENSITIVE', 'HIGHLY_SENSITIVE'].includes(field.sensitivity), `${field.key} sensitivity 非法`)
  assert(['ALLOW', 'CONFIRM', 'DENY'].includes(field.autofillPolicy), `${field.key} autofillPolicy 非法`)
  assert(!(field.sensitivity === 'SENSITIVE' && field.autofillPolicy === 'ALLOW'), `${field.key} 是敏感字段，不能默认自动填写`)
}

validateProfile(profile)
console.log(`Contracts validated: ${registry.fields.length} field definitions, ${profile.education.length} education record(s).`)
