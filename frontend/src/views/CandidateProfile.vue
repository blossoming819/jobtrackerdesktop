<template>
  <section class="profile-page">
    <div class="page-heading">
      <div><p class="eyebrow">ApplyMate</p><h1>个人档案</h1><p>作为简历解析和浏览器填写的唯一信息源。保存时会自动创建可恢复的版本快照。</p></div>
      <div class="profile-actions"><el-tag type="info">Schema {{ profile.schemaVersion }}</el-tag><el-tag type="success">修订 {{ profile.revision }}</el-tag><el-button :loading="loading" @click="load">重新读取</el-button><el-button type="primary" :loading="saving" @click="save">保存档案</el-button></div>
    </div>

    <el-alert :title="serviceOnline ? `当前已连接：${runtimeLabel}。档案、快照与扩展配对均保存在该端；Web(MySQL) 与桌面端(H2) 默认不同步。` : '本地 JobTracker 服务未连接：请启动后端或桌面端。'" :type="serviceOnline ? 'success' : 'warning'" :closable="false" show-icon />

    <el-form label-position="top" class="profile-form">
      <el-card class="form-card"><template #header><div class="card-header"><span>基本信息</span><small>用于姓名等通用字段匹配</small></div></template>
        <div class="form-grid three"><el-form-item label="中文姓名"><el-input v-model="form.basic.nameCn" placeholder="请输入姓名" /></el-form-item><el-form-item label="性别"><el-select v-model="form.basic.gender"><el-option label="未填写" value="UNSPECIFIED" /><el-option label="男" value="MALE" /><el-option label="女" value="FEMALE" /></el-select></el-form-item><el-form-item label="当前所在城市"><el-input v-model="form.contact.currentCity" placeholder="例如：杭州" /></el-form-item></div>
      </el-card>
      <el-card class="form-card"><template #header><div class="card-header"><span>联系方式</span><small>手机号、邮箱等个人信息，扩展填写前会要求确认</small></div></template>
        <div class="form-grid"><el-form-item label="手机号码"><el-input v-model="form.contact.phone" placeholder="请输入手机号" /></el-form-item><el-form-item label="邮箱"><el-input v-model="form.contact.email" placeholder="name@example.com" /></el-form-item></div>
      </el-card>
      <el-card class="form-card"><template #header><div class="card-header"><div><span>教育经历</span><small>按最高或最常用经历填写，浏览器扩展默认使用第一条</small></div><el-button text type="primary" @click="addEducation">添加教育经历</el-button></div></template>
        <el-empty v-if="form.education.length === 0" description="尚未添加教育经历" :image-size="64"><el-button type="primary" plain @click="addEducation">添加教育经历</el-button></el-empty>
        <div v-for="(education, index) in form.education" :key="education.id" class="education-block"><div class="education-title"><strong>教育经历 {{ index + 1 }}</strong><el-button text type="danger" @click="removeEducation(index)">删除</el-button></div><div class="form-grid three"><el-form-item label="学校名称" required><el-input v-model="education.school" placeholder="例如：北京大学" /></el-form-item><el-form-item label="学历"><el-select v-model="education.degree" placeholder="请选择"><el-option label="本科" value="BACHELOR" /><el-option label="硕士" value="MASTER" /><el-option label="博士" value="DOCTOR" /></el-select></el-form-item><el-form-item label="专业"><el-input v-model="education.major" placeholder="例如：计算机科学与技术" /></el-form-item><el-form-item label="学院"><el-input v-model="education.college" placeholder="选填" /></el-form-item><el-form-item label="入学时间"><el-input v-model="education.startDate" placeholder="YYYY-MM" /></el-form-item><el-form-item label="毕业时间"><el-input v-model="education.expectedGraduation" placeholder="YYYY-MM" /></el-form-item></div></div>
      </el-card>
    </el-form>

    <el-card class="snapshot-card"><template #header><div class="card-header"><span>版本快照</span><small>恢复快照会生成一条新的修订记录</small></div></template><el-empty v-if="snapshots.length === 0" description="尚未保存档案快照" :image-size="72" /><el-table v-else :data="snapshots" size="small"><el-table-column prop="revision" label="修订" width="100"><template #default="{ row }">v{{ row.revision }}</template></el-table-column><el-table-column prop="createdAt" label="保存时间" min-width="180" /><el-table-column label="操作" width="120"><template #default="{ row }"><el-button text type="primary" @click="restore(row)">恢复</el-button></template></el-table-column></el-table></el-card>
    <el-card class="pairing-card"><template #header><div class="card-header"><span>浏览器扩展配对</span><small>仅批准你刚刚在 Chrome 中发起的请求</small></div></template><el-empty v-if="pairingRequests.length === 0" description="没有待批准的扩展请求" :image-size="64" /><el-table v-else :data="pairingRequests" size="small"><el-table-column prop="displayName" label="扩展" min-width="170" /><el-table-column prop="extensionId" label="Extension ID" min-width="260" show-overflow-tooltip /><el-table-column prop="createdTime" label="请求时间" min-width="180" /><el-table-column label="操作" width="120"><template #default="{ row }"><el-button text type="primary" :loading="approvingId === row.id" @click="approvePairing(row)">批准</el-button></template></el-table-column></el-table></el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { candidateProfileApi, pairingApi } from '../api'
