<template>
  <h1 class="page-title">笔记管理</h1>

  <div class="notes-layout">
    <section class="notes-sidebar panel">
      <div class="toolbar notes-toolbar">
        <el-button type="primary" @click="openCreate('FOLDER')">
          <el-icon><FolderAdd /></el-icon>新建文件夹
        </el-button>
        <el-button @click="openCreate('NOTE')">
          <el-icon><DocumentAdd /></el-icon>新建笔记
        </el-button>
        <el-upload :http-request="uploadNote" :show-file-list="false" accept=".md,.markdown,.txt,.pdf,.doc,.docx">
          <el-button>
            <el-icon><Upload /></el-icon>上传笔记
          </el-button>
        </el-upload>
      </div>

      <button
        class="notes-root-drop"
        :class="{ active: canMoveSelectedToRoot || draggingCanMoveToRoot, dragover: rootDragOver }"
        :disabled="!canMoveSelectedToRoot && !draggingCanMoveToRoot"
        @click="moveSelectedToRoot"
        @dragover.prevent="handleRootDragOver"
        @dragleave="rootDragOver = false"
        @drop.prevent="dropToRoot"
      >
        移出到根目录
      </button>

      <el-tree
        class="notes-tree"
        :data="treeData"
        node-key="id"
        draggable
        default-expand-all
        :expand-on-click-node="false"
        :allow-drop="allowDrop"
        @node-click="selectNode"
        @node-drop="handleDrop"
        @node-drag-start="handleDragStart"
        @node-drag-end="handleDragEnd"
      >
        <template #default="{ data }">
          <span class="notes-node" :class="{ selected: data.id === selected?.id }">
            <el-icon><component :is="data.type === 'FOLDER' ? FolderOpened : Document" /></el-icon>
            <span :title="data.title">{{ data.title }}</span>
          </span>
        </template>
      </el-tree>
    </section>

    <section class="notes-editor panel">
      <template v-if="selected">
        <div class="notes-editor-head">
          <div>
            <span class="note-type-tag" :class="fileTypeMeta(selected).className">
              <el-icon><component :is="fileTypeMeta(selected).icon" /></el-icon>{{ fileTypeMeta(selected).label }}
            </span>
            <h2 :title="selected.title">{{ selected.title }}</h2>
          </div>
          <div class="notes-actions">
            <el-button @click="openInfoDialog">
              <el-icon><Edit /></el-icon>文件信息
            </el-button>
            <el-button v-if="!editing && isEditableNote(selected)" @click="startEdit">
              <el-icon><Edit /></el-icon>编辑内容
            </el-button>
            <el-button v-if="canMoveSelectedToRoot" @click="moveSelectedToRoot">
              移到根目录
            </el-button>
            <el-button v-if="selected.type === 'NOTE'" @click="downloadSelected">
              <el-icon><Download /></el-icon>下载
            </el-button>
            <el-button type="danger" @click="removeSelected">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </div>
        </div>

        <template v-if="!editing">
          <div v-if="selected.type === 'NOTE'" class="note-content-scroll note-preview-area">
            <iframe v-if="isPdfNote(selected)" class="note-file-preview" :src="noteItemApi.previewUrl(selected.id!)"></iframe>
            <div v-else-if="isEditableNote(selected)" class="markdown-preview note-preview-only" v-html="notePreview"></div>
            <pre v-else-if="isWordNote(selected)" class="note-plain-preview note-word-preview">{{ selected.content || '该 Word 文件暂时未提取到可预览文本，可下载后查看原文件。' }}</pre>
            <pre v-else class="note-plain-preview">{{ selected.content || '该文件暂时只能下载查看，当前未提取到可预览文本。' }}</pre>
          </div>
          <div v-else class="folder-summary folder-summary-fill">
            <el-icon><FolderOpened /></el-icon>
            <strong>{{ childCount(selected.id) }}</strong>
            <span>个子项目</span>
          </div>
        </template>

        <template v-else>
          <div class="notes-content-editor">
            <div class="markdown-layout notes-markdown">
              <el-input v-model="selected.content" type="textarea" resize="none" />
              <div class="markdown-preview" v-html="notePreview"></div>
            </div>
          </div>
          <div class="notes-savebar">
            <span class="notes-save-hint">Ctrl+S 快速保存</span>
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveSelected">
              <el-icon><Check /></el-icon>保存内容
            </el-button>
          </div>
        </template>
      </template>

      <div v-else class="empty-state">
        <el-icon><Memo /></el-icon>
        <p>选择左侧文件夹或笔记开始管理</p>
      </div>
    </section>
  </div>

  <el-dialog v-model="infoVisible" title="文件信息" width="720px">
    <el-form label-width="110px" class="notes-form">
      <el-form-item label="名称">
        <el-input v-model="infoForm.title" />
      </el-form-item>
      <el-form-item label="所在位置">
        <el-select v-model="infoForm.parentId" filterable placeholder="选择所在位置">
          <el-option :value="null" label="根目录" />
          <el-option
            v-for="folder in infoFolderOptions"
            :key="folder.id"
            :label="folder.path"
            :value="folder.id"
            :disabled="folder.disabled"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="infoForm.type === 'FOLDER'" label="本地保存位置">
        <div class="path-row">
          <el-input v-model="infoForm.filePath" placeholder="不填则默认保存在普通笔记目录或父文件夹下" />
          <el-button :icon="FolderOpened" @click="chooseFolderPathForInfo">选择目录</el-button>
        </div>
      </el-form-item>
      <el-form-item v-if="infoForm.type === 'NOTE'" label="文件名">
        <span>{{ infoForm.fileName || '-' }}</span>
      </el-form-item>
      <el-form-item label="创建时间">
        <span>{{ formatDate(infoForm.createdTime) || '-' }}</span>
      </el-form-item>
      <el-form-item label="更新时间">
        <span>{{ formatDate(infoForm.updatedTime) || '-' }}</span>
      </el-form-item>
      <el-form-item label="本地路径">
        <span class="path-text">{{ infoForm.filePath || '-' }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="infoVisible = false">取消</el-button>
      <el-button type="primary" @click="saveInfo">保存信息</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="createVisible" :title="createType === 'FOLDER' ? '新建文件夹' : '新建笔记'" width="640px">
    <el-form label-width="110px">
      <el-form-item label="名称">
        <el-input v-model="createForm.title" />
      </el-form-item>
      <el-form-item label="所在位置">
        <el-select v-model="createForm.parentId" filterable placeholder="选择所在位置">
          <el-option :value="null" label="根目录" />
          <el-option v-for="folder in folderOptions" :key="folder.id" :label="folder.path" :value="folder.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="createType === 'FOLDER'" label="本地保存位置">
        <div class="path-row">
          <el-input v-model="createForm.filePath" placeholder="不填则默认保存在普通笔记目录或父文件夹下" />
          <el-button :icon="FolderOpened" @click="chooseFolderPathForCreate">选择目录</el-button>
        </div>
      </el-form-item>
      <el-form-item v-if="createType === 'NOTE'" label="内容">
        <el-input v-model="createForm.content" type="textarea" :rows="8" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" @click="createItem">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, Document, DocumentAdd, Download, Edit, Files, FolderAdd, FolderOpened, Memo, Tickets, Upload } from '@element-plus/icons-vue'
import { noteItemApi } from '../api'
import type { NoteItem } from '../types'
import { formatDateTime } from '../utils/time'

type FolderOption = { id: number; path: string; disabled?: boolean }

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const rows = ref<NoteItem[]>([])
const selected = ref<NoteItem | null>(null)
const dragging = ref<NoteItem | null>(null)
const rootDragOver = ref(false)
const editing = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const infoVisible = ref(false)
const createType = ref<'FOLDER' | 'NOTE'>('FOLDER')
const createForm = reactive<Partial<NoteItem>>({ title: '', parentId: null, content: '', filePath: '' })
const infoForm = reactive<Partial<NoteItem>>({})

const treeData = computed(() => buildTree(rows.value))
const folderOptions = computed(() => buildFolderOptions(rows.value))
const canMoveSelectedToRoot = computed(() => Boolean(selected.value?.id && selected.value.parentId))
const draggingCanMoveToRoot = computed(() => Boolean(dragging.value?.id && dragging.value.parentId))
const selectableFolderOptions = computed(() => selectableFoldersFor(selected.value))
const infoFolderOptions = computed(() => selectableFoldersFor(infoForm as NoteItem))
const notePreview = computed(() => md.render(selected.value?.content || ''))

onMounted(() => {
  load()
  window.addEventListener('keydown', handleSaveShortcut)
})

onBeforeUnmount(() => window.removeEventListener('keydown', handleSaveShortcut))

async function load() {
  rows.value = await noteItemApi.list() as unknown as NoteItem[]
  const selectedId = selected.value?.id
  if (selectedId) {
    selected.value = clone(rows.value.find((item) => item.id === selectedId) || null)
  }
}

function buildTree(items: NoteItem[]) {
  const map = new Map<number, NoteItem>()
  const roots: NoteItem[] = []
  items.forEach((item) => map.set(item.id!, { ...item, children: [] }))
  map.forEach((item) => {
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children!.push(item)
    } else {
      roots.push(item)
    }
  })
  sortTree(roots)
  return roots
}

