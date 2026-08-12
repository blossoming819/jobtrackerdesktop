<template>
  <h1 class="page-title">Dashboard</h1>
  <div class="stat-grid">
    <div class="stat"><span>总投递数量</span><strong>{{ data.totalApplications || 0 }}</strong></div>
    <div class="stat"><span>面试数量</span><strong>{{ data.interviewCount || 0 }}</strong></div>
    <div class="stat"><span>Offer 数量</span><strong>{{ data.offerCount || 0 }}</strong></div>
    <div class="stat"><span>今日日程</span><strong>{{ data.todayReminders?.length || 0 }}</strong></div>
  </div>
  <div class="chart-grid">
    <div class="panel"><div ref="pieRef" class="chart"></div></div>
    <div class="panel"><div ref="lineRef" class="chart"></div></div>
    <div class="panel"><div ref="barRef" class="chart"></div></div>
    <div class="panel">
      <div class="section-heading">
        <div>
          <h3>今日日程</h3>
          <span>只展示未完成事项，完成或取消后会从这里移除。</span>
        </div>
        <el-button link type="primary" @click="$router.push('/reminder')">查看日程</el-button>
      </div>
      <div v-if="data.todayReminders?.length" class="dashboard-schedule-list">
        <button
          v-for="item in data.todayReminders"
          :key="item.id"
          class="dashboard-schedule-item"
          :class="scheduleClass(item)"
          @click="$router.push('/reminder?quick=pending')"
        >
          <span>{{ scheduleTimeRange(item) }}</span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.scheduleType || '自定义' }} · {{ item.priority || '中' }} · {{ item.importance || '普通' }}</p>
          </div>
        </button>
      </div>
      <div v-else class="empty-state dashboard-schedule-empty">今天没有未完成日程</div>
    </div>
  </div>
  <div class="panel">
    <div class="section-heading">
      <div><h3>最近投递记录</h3><span>展示最近五条；按投递时间倒序排列，未填写时用最近更新时间兜底。</span></div>
      <el-button text @click="recentExpanded = !recentExpanded">{{ recentExpanded ? '收起' : '展开' }}</el-button>
    </div>
    <el-table v-show="recentExpanded" :data="(data.recentApplications || []).slice(0, 5)">
      <el-table-column prop="companyName" label="公司" min-width="130" show-overflow-tooltip />
      <el-table-column prop="positionName" label="岗位" min-width="170" show-overflow-tooltip />
      <el-table-column prop="positionType" label="岗位类别" min-width="170">
        <template #default="{ row }">
          <el-tooltip :content="row.positionType || '-'" placement="top" :disabled="!row.positionType">
            <div class="multi-tag-cell">
              <el-tag v-for="tag in visibleMultiTags(row.positionType)" :key="tag" class="category-tag" :style="tagStyle(tag)" size="small">{{ tag }}</el-tag>
              <el-tag v-if="hiddenMultiTagCount(row.positionType)" class="more-tag" size="small" type="info">+{{ hiddenMultiTagCount(row.positionType) }}</el-tag>
              <span v-if="!row.positionType" class="muted">-</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="recruitmentType" label="投递批次" width="110" show-overflow-tooltip />
      <el-table-column prop="resumeCategory" label="简历类别" min-width="140">
        <template #default="{ row }">
          <el-tooltip :content="row.resumeCategory || '-'" placement="top" :disabled="!row.resumeCategory">
            <div class="multi-tag-cell">
              <el-tag v-for="tag in visibleMultiTags(row.resumeCategory)" :key="tag" class="category-tag" :style="tagStyle(tag)" size="small">{{ tag }}</el-tag>
              <el-tag v-if="hiddenMultiTagCount(row.resumeCategory)" class="more-tag" size="small" type="info">+{{ hiddenMultiTagCount(row.resumeCategory) }}</el-tag>
              <span v-if="!row.resumeCategory" class="muted">-</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="状态" width="120">
        <template #default="{ row }">
          <span class="status-pill" :class="statusMeta(row.currentStatus).className">
            <el-icon><component :is="statusMeta(row.currentStatus).icon" /></el-icon>
            <span>{{ row.currentStatus }}</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="appliedTime" label="投递时间" width="170">
        <template #default="{ row }">{{ formatDate(row.appliedTime) || '未填写' }}</template>
      </el-table-column>
      <el-table-column prop="source" label="来源" width="100" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChatDotRound, CircleCheck, Collection, EditPen, Medal, Promotion, Star, User, Warning } from '@element-plus/icons-vue'