import http from '../api/http'
import type { CandidateProfileResponse, ExtensionPairing, ProfileSnapshot } from '../types'

type Education = { id: string, school: string, degree: string, major: string, college: string, startDate: string, expectedGraduation: string }
const emptyEducation = (): Education => ({ id: `edu_${crypto.randomUUID()}`, school: '', degree: '', major: '', college: '', startDate: '', expectedGraduation: '' })
const loading = ref(false); const saving = ref(false); const snapshots = ref<ProfileSnapshot[]>([]); const pairingRequests = ref<ExtensionPairing[]>([]); const approvingId = ref<number>(); const serviceOnline = ref(false); const runtimeLabel = ref('正在识别服务类型')
const profile = ref<CandidateProfileResponse>({ profileId: 'default', schemaVersion: '0.1', revision: 0, content: {} })
const form = reactive({ basic: { nameCn: '', gender: 'UNSPECIFIED' }, contact: { phone: '', email: '', currentCity: '' }, education: [] as Education[] })

function bind(content: Record<string, any>) { Object.assign(form.basic, { nameCn: content.basic?.nameCn || '', gender: content.basic?.gender || 'UNSPECIFIED' }); Object.assign(form.contact, { phone: content.contact?.phone || '', email: content.contact?.email || '', currentCity: content.contact?.currentCity || '' }); form.education = Array.isArray(content.education) ? content.education.map((item: any) => ({ ...emptyEducation(), ...item })) : [] }
async function load() { loading.value = true; try { profile.value = await candidateProfileApi.get(); bind(profile.value.content); snapshots.value = await candidateProfileApi.snapshots(); pairingRequests.value = await pairingApi.pending() } finally { loading.value = false } }
function addEducation() { form.education.push(emptyEducation()) }
function removeEducation(index: number) { form.education.splice(index, 1) }
async function save() { const incomplete = form.education.find(item => !item.school.trim()); if (incomplete) return ElMessage.warning('已添加的教育经历必须填写学校名称'); saving.value = true; try { const content = { ...profile.value.content, basic: { ...form.basic }, contact: { ...form.contact }, education: form.education.map(item => ({ ...item })), fieldMeta: profile.value.content.fieldMeta || {} }; profile.value = await candidateProfileApi.save(content); bind(profile.value.content); snapshots.value = await candidateProfileApi.snapshots(); ElMessage.success(`档案已保存为修订 v${profile.value.revision}`) } finally { saving.value = false } }
async function restore(snapshot: ProfileSnapshot) { await ElMessageBox.confirm(`恢复 v${snapshot.revision} 后会创建一个新的修订版本，是否继续？`, '恢复档案快照', { type: 'warning' }); profile.value = await candidateProfileApi.restore(snapshot.id); bind(profile.value.content); snapshots.value = await candidateProfileApi.snapshots(); ElMessage.success(`已恢复为新修订 v${profile.value.revision}`) }
async function approvePairing(request: ExtensionPairing) { await ElMessageBox.confirm(`确认批准 Chrome 扩展“${request.displayName || request.extensionId}”吗？仅应批准你本人刚发起的请求。`, '批准扩展配对', { type: 'warning' }); approvingId.value = request.id; try { await pairingApi.approve(request.id); pairingRequests.value = await pairingApi.pending(); ElMessage.success('配对已批准，请回到 Chrome 扩展完成令牌领取。') } finally { approvingId.value = undefined } }
onMounted(load); onMounted(async () => { try { const health = await http.get('/applymate/v1/health') as { runtime?: string, dataStore?: string }; runtimeLabel.value = health.runtime === 'DESKTOP' ? '桌面端（H2）' : health.runtime === 'WEB' ? 'Web 端（MySQL）' : `未知服务（${health.dataStore || '未识别数据库'}）`; serviceOnline.value = true } catch { serviceOnline.value = false } })
</script>

<style scoped>
.profile-page { display: grid; gap: 18px; max-width: 1180px; }.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }.page-heading h1 { margin: 4px 0 8px; font-size: 28px; }.page-heading p { margin: 0; color: var(--el-text-color-secondary); line-height: 1.7; }.eyebrow { color: var(--el-color-primary) !important; font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }.profile-actions { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }.profile-form { display: grid; gap: 18px; }.form-card, .snapshot-card, .pairing-card { border-radius: 14px; }.card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; font-weight: 700; }.card-header small { margin-left: 8px; color: var(--el-text-color-secondary); font-weight: 400; }.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; }.form-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }.education-block { padding: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; }.education-block + .education-block { margin-top: 12px; }.education-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }.education-block :deep(.el-form-item) { margin-bottom: 10px; } @media (max-width: 760px) { .page-heading { display: grid; }.profile-actions { justify-content: flex-start; }.form-grid, .form-grid.three { grid-template-columns: 1fr; } }
</style>