function sortTree(items: NoteItem[]) {
  items.sort((a, b) => {
    if (a.type !== b.type) return a.type === 'FOLDER' ? -1 : 1
    return a.title.localeCompare(b.title, 'zh-Hans-CN')
  })
  items.forEach((item) => item.children?.length && sortTree(item.children))
}

function buildFolderOptions(items: NoteItem[]) {
  const tree = buildTree(items.filter((item) => item.type === 'FOLDER'))
  const options: FolderOption[] = []
  const walk = (nodes: NoteItem[], prefix = '') => {
    nodes.forEach((node) => {
      const path = `${prefix}${node.title}`
      options.push({ id: node.id!, path })
      walk(node.children || [], `${path} / `)
    })
  }
  walk(tree)
  return options
}

function selectableFoldersFor(current?: Partial<NoteItem> | null) {
  if (!current || current.type !== 'FOLDER') return folderOptions.value
  const descendantIds = new Set<number>()
  collectDescendants(current.id!, rows.value, descendantIds)
  return folderOptions.value.map((folder) => ({
    ...folder,
    disabled: folder.id === current.id || descendantIds.has(folder.id)
  }))
}

function collectDescendants(parentId: number, items: NoteItem[], result: Set<number>) {
  items.filter((item) => item.parentId === parentId).forEach((item) => {
    if (item.id) {
      result.add(item.id)
      collectDescendants(item.id, items, result)
    }
  })
}