import { dashboardApi } from '../api'
import { formatDateTime, formatTime } from '../utils/time'

const pieRef = ref<HTMLDivElement>()
const lineRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()
const data = ref<any>({})
const recentExpanded = ref(true)
const chartInstances: echarts.ECharts[] = []
let chartResizeObserver: ResizeObserver | undefined
let chartResizeFrame: number | undefined
let chartSettleTimer: number | undefined

onMounted(async () => {
  data.value = await dashboardApi.overview()
  await nextTick()
  renderCharts()
  trackChartSizes()
})

onBeforeUnmount(() => {
  chartResizeObserver?.disconnect()
  window.removeEventListener('resize', scheduleChartResize)
  if (chartResizeFrame !== undefined) window.cancelAnimationFrame(chartResizeFrame)
  if (chartSettleTimer !== undefined) window.clearTimeout(chartSettleTimer)
  chartInstances.splice(0).forEach((chart) => chart.dispose())
})

function renderCharts() {
  const status = data.value.statusCount || {}
  const chartText = '#3f3d39'
  const chartMuted = '#625d56'
  const chartGrid = '#e7e2d9'
  const chartTitleFont = 'Georgia, "Times New Roman", "Noto Serif SC", "Songti SC", serif'
  const chartPalette = ['#c15f3c', '#7f8f73', '#d2a24c', '#7b829b', '#a66e64', '#5f837d', '#b28a6a']
  const pieChart = echarts.init(pieRef.value!)
  chartInstances.push(pieChart)
  pieChart.setOption({
    color: chartPalette,
    title: { text: '投递状态分布', left: 0, top: 0, textStyle: { color: chartText, fontFamily: chartTitleFont, fontSize: 18, fontWeight: 600 } },
    tooltip: { trigger: 'item' },
    legend: {
      type: 'scroll',
      top: 2,
      left: 158,
      right: 8,
      itemWidth: 18,
      itemHeight: 10,
      pageButtonItemGap: 5,
      pageIconSize: 11,
      pageTextStyle: { color: chartMuted },
      textStyle: { color: chartMuted }
    },
    series: [{
      type: 'pie',
      center: ['50%', '59%'],
      radius: ['42%', '66%'],
      padAngle: 2,
      itemStyle: { borderColor: '#fffdf9', borderWidth: 2, borderRadius: 5 },
      label: { color: chartMuted },
      data: Object.entries(status).map(([name, value]) => ({ name, value }))
    }]
  })
  const trend = data.value.weeklyTrend || {}
  const lineChart = echarts.init(lineRef.value!)
  chartInstances.push(lineChart)
  lineChart.setOption({
    color: [chartPalette[0]],
    title: { text: '每周投递趋势', left: 0, top: 0, textStyle: { color: chartText, fontFamily: chartTitleFont, fontSize: 18, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 20, top: 58, bottom: 34 },
    xAxis: { type: 'category', data: Object.keys(trend), axisLine: { lineStyle: { color: chartGrid } }, axisLabel: { color: chartMuted } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: chartGrid, type: 'dashed' } }, axisLabel: { color: chartMuted } },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { width: 3 },
      areaStyle: { color: 'rgba(193, 95, 60, .10)' },
      data: Object.values(trend)
    }]
  })
  const company = data.value.companyCount || {}
  const barChart = echarts.init(barRef.value!)
  chartInstances.push(barChart)
  barChart.setOption({
    color: [chartPalette[1]],
    title: { text: '公司投递数量', left: 0, top: 0, textStyle: { color: chartText, fontFamily: chartTitleFont, fontSize: 18, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 20, top: 58, bottom: 34 },
    xAxis: { type: 'category', data: Object.keys(company), axisLine: { lineStyle: { color: chartGrid } }, axisLabel: { color: chartMuted } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: chartGrid, type: 'dashed' } }, axisLabel: { color: chartMuted } },
    series: [{ type: 'bar', barMaxWidth: 32, itemStyle: { borderRadius: [6, 6, 0, 0] }, data: Object.values(company) }]
  })
}

