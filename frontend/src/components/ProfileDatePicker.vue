<template>
  <el-popover v-model:visible="visible" trigger="click" placement="bottom-start" :width="336" @show="preparePicker">
    <template #reference>
      <el-input :model-value="displayValue" readonly clearable placeholder="选择月份或日期" @clear="clearValue">
        <template #suffix><el-icon><Calendar /></el-icon></template>
      </el-input>
    </template>

    <div v-if="phase === 'month'" class="month-panel">
      <div class="picker-header">
        <el-button text size="small" @click="viewYear -= 1">‹</el-button>
        <strong>{{ viewYear }} 年</strong>
        <el-button text size="small" @click="viewYear += 1">›</el-button>
      </div>
      <p class="picker-hint">选择月份后即可关闭；如需精确到日，可继续选择日期。</p>
      <div class="month-grid">
        <button v-for="month in 12" :key="month" type="button" :class="{ selected: selectedMonth === month && selectedYear === viewYear }" @click="selectMonth(month)">
          {{ month }} 月
        </button>
      </div>
    </div>

    <div v-else class="day-panel">
      <div class="day-panel-title">
        <div><strong>{{ selectedYear }} 年 {{ selectedMonth }} 月</strong><small>当前已保存到月；可继续点选具体日期</small></div>
        <el-button text size="small" @click="phase = 'month'">重新选月</el-button>
      </div>
      <el-calendar v-model="calendarDate" @input="selectDay">
        <template #header><span /></template>
        <template #date-cell="{ data }"><span>{{ Number(data.day.slice(-2)) }}</span></template>
      </el-calendar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { Calendar } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{ modelValue?: string }>(), { modelValue: '' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const visible = ref(false)
const phase = ref<'month' | 'day'>('month')
const today = new Date()
const viewYear = ref(today.getFullYear())
const selectedYear = ref(today.getFullYear())
const selectedMonth = ref(today.getMonth() + 1)
const calendarDate = ref(today)

const displayValue = computed(() => {
  if (!props.modelValue) return ''
  const [year, month, day] = props.modelValue.split('-')
  return day ? `${year}年${month}月${day}日` : `${year}年${month}月`
})

function preparePicker() {
  const [year, month] = props.modelValue.split('-').map(Number)
  const base = year && month ? new Date(year, month - 1, 1) : new Date()
  viewYear.value = base.getFullYear()
  selectedYear.value = base.getFullYear()
  selectedMonth.value = base.getMonth() + 1
  calendarDate.value = base
  phase.value = 'month'
}

function selectMonth(month: number) {
  selectedYear.value = viewYear.value
  selectedMonth.value = month
  calendarDate.value = new Date(viewYear.value, month - 1, 1)
  emit('update:modelValue', `${viewYear.value}-${String(month).padStart(2, '0')}`)
  phase.value = 'day'
}

function selectDay(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  emit('update:modelValue', `${year}-${month}-${day}`)
  visible.value = false
}

function clearValue() {
  emit('update:modelValue', '')
  visible.value = false
}
</script>

<style scoped>
.picker-header,.day-panel-title{display:flex;align-items:center;justify-content:space-between;gap:10px}.picker-hint{margin:4px 0 12px;color:var(--el-text-color-secondary);font-size:12px;line-height:1.5}.month-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.month-grid button{padding:8px 5px;border:1px solid var(--el-border-color-light);border-radius:7px;background:var(--el-fill-color-blank);color:var(--el-text-color-regular);cursor:pointer}.month-grid button:hover,.month-grid button.selected{border-color:var(--el-color-primary);color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.day-panel-title>div{display:grid;gap:2px}.day-panel-title small{color:var(--el-text-color-secondary);font-size:11px}.day-panel :deep(.el-calendar){--el-calendar-cell-width:38px}.day-panel :deep(.el-calendar__header){display:none}.day-panel :deep(.el-calendar__body){padding:8px 0 0}.day-panel :deep(.el-calendar-table thead th){padding:5px 0;font-size:12px}.day-panel :deep(.el-calendar-table .el-calendar-day){height:34px;padding:7px;text-align:center;border-radius:6px}.day-panel :deep(.el-calendar-table td){border:0}.day-panel :deep(.el-calendar-table td.is-selected .el-calendar-day){color:var(--el-color-primary);background:var(--el-color-primary-light-9)}
</style>