function selectNode(data: NoteItem) {
  selected.value = clone(data)
  editing.value = false
}

function startEdit() {
  if (!selected.value || !isEditableNote(selected.value)) {
    ElMessage.info('该格式只支持预览和下载')
    return
  }
  editing.value = true
}

function cancelEdit() {
  const selectedId = selected.value?.id
  selected.value = clone(rows.value.find((item) => item.id === selectedId) || null)
  editing.value = false
}

function openInfoDialog() {
  if (!selected.value) return
  Object.assign(infoForm, clone(selected.value))
  infoVisible.value = true
}

function openCreate(type: 'FOLDER' | 'NOTE') {
  createType.value = type
  createForm.title = ''
  createForm.content = type === 'NOTE' ? '# 新笔记\n\n' : ''
  createForm.filePath = ''
  createForm.parentId = selected.value?.type === 'FOLDER' ? selected.value.id : selected.value?.parentId ?? null
  createVisible.value = true
}

async function createItem() {
  if (!createForm.title?.trim()) {
    ElMessage.warning('请先填写名称')
    return
  }
  if (createType.value === 'FOLDER') {
    await noteItemApi.createFolder({ title: createForm.title, parentId: createForm.parentId, filePath: createForm.filePath })
  } else {
    await noteItemApi.createNote({ title: createForm.title, parentId: createForm.parentId, content: createForm.content })
  }
  createVisible.value = false
  await load()
  ElMessage.success('已创建')
}

async function uploadNote(option: any) {
  const fd = new FormData()
  fd.append('file', option.file)
  const parentId = selected.value?.type === 'FOLDER' ? selected.value.id : selected.value?.parentId
  if (parentId) {
    fd.append('parentId', String(parentId))
  }
  const created = await noteItemApi.upload(fd) as unknown as NoteItem
  selected.value = clone(created)
  editing.value = false
  await load()
  ElMessage.success('笔记已上传')
}

async function chooseFolderPathForCreate() {
  const dir = await selectLocalDirectory()
  if (dir) createForm.filePath = dir
}

async function chooseFolderPathForInfo() {
  const dir = await selectLocalDirectory()
  if (dir) infoForm.filePath = dir
}

async function selectLocalDirectory() {
  if (!window.jobTrackerDesktop?.selectDirectory) {
    ElMessage.info('当前不是桌面客户端环境，请手动输入本地目录')
    return null
  }
  return await window.jobTrackerDesktop.selectDirectory()
}

async function saveInfo() {
  if (!infoForm.id) return
  if (!infoForm.title?.trim()) {
    ElMessage.warning('请先填写名称')
    return
  }
  await noteItemApi.update(infoForm.id, infoForm)
  if (selected.value?.id === infoForm.id) {
    selected.value = { ...selected.value, ...clone(infoForm) } as NoteItem
  }
  infoVisible.value = false
  editing.value = false
  await load()
  ElMessage.success('文件信息已保存')
}