function trackChartSizes() {
  const elements = [pieRef.value, lineRef.value, barRef.value].filter((item): item is HTMLDivElement => Boolean(item))
  if ('ResizeObserver' in window) {
    chartResizeObserver = new ResizeObserver(scheduleChartResize)
    elements.forEach((element) => chartResizeObserver?.observe(element))
  }
  window.addEventListener('resize', scheduleChartResize)
  document.fonts?.ready.then(scheduleChartResize)
  scheduleChartResize()
  chartSettleTimer = window.setTimeout(scheduleChartResize, 360)
}

function scheduleChartResize() {
  if (chartResizeFrame !== undefined) window.cancelAnimationFrame(chartResizeFrame)
  chartResizeFrame = window.requestAnimationFrame(() => {
    chartResizeFrame = window.requestAnimationFrame(() => {
      chartResizeFrame = undefined
      chartInstances.forEach((chart) => chart.resize())
    })
  })
}

function formatDate(value?: string) {
  return formatDateTime(value)
}

function formatClock(value?: string) {
  return formatTime(value)
}

function scheduleTimeRange(row: any) {
  const start = formatClock(row.remindTime)
  if (!row.endTime) return start
  if (row.endTime <= row.remindTime) return `${start} - 结束时间异常`
  return row.endTime.slice(0, 10) === row.remindTime?.slice(0, 10)
    ? `${start} - ${formatClock(row.endTime)}`
    : `${start} - ${formatDate(row.endTime)}`
}

function scheduleClass(item: any) {
  if (item.status === '已完成') return 'schedule-done'
  if (item.status === '已取消') return 'schedule-cancelled'
  const priorityClass: Record<string, string> = {
    低: 'schedule-low',
    中: 'schedule-medium',
    高: 'schedule-high',
    紧急: 'schedule-urgent'
  }
  return priorityClass[item.priority || '中'] || 'schedule-medium'
}

function splitMultiValue(value?: string) {
  return value ? value.split(/[、,，;；]/).map(item => item.trim()).filter(Boolean) : []
}

function visibleMultiTags(value?: string) {
  return splitMultiValue(value).slice(0, 2)
}

function hiddenMultiTagCount(value?: string) {
  return Math.max(splitMultiValue(value).length - 2, 0)
}

function tagStyle(value: string) {
  const palettes = [
    { color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
    { color: '#047857', bg: '#ecfdf5', border: '#a7f3d0' },
    { color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
    { color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe' },
    { color: '#be123c', bg: '#fff1f2', border: '#fecdd3' },
    { color: '#0e7490', bg: '#ecfeff', border: '#a5f3fc' }
  ]
  const hash = Array.from(value).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  const item = palettes[hash % palettes.length]
  return { color: item.color, backgroundColor: item.bg, borderColor: item.border }
}

function statusMeta(status?: string) {
  const map: Record<string, any> = {
    收藏: { className: 'status-favorite', icon: Star },
    待投递: { className: 'status-pending', icon: Collection },
    已投递: { className: 'status-submitted', icon: Promotion },
    笔试: { className: 'status-written', icon: EditPen },
    一面: { className: 'status-interview', icon: ChatDotRound },
    二面: { className: 'status-interview', icon: ChatDotRound },
    三面: { className: 'status-interview', icon: ChatDotRound },
    四面: { className: 'status-interview', icon: ChatDotRound },
    主管面: { className: 'status-interview', icon: User },
    'HR 面': { className: 'status-interview', icon: User },
    面试中: { className: 'status-interview', icon: ChatDotRound },
    Offer: { className: 'status-offer', icon: Medal },
    淘汰: { className: 'status-rejected', icon: Warning }
  }
  return map[status || ''] || { className: 'status-default', icon: CircleCheck }
}
</script>