async function saveSelected() {
  if (!selected.value?.id || saving.value) return
  saving.value = true
  try {
    await noteItemApi.update(selected.value.id, selected.value)
    ElMessage.success('内容已保存')
    editing.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function handleSaveShortcut(event: KeyboardEvent) {
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 's' || !editing.value) return
  event.preventDefault()
  saveSelected()
}

async function removeSelected() {
  if (!selected.value?.id) return
  try {
    await ElMessageBox.confirm('删除后会同时移除该文件夹下的子项目，确定继续吗？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await noteItemApi.remove(selected.value.id)
  selected.value = null
  editing.value = false
  await load()
}

async function moveSelectedToRoot() {
  if (!selected.value?.id || !selected.value.parentId) return
  await moveItemToRoot(selected.value)
}

async function moveItemToRoot(item: NoteItem) {
  if (!item.id || !item.parentId) return
  await noteItemApi.moveRoot(item.id)
  if (selected.value?.id === item.id) {
    selected.value.parentId = null
  }
  editing.value = false
  rootDragOver.value = false
  await load()
  ElMessage.success('已移到根目录')
}

function handleRootDragOver() {
  if (draggingCanMoveToRoot.value) {
    rootDragOver.value = true
  }
}

async function dropToRoot() {
  const item = dragging.value
  rootDragOver.value = false
  if (!item?.id || !item.parentId) return
  await moveItemToRoot(item)
}

function handleDragStart(node: any) {
  dragging.value = clone(node.data as NoteItem)
}

function handleDragEnd() {
  dragging.value = null
  rootDragOver.value = false
}

function allowDrop(draggingNode: any, dropNode: any, type: 'prev' | 'inner' | 'next') {
  if (type !== 'inner') return true
  const draggingItem = draggingNode.data as NoteItem
  const target = dropNode.data as NoteItem
  if (target.type !== 'FOLDER') return false
  if (draggingItem.id === target.id) return false
  return !isDescendantOf(target.id!, draggingItem.id!)
}

async function handleDrop(draggingNode: any, dropNode: any, type: 'prev' | 'inner' | 'next') {
  const draggingItem = draggingNode.data as NoteItem
  const target = dropNode.data as NoteItem
  const parentId = type === 'inner' ? target.id : target.parentId ?? null
  if (!draggingItem.id) return
  if (parentId == null) {
    await noteItemApi.moveRoot(draggingItem.id)
  } else {
    await noteItemApi.move(draggingItem.id, parentId)
  }
  if (selected.value?.id === draggingItem.id) selected.value.parentId = parentId
  dragging.value = null
  await load()
}

function isDescendantOf(candidateId: number, sourceId: number) {
  let cursor: number | null | undefined = candidateId
  while (cursor) {
    if (cursor === sourceId) return true
    cursor = rows.value.find((item) => item.id === cursor)?.parentId
  }
  return false
}

function extension(item?: NoteItem | null) {
  const name = item?.fileName || item?.title || ''
  const index = name.lastIndexOf('.')
  return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
}

function isEditableNote(item?: NoteItem | null) {
  if (!item || item.type !== 'NOTE') return false
  return ['md', 'markdown', 'txt'].includes(extension(item))
}

function isPdfNote(item?: NoteItem | null) {
  return item?.type === 'NOTE' && extension(item) === 'pdf'
}

function isWordNote(item?: NoteItem | null) {
  return item?.type === 'NOTE' && ['doc', 'docx'].includes(extension(item))
}

function fileTypeMeta(item?: NoteItem | null) {
  if (!item || item.type === 'FOLDER') {
    return { label: '文件夹', className: 'note-type-folder', icon: FolderOpened }
  }
  const ext = extension(item)
  if (ext === 'pdf') return { label: 'PDF 笔记', className: 'note-type-pdf', icon: Files }
  if (ext === 'doc' || ext === 'docx') return { label: 'Word 笔记', className: 'note-type-word', icon: Document }
  if (ext === 'txt') return { label: '文本笔记', className: 'note-type-text', icon: Memo }
  return { label: 'Markdown 笔记', className: 'note-type-markdown', icon: Tickets }
}

function downloadSelected() {
  if (selected.value?.id) window.open(noteItemApi.downloadUrl(selected.value.id), '_blank')
}

function locationName(parentId?: number | null) {
  if (!parentId) return '根目录'
  return folderOptions.value.find((item) => item.id === parentId)?.path || '未知位置'
}

function childCount(id?: number) {
  if (!id) return 0
  return rows.value.filter((item) => item.parentId === id).length
}

function formatDate(value?: string) {
  return formatDateTime(value)
}

function clone<T>(value: T): T {
  return value ? JSON.parse(JSON.stringify(value)) : value
}
</script>
