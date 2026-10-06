<template>
  <h1 class="page-title">投递管理</h1>
  <div class="panel">
    <div class="application-filters">
      <div class="filter-row">
        <el-input v-model="query.companyName" placeholder="搜索公司" clearable />
        <el-select v-model="query.currentStatus" placeholder="投递状态" clearable filterable allow-create default-first-option>
          <el-option v-for="item in dynamicStatusOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.positionType" placeholder="岗位类别" clearable filterable allow-create default-first-option>
          <el-option v-for="item in dynamicTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.recruitmentType" placeholder="投递批次" clearable filterable allow-create default-first-option>
          <el-option v-for="item in recruitmentTypeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.resumeCategory" placeholder="简历类别" clearable filterable allow-create default-first-option>
          <el-option v-for="item in dynamicResumeCategoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker v-model="dateRange" type="datetimerange" start-placeholder="投递开始" end-placeholder="投递结束" value-format="YYYY-MM-DDTHH:mm:ss" />
      </div>
      <div class="filter-actions">
        <el-segmented v-model="sortMode" :options="sortOptions" />
        <el-segmented v-model="groupMode" :options="groupOptions" :disabled="batchMode" />
        <div class="action-spacer"></div>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button @click="openCreate">新增岗位</el-button>
        <el-button :type="batchMode ? 'primary' : undefined" @click="batchMode ? exitBatchMode() : enterBatchMode()">
          {{ batchMode ? '退出批量管理' : '批量管理' }}
        </el-button>
      </div>
    </div>

    <div v-if="batchMode" class="batch-toolbar">
      <span class="batch-selection-count">已选 {{ selectedRows.length }} 条</span>
      <el-button :disabled="allCurrentPageSelected" @click="selectAllCurrentPage">全选当前页</el-button>
      <el-button :disabled="!selectedRows.length" @click="clearSelection">取消全选</el-button>
      <el-button type="danger" plain :disabled="!selectedRows.length" @click="batchRemove">批量删除</el-button>
      <div class="batch-toolbar-spacer"></div>
      <el-button @click="exitBatchMode">完成</el-button>
    </div>

    <el-table class="applications-table" ref="applicationTableRef" :data="displayRows" row-key="rowKey" :row-class-name="applicationRowClass" @selection-change="handleSelectionChange" @row-click="handleRowClick">
      <el-table-column v-if="batchMode" type="selection" width="48" :selectable="selectableRow" />
      <el-table-column prop="companyName" label="招聘组织" min-width="230">
        <template #default="{ row }">
          <div v-if="row.isGroup" class="company-group-title">
            <strong>{{ row.companyName }}</strong>
          </div>
          <div v-else class="organization-cell" :class="{ 'company-child-name': groupMode !== 'none' }">
            <strong>{{ employerDisplay(row) }}</strong>
            <small v-if="organizationPath(row) !== employerDisplay(row)">{{ organizationPath(row) }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="positionName" label="岗位" min-width="160">
        <template #default="{ row }">
          <div v-if="row.isGroup" class="company-group-summary">
            <el-tag size="small" class="company-count">{{ groupPositionCount(row.groupChildren) }} 个岗位</el-tag>
            <span>{{ groupMode === 'group' ? '同集团招聘合集' : '同企业投递合集' }}</span>
          </div>
          <button v-else-if="row.multiPreference" class="submission-summary-button" @click.stop="openPreferenceDialog(row)">
            <span>本次网申</span>
            <el-tag class="preference-tag" size="small">{{ row.preferenceCount || row.preferences?.length || 0 }} 个志愿</el-tag>
          </button>
          <div v-else class="position-cell">
            <el-tooltip :content="row.positionName" placement="top" :disabled="!row.positionName">
              <span class="table-ellipsis">{{ row.positionName }}</span>
            </el-tooltip>
            <el-tag v-if="row.preferenceOrder" class="preference-tag" size="small">第 {{ row.preferenceOrder }} 志愿</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="positionType" label="岗位类别" min-width="145">
        <template #default="{ row }">
          <el-tooltip v-if="!row.isGroup && !row.submissionParent" :content="row.positionType || '-'" placement="top" :disabled="!row.positionType">
            <div class="multi-tag-cell">
              <el-tag v-for="tag in visibleMultiTags(row.positionType)" :key="tag" class="category-tag" :style="tagStyle(tag)" size="small">{{ tag }}</el-tag>
              <el-tag v-if="hiddenMultiTagCount(row.positionType)" class="more-tag" size="small" type="info">+{{ hiddenMultiTagCount(row.positionType) }}</el-tag>
              <span v-if="!row.positionType" class="muted">-</span>
            </div>
          </el-tooltip>
          <span v-else-if="row.submissionParent" class="muted">各志愿独立</span>
        </template>
      </el-table-column>
      <el-table-column prop="recruitmentType" label="投递批次" width="84" show-overflow-tooltip />
      <el-table-column prop="resumeCategory" label="简历类别" min-width="145">
        <template #default="{ row }">
          <el-tooltip v-if="!row.isGroup && !row.submissionParent" :content="row.resumeCategory || '-'" placement="top" :disabled="!row.resumeCategory">
            <div class="multi-tag-cell">
              <el-tag v-for="tag in visibleMultiTags(row.resumeCategory)" :key="tag" class="category-tag" :style="tagStyle(tag)" size="small">{{ tag }}</el-tag>
              <el-tag v-if="hiddenMultiTagCount(row.resumeCategory)" class="more-tag" size="small" type="info">+{{ hiddenMultiTagCount(row.resumeCategory) }}</el-tag>
              <span v-if="!row.resumeCategory" class="muted">-</span>
            </div>
          </el-tooltip>
          <span v-else-if="row.submissionParent" class="muted">各志愿独立</span>
        </template>
      </el-table-column>
      <el-table-column prop="currentStatus" label="状态" width="104">
        <template #default="{ row }">
          <span v-if="!row.isGroup && !row.submissionParent" class="status-pill" :class="statusMeta(displayStatus(row)).className">
            <el-icon><component :is="statusMeta(displayStatus(row)).icon" /></el-icon>
            <span>{{ displayStatus(row) }}</span>
          </span>
          <span v-else-if="row.submissionParent" class="muted">见志愿</span>
        </template>
      </el-table-column>
      <el-table-column prop="appliedTime" label="投递时间" width="140">
        <template #default="{ row }">{{ row.isGroup ? '' : formatDate(row.appliedTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <div v-if="!row.isGroup" class="application-row-actions">
            <el-button size="small" @click.stop="row.multiPreference ? openPreferenceDialog(row) : $router.push(`/application/${row.id}`)">{{ row.multiPreference ? '志愿' : '详情' }}</el-button>
            <el-button size="small" @click.stop="row.multiPreference ? openPreferenceFromParent(row) : openEdit(row)">{{ row.multiPreference ? '加志愿' : '编辑' }}</el-button>
            <el-dropdown trigger="click" @command="handleRowCommand($event, row)">
              <el-button size="small" @click.stop>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="new-position">同公司新增岗位</el-dropdown-item>
                  <el-dropdown-item command="next-preference">添加下一志愿</el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="danger-dropdown-item">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="load"
      @size-change="handlePageSizeChange"
    />
  </div>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(820px, 92vw)" class="application-dialog">
    <el-form :model="form" label-width="110px">
      <el-form-item label="投递关系">
        <template v-if="!form.id">
          <el-radio-group v-model="submissionMode">
            <el-radio-button value="independent">独立岗位投递</el-radio-button>
            <el-radio-button value="preference">同次投递志愿</el-radio-button>
          </el-radio-group>
          <div class="form-item-hint submission-mode-hint">
            {{ submissionMode === 'preference' ? '与原记录属于同一次网申，系统会自动生成下一志愿序号。' : '作为一次新的岗位投递单独统计。' }}
          </div>
        </template>
        <el-tag v-else size="small">{{ form.preferenceOrder ? `第 ${form.preferenceOrder} 志愿` : '独立岗位投递' }}</el-tag>
      </el-form-item>
      <el-alert v-if="parentSubmissionId" class="quick-add-alert" type="info" :closable="false" show-icon>
        已从本次网申父记录复用公司、组织、批次、地点、来源和投递时间；简历、JD、链接、状态等按志愿单独填写。
      </el-alert>
      <el-alert v-else-if="quickAddSourceId" class="quick-add-alert" type="info" :closable="false" show-icon>
        已复用公司、招聘批次、地点、来源和简历信息；岗位名称、JD、链接、简历及其他字段都可独立修改。
      </el-alert>
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="公司名称"><el-input v-model="form.companyName" :disabled="Boolean(form.organizationUnitId)" placeholder="简单公司可直接填写" /><div v-if="form.organizationUnitId" class="form-item-hint">已绑定组织时由招聘企业节点决定，可通过“编辑当前组织”修改。</div></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="岗位名称"><el-input v-model="form.positionName" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="组织归属">
        <div class="organization-picker-row">
          <el-cascader
            v-model="form.organizationUnitId"
            :options="organizationTree"
            :props="organizationCascaderProps"
            clearable
            filterable
            placeholder="复杂企业可选择集团 / 子公司 / 部门；简单公司可不选"
            @change="handleOrganizationChange"
          />
          <el-button @click="openOrganizationDialog">新建组织节点</el-button>
          <el-button :disabled="!form.organizationUnitId" @click="openEditOrganizationDialog">编辑当前组织</el-button>
          <el-button :disabled="!form.organizationUnitId" @click="openOrganizationManager(form.organizationUnitId)">管理组织节点</el-button>
        </div>
        <div v-if="form.organizationPathSnapshot" class="organization-form-summary">
          <span>完整路径：{{ form.organizationPathSnapshot }}</span>
          <span>招聘企业：{{ form.employerNameSnapshot || form.companyName }}</span>
          <span>所属集团：{{ form.groupNameSnapshot || form.companyName }}</span>
        </div>
        <div v-else class="form-item-hint">不选择时会把公司名称自动保存为一级公司节点。</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="岗位类别">
            <el-select v-model="formPositionTypes" multiple filterable allow-create default-first-option placeholder="可多选或自定义">
              <el-option v-for="item in dynamicTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="投递批次">
            <el-select v-model="form.recruitmentType" filterable allow-create default-first-option clearable>
              <el-option v-for="item in recruitmentTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="绑定简历">
            <div class="resume-bind-row">
              <el-select v-model="form.resumeId" clearable filterable placeholder="选择已上传简历" @change="handleResumeChange">
                <el-option v-for="resume in resumes" :key="resume.id" :label="resume.fileName" :value="resume.id" />
              </el-select>
              <el-upload
                :http-request="uploadAndBindResume"
                :show-file-list="false"
                accept=".pdf,.doc,.docx"
              >
                <el-button :loading="resumeUploading">上传新简历</el-button>
              </el-upload>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="简历类别">
            <el-select v-model="formResumeCategories" multiple filterable allow-create default-first-option clearable placeholder="可多选或自定义">
              <el-option v-for="item in dynamicResumeCategoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="个人档案版本">
        <el-select v-model="form.profileId" clearable filterable placeholder="选择本次投递使用的档案版本">
          <el-option v-for="item in profileVersions" :key="item.profileId" :label="item.defaultProfile ? `${item.name}（默认）` : item.name" :value="item.profileId" />
        </el-select>
        <div class="form-item-hint">与简历文件独立选择；浏览器扩展可优先使用此版本填写。</div>
      </el-form-item>
      <el-form-item label="投递简历名称">
        <div class="resume-alias-editor">
          <el-select v-model="namingTemplate" placeholder="选择命名模板" @change="regenerateResumeAlias">
            <el-option v-for="item in namingTemplates" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
<!--          <el-button @click="regenerateResumeAlias">按模板生成</el-button>-->
        </div>
        <el-input v-model="form.resumeAlias" clearable placeholder="选择简历并填写公司、岗位后自动生成，也可手动修改" @input="resumeAliasAuto = false" />
        <div class="form-item-hint">根据系统设置中的姓名、学校和毕业年份生成；仅影响本次投递的展示与下载文件名，不会复制或改动原简历。</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="工作地点"><el-input v-model="form.workLocation" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="薪资"><el-input v-model="form.salary" /></el-form-item></el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="投递来源"><el-input v-model="form.source" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="当前状态">
            <el-select v-model="form.currentStatus" filterable default-first-option>
              <el-option v-for="item in formStatusOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="岗位链接"><el-input v-model="form.jobLink" /></el-form-item>
      <el-form-item label="投递时间"><el-date-picker v-model="form.appliedTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      <el-form-item label="岗位 JD"><el-input v-model="form.jobDescription" type="textarea" :rows="5" placeholder="粘贴岗位职责、要求、技术栈等信息" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible=false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="preferenceDialogVisible" title="同次网申志愿" width="min(1240px, 96vw)" class="preference-dialog">
    <div class="preference-dialog-summary">
      <div><span>公司</span><strong>{{ selectedSubmission?.companyName }}</strong></div>
      <div><span>投递批次</span><strong>{{ selectedSubmission?.recruitmentType || '-' }}</strong></div>
      <div><span>志愿数量</span><strong>{{ preferenceRows.length }}</strong></div>
    </div>
    <el-table :data="preferenceRows" class="preference-table">
      <el-table-column label="志愿" width="76">
        <template #default="{ row }"><el-tag size="small">第 {{ row.preferenceOrder }} 志愿</el-tag></template>
      </el-table-column>
      <el-table-column prop="positionName" label="岗位" min-width="190" show-overflow-tooltip />
      <el-table-column label="所属组织" min-width="220">
        <template #default="{ row }"><span class="organization-path-full">{{ organizationPath(row) }}</span></template>
      </el-table-column>
      <el-table-column prop="positionType" label="岗位类别" min-width="130" show-overflow-tooltip />
      <el-table-column prop="resumeCategory" label="简历类别" min-width="120" show-overflow-tooltip />
      <el-table-column prop="currentStatus" label="状态" width="100" />
      <el-table-column prop="appliedTime" label="投递时间" width="150">
        <template #default="{ row }">{{ formatDate(row.appliedTime) || '未填写' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <div class="preference-row-actions">
            <el-button size="small" @click="$router.push(`/application/${row.id}`)">详情</el-button>
            <el-button size="small" @click="editPreference(row)">编辑</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="preferenceDialogVisible = false">关闭</el-button>
      <el-button @click="openSubmissionEditor">编辑本次网申</el-button>
      <el-button type="primary" @click="addPreferenceFromDialog">添加下一志愿</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="submissionDialogVisible" title="编辑本次网申" width="min(820px, 92vw)" class="application-dialog">
    <el-alert class="quick-add-alert" type="info" :closable="false" show-icon title="父记录归属由你手动指定为组织树中的任一节点，不会按企业主体标记自动推导。工作地点、来源、投递时间及简历、JD、状态均属于具体志愿，不会保存或复用。" />
    <el-form :model="submissionForm" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="父记录名称"><el-input :model-value="submissionForm.companyName || '请先选择父记录归属'" readonly /><div class="form-item-hint">使用你在下方选定组织节点的名称；要修改名称，请编辑该组织节点或重新选择。</div></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="投递批次"><el-select v-model="submissionForm.recruitmentType" filterable allow-create default-first-option clearable><el-option v-for="item in recruitmentTypeOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
      </el-row>
      <el-form-item label="父记录归属" required>
        <div class="organization-picker-row">
          <el-cascader v-model="submissionForm.organizationUnitId" :options="organizationTree" :props="organizationCascaderProps" clearable filterable placeholder="搜索并选择集团 / 子公司 / 分公司 / 部门" @change="handleSubmissionOrganizationChange" />
          <el-button @click="openOrganizationDialog('submission')">新建组织节点</el-button>
          <el-button :disabled="!submissionForm.organizationUnitId" @click="openEditSubmissionOrganizationDialog">编辑当前组织</el-button>
          <el-button :disabled="!submissionForm.organizationUnitId" @click="openOrganizationManager(submissionForm.organizationUnitId)">管理组织节点</el-button>
        </div>
        <div v-if="submissionForm.organizationPathSnapshot" class="organization-form-summary"><span>完整路径：{{ submissionForm.organizationPathSnapshot }}</span><span>招聘企业：{{ submissionForm.employerNameSnapshot || submissionForm.companyName }}</span><span>所属集团：{{ submissionForm.groupNameSnapshot || submissionForm.companyName }}</span></div>
        <div v-else class="form-item-hint">不选择时会把公司名称自动保存为一级公司节点。</div>
      </el-form-item>
      <el-form-item label="父记录备注"><el-input v-model="submissionForm.remark" type="textarea" :rows="3" placeholder="仅记录本次网申的公共说明" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="submissionDialogVisible = false">取消</el-button><el-button type="primary" @click="saveSubmission">保存父记录</el-button></template>
  </el-dialog>

  <el-dialog v-model="organizationDialogVisible" :title="organizationDialogTitle" width="min(620px, 92vw)" class="organization-dialog">
    <el-alert :title="organizationEditingId ? '可修改名称、类型、上级和企业主体；系统会阻止循环层级。' : '选择上级后创建一个下级节点；可反复创建，从集团一直建立到部门或团队。'" type="info" :closable="false" show-icon />
    <el-form :model="organizationDraft" label-width="96px" class="organization-create-form">
      <el-form-item label="上级组织">
        <el-cascader v-model="organizationDraft.parentId" :options="organizationTree" :props="organizationCascaderProps" clearable filterable placeholder="不选表示一级组织" />
      </el-form-item>
      <el-form-item label="组织名称" required><el-input v-model="organizationDraft.name" maxlength="200" /></el-form-item>
      <el-form-item label="组织类型">
        <el-select v-model="organizationDraft.unitType">
          <el-option v-for="item in organizationTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="企业主体">
        <el-switch v-model="organizationDraft.companyEntity" active-text="计入投递企业统计" />
        <div class="form-item-hint">集团通常不勾选；公司、子公司或分公司可按实际招聘主体勾选。</div>
      </el-form-item>
      <el-form-item label="别名"><el-input v-model="organizationDraft.aliases" placeholder="多个别名可用顿号分隔，便于搜索" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="organizationDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveOrganization">{{ organizationEditingId ? '保存修改' : '创建并选中' }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="organizationManagerVisible" :title="organizationManagerTitle" width="min(980px, 96vw)" class="organization-manager-dialog">
    <el-alert title="节点只能在没有下级组织、且没有投递岗位或网申父记录引用时删除。请先删除或改绑关联记录，并从最末级节点开始清理。" type="warning" :closable="false" show-icon />
    <el-input v-model="organizationSearchKeyword" clearable placeholder="在当前组织树内搜索名称、别名或完整路径" class="organization-manager-search" />
    <el-table :data="managedOrganizationNodes" class="organization-manager-table" max-height="520">
      <el-table-column prop="fullPath" label="完整路径" min-width="340" show-overflow-tooltip />
      <el-table-column prop="unitType" label="类型" width="120" />
      <el-table-column label="招聘企业" width="120"><template #default="{ row }">{{ row.companyEntity ? '是' : '否' }}</template></el-table-column>
      <el-table-column label="操作" width="180"><template #default="{ row }"><div class="organization-manager-actions"><el-button size="small" @click="openEditOrganizationNode(row)">编辑</el-button><el-button size="small" type="danger" plain @click="removeOrganizationNode(row)">删除</el-button></div></template></el-table-column>
    </el-table>
    <template #footer><el-button @click="organizationManagerVisible = false">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ChatDotRound, CircleCheck, Collection, EditPen, Medal, Promotion, Star, User, Warning } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi, candidateProfileApi, organizationApi, recruitmentTypeOptions, resumeApi, resumeCategoryOptions, statusOptions, storageApi, typeOptions } from '../api'
import type { CandidateProfileSummary, JobApplication, OrganizationUnit, Resume } from '../types'
import { formatDateTime } from '../utils/time'
import { parseSavedResumeNamingTemplates, renderResumeName, resumeNamingPresets, templateForSelection } from '../utils/resumeNaming'

type ApplicationRow = JobApplication & { rowKey?: string; isGroup?: boolean; children?: ApplicationRow[]; groupChildren?: ApplicationRow[] }

const rows = ref<ApplicationRow[]>([])
const resumes = ref<Resume[]>([])
const profileVersions = ref<CandidateProfileSummary[]>([])
const dynamicStatusOptions = ref<string[]>([...statusOptions])
const dynamicTypeOptions = ref<string[]>([...typeOptions])
const dynamicResumeCategoryOptions = ref<string[]>([...resumeCategoryOptions])
const total = ref(0)
const dialogVisible = ref(false)
const resumeUploading = ref(false)
const groupMode = ref<'none' | 'company' | 'group'>('none')
const groupOptions = [
  { label: '不合并', value: 'none' },
  { label: '按招聘企业', value: 'company' },
  { label: '按集团', value: 'group' }
]
const organizationTree = ref<OrganizationUnit[]>([])
const organizationDialogVisible = ref(false)
const organizationManagerVisible = ref(false)
const organizationManagerRootId = ref<number>()
const organizationSearchKeyword = ref('')
const organizationEditingId = ref<number>()
const organizationDialogTarget = ref<'application' | 'submission'>('application')
const organizationDraft = reactive<Partial<OrganizationUnit>>({ name: '', unitType: 'COMPANY', companyEntity: true, active: true })
const organizationCascaderProps = { value: 'id', label: 'name', children: 'children', emitPath: false, checkStrictly: true }
const organizationTypeOptions = [
  { label: '集团', value: 'GROUP' },
  { label: '公司', value: 'COMPANY' },
  { label: '子公司', value: 'SUBSIDIARY' },
  { label: '分公司', value: 'BRANCH' },
  { label: '事业部', value: 'BUSINESS_UNIT' },
  { label: '部门', value: 'DEPARTMENT' },
  { label: '团队', value: 'TEAM' },
  { label: '其他', value: 'OTHER' }
]
const dateRange = ref<string[]>([])
const sortMode = ref('appliedTime-desc')
const batchMode = ref(false)
const selectedRows = ref<ApplicationRow[]>([])
const applicationTableRef = ref<any>()
const formPositionTypes = ref<string[]>([])
const formResumeCategories = ref<string[]>([])
const namingTemplate = ref('company')
const resumeAliasAuto = ref(true)
const quickAddSourceId = ref<number>()
const parentSubmissionId = ref<string>()
const submissionMode = ref<'independent' | 'preference'>('independent')
const preferenceDialogVisible = ref(false)
const selectedSubmission = ref<ApplicationRow>()
const submissionDialogVisible = ref(false)
const editingSubmissionId = ref<string>()
const submissionForm = reactive<JobApplication>(emptyForm())
const namingSettings = reactive({ resumeOwnerName: '', resumeOwnerSchool: '', resumeGraduationYear: '', resumeCustomNamingTemplate: '', resumeCustomNamingTemplates: '' })
const namingTemplates = computed(() => [
  ...resumeNamingPresets,
  ...parseSavedResumeNamingTemplates(namingSettings.resumeCustomNamingTemplates, namingSettings.resumeCustomNamingTemplate)
    .map((item) => ({ value: `custom:${item.id}`, label: `自定义 · ${item.name}` }))
])
const sortOptions = [
  { label: '投递时间↓', value: 'appliedTime-desc' },
  { label: '投递时间↑', value: 'appliedTime-asc' },
  { label: '公司名 A-Z', value: 'companyName-asc' },
  { label: '公司名 Z-A', value: 'companyName-desc' }
]
const query = reactive({
  companyName: '',
  currentStatus: '',
  positionType: '',
  recruitmentType: '',
  resumeCategory: '',
  sortField: 'appliedTime',
  sortOrder: 'desc',
  pageNo: 1,
  pageSize: 10
})
const form = reactive<JobApplication>(emptyForm())
const formStatusOptions = computed(() => {
  const flowOptions = parseProgressFlow(form.progressFlow)
  return flowOptions.length ? flowOptions : statusOptions
})
const dialogTitle = computed(() => {
  if (form.id) return '编辑岗位'
  if (parentSubmissionId.value) return '添加下一志愿'
  if (quickAddSourceId.value && submissionMode.value === 'preference') return '添加下一志愿'
  if (quickAddSourceId.value) return '同公司新增岗位'
  return '新增岗位'
})
const organizationDialogTitle = computed(() => organizationEditingId.value ? '编辑组织节点' : '新建组织节点')
const preferenceRows = computed(() => [...(selectedSubmission.value?.preferences || [])]
  .sort((left, right) => (left.preferenceOrder || 0) - (right.preferenceOrder || 0)))
const managedOrganizationRoot = computed(() => organizationTree.value.find(item => item.id === organizationManagerRootId.value))
const organizationManagerTitle = computed(() => managedOrganizationRoot.value
  ? `组织节点管理 · ${managedOrganizationRoot.value.name}`
  : '组织节点管理')
const managedOrganizationNodes = computed(() => {
  const nodes: OrganizationUnit[] = []
  const visit = (items: OrganizationUnit[]) => items.forEach(item => {
    nodes.push(item)
    visit(item.children || [])
  })
  if (managedOrganizationRoot.value) visit([managedOrganizationRoot.value])
  const keyword = organizationSearchKeyword.value.trim().toLowerCase()
  if (!keyword) return nodes
  return nodes.filter(item => [item.name, item.aliases, item.fullPath].some(value => value?.toLowerCase().includes(keyword)))
})

const displayRows = computed<ApplicationRow[]>(() => {
  if (groupMode.value === 'none') {
    return rows.value.map(row => ({ ...row, rowKey: submissionRowKey(row) }))
  }
  return rows.value.flatMap((group, index) => {
    const rowKey = group.rowKey || `company-${query.pageNo}-${index}`
    const groupChildren = group.children || []
    const { children: _children, ...groupWithoutChildren } = group
    const groupRow = {
      ...groupWithoutChildren,
      rowKey,
      isGroup: true,
      companyName: group.companyName || '未填写公司',
      positionName: '',
      currentStatus: '',
      groupChildren
    } as ApplicationRow
    return [
      groupRow,
      ...groupChildren.map(row => ({ ...row, rowKey: submissionRowKey(row) }))
    ]
  })
})

let loadRequestId = 0

watch(sortMode, value => {
  const [field, order] = value.split('-')
  query.sortField = field
  query.sortOrder = order
  query.pageNo = 1
  load()
})

watch(groupMode, () => {
  query.pageNo = 1
  load()
})

onMounted(async () => {
  await Promise.all([load(), loadResumes(), loadProfileVersions(), loadFilterOptions(), loadNamingSettings(), loadOrganizationTree()])
})

watch(() => [
  form.companyName,
  form.positionName,
  form.recruitmentType,
  form.workLocation,
  formPositionTypes.value.join('、'),
  formResumeCategories.value.join('、'),
  namingTemplate.value
], () => {
  if (dialogVisible.value && resumeAliasAuto.value) regenerateResumeAlias()
})

function emptyForm(): JobApplication {
  return {
    companyName: '',
    positionName: '',
    currentStatus: '待投递',
    recruitmentType: '秋招'
  }
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

function formatDate(value?: string) {
  return formatDateTime(value)
}

function queryParams() {
  return {
    ...query,
    appliedStartTime: dateRange.value?.[0] || '',
    appliedEndTime: dateRange.value?.[1] || ''
  }
}

async function load() {
  const requestId = ++loadRequestId
  const grouped = groupMode.value !== 'none'
  const page: any = grouped
    ? await applicationApi.pageGroupedByCompany({ ...queryParams(), groupLevel: groupMode.value })
    : await loadSubmissionPage()
  if (requestId !== loadRequestId || grouped !== (groupMode.value !== 'none')) return
  rows.value = grouped
    ? page.records.map((group: ApplicationRow, index: number) => ({
        ...group,
        children: collapseSubmissions(group.children || []),
        rowKey: `company-${query.pageNo}-${index}-${group.companyName || 'empty'}`
      }))
    : page.records
  total.value = page.total
  clearSelection()
}

async function loadSubmissionPage() {
  try {
    return await applicationApi.pageGroupedBySubmission(queryParams()) as any
  } catch {
    const rawPage: any = await applicationApi.page({ ...queryParams(), pageNo: 1, pageSize: 10000 })
    const submissions = collapseSubmissions(rawPage.records || [])
    const start = (query.pageNo - 1) * query.pageSize
    return {
      records: submissions.slice(start, start + query.pageSize),
      total: submissions.length
    }
  }
}

function collapseSubmissions(applications: ApplicationRow[]) {
  const grouped = new Map<string, ApplicationRow[]>()
  applications.forEach(application => {
    const sourceItems = application.multiPreference && application.preferences?.length
      ? application.preferences
      : [application]
    sourceItems.forEach(item => {
      const isPreference = Boolean(item.submissionGroupId && item.preferenceOrder)
      const key = isPreference ? `preference:${item.submissionGroupId}` : `application:${item.id}`
      const items = grouped.get(key) || []
      items.push(item)
      grouped.set(key, items)
    })
  })
  return Array.from(grouped.values()).map(items => {
    const preferences = [...items].sort((left, right) => (left.preferenceOrder || 0) - (right.preferenceOrder || 0))
    return {
      ...items[0],
      multiPreference: items.length > 1,
      submissionParent: items.length > 1,
      preferenceCount: items.length,
      positionName: items.length > 1 ? '本次网申' : items[0].positionName,
      preferences
    }
  })
}

function submissionRowKey(row: ApplicationRow) {
  return row.multiPreference && row.submissionGroupId
    ? `submission-${row.submissionGroupId}`
    : `job-${row.id}`
}

function groupPositionCount(groupChildren?: ApplicationRow[]) {
  return (groupChildren || []).reduce((count, row) => count + (row.preferences?.length || 1), 0)
}

function applicationRowClass({ row }: { row: ApplicationRow }) {
  if (row.isGroup) return 'company-group-row'
  return [groupMode.value !== 'none' ? 'company-child-row' : '', row.multiPreference ? 'submission-group-row' : '']
    .filter(Boolean)
    .join(' ')
}

async function loadResumes() {
  resumes.value = await resumeApi.list() as unknown as Resume[]
}

async function loadOrganizationTree() {
  organizationTree.value = await organizationApi.tree()
}

function findOrganizationContext(id?: number) {
  if (!id) return undefined
  const visit = (nodes: OrganizationUnit[], parents: OrganizationUnit[]): OrganizationUnit[] | undefined => {
    for (const node of nodes) {
      const path = [...parents, node]
      if (node.id === id) return path
      const found = visit(node.children || [], path)
      if (found) return found
    }
    return undefined
  }
  return visit(organizationTree.value, [])
}

function applyOrganizationContext(target: JobApplication, value?: number | null) {
  if (!value) {
    target.submissionOrganizationId = undefined
    target.employerOrganizationId = undefined
    target.organizationPathSnapshot = undefined
    target.employerNameSnapshot = undefined
    target.groupNameSnapshot = undefined
    return
  }
  const path = findOrganizationContext(value)
  if (!path?.length) return
  const root = path[0]
  const employer = [...path].reverse().find(item => item.companyEntity) || root
  target.submissionOrganizationId = root.id
  target.employerOrganizationId = employer.id
  target.organizationPathSnapshot = path.map(item => item.name).join(' / ')
  target.employerNameSnapshot = employer.name
  target.groupNameSnapshot = root.name
  target.companyName = employer.name
}

function handleOrganizationChange(value?: number | null) {
  applyOrganizationContext(form, value)
}

function handleSubmissionOrganizationChange(value?: number | null) {
  applyOrganizationContext(submissionForm, value)
}

function openOrganizationDialog(target: 'application' | 'submission' = 'application') {
  organizationDialogTarget.value = target
  const targetForm = target === 'submission' ? submissionForm : form
  organizationEditingId.value = undefined
  Object.assign(organizationDraft, {
    parentId: targetForm.organizationUnitId,
    name: '',
    unitType: targetForm.organizationUnitId ? 'DEPARTMENT' : 'COMPANY',
    companyEntity: !targetForm.organizationUnitId,
    aliases: '',
    active: true
  })
  organizationDialogVisible.value = true
}

function openOrganizationManager(nodeId?: number) {
  const path = findOrganizationContext(nodeId)
  if (!path?.length) {
    ElMessage.warning('请先选择一个组织节点，再管理其所在组织树')
    return
  }
  organizationManagerRootId.value = path[0].id
  organizationSearchKeyword.value = ''
  organizationManagerVisible.value = true
}

function openEditOrganizationDialog() {
  const path = findOrganizationContext(form.organizationUnitId)
  openEditOrganizationNode(path?.[path.length - 1])
}

function openEditSubmissionOrganizationDialog() {
  const path = findOrganizationContext(submissionForm.organizationUnitId)
  openEditOrganizationNode(path?.[path.length - 1])
}

function openEditOrganizationNode(current?: OrganizationUnit) {
  if (!current?.id) return
  organizationEditingId.value = current.id
  Object.assign(organizationDraft, {
    parentId: current.parentId,
    name: current.name,
    unitType: current.unitType,
    companyEntity: current.companyEntity,
    aliases: current.aliases || '',
    sortOrder: current.sortOrder || 0,
    active: current.active !== false
  })
  organizationDialogVisible.value = true
}

async function saveOrganization() {
  if (!organizationDraft.name?.trim()) {
    ElMessage.warning('请填写组织名称')
    return
  }
  const saved = organizationEditingId.value
    ? await organizationApi.update(organizationEditingId.value, organizationDraft)
    : await organizationApi.create(organizationDraft)
  await loadOrganizationTree()
  if (organizationEditingId.value) {
    if (form.organizationUnitId === saved.id) handleOrganizationChange(saved.id)
    if (submissionForm.organizationUnitId === saved.id) handleSubmissionOrganizationChange(saved.id)
  } else if (organizationDialogTarget.value === 'submission') {
    submissionForm.organizationUnitId = saved.id
    handleSubmissionOrganizationChange(saved.id)
  } else {
    form.organizationUnitId = saved.id
    handleOrganizationChange(saved.id)
  }
  organizationDialogVisible.value = false
  ElMessage.success(organizationEditingId.value ? '组织节点已更新' : '组织节点已创建并选中')
  organizationEditingId.value = undefined
}

async function removeOrganizationNode(node: OrganizationUnit) {
  if (!node.id) return
  try {
    await ElMessageBox.confirm(`确认删除组织节点“${node.fullPath || node.name}”吗？只有无下级节点、且没有关联投递记录时才能删除。`, '删除组织节点', { type: 'warning' })
  } catch {
    return
  }
  await organizationApi.remove(node.id)
  if (form.organizationUnitId === node.id) {
    form.organizationUnitId = undefined
    handleOrganizationChange(undefined)
  }
  if (submissionForm.organizationUnitId === node.id) {
    submissionForm.organizationUnitId = undefined
    handleSubmissionOrganizationChange(undefined)
  }
  await loadOrganizationTree()
  ElMessage.success('组织节点已删除')
}

function employerDisplay(row: JobApplication) {
  return row.employerNameSnapshot || row.companyName || '未填写公司'
}

function organizationPath(row: JobApplication) {
  return row.organizationPathSnapshot || employerDisplay(row)
}

async function loadProfileVersions() {
  profileVersions.value = await candidateProfileApi.versions()
}

async function loadNamingSettings() {
  const settings = await storageApi.get() as any
  namingSettings.resumeOwnerName = settings.resumeOwnerName || ''
  namingSettings.resumeOwnerSchool = settings.resumeOwnerSchool || ''
  namingSettings.resumeGraduationYear = settings.resumeGraduationYear || ''
  namingSettings.resumeCustomNamingTemplate = settings.resumeCustomNamingTemplate || ''
  namingSettings.resumeCustomNamingTemplates = settings.resumeCustomNamingTemplates || ''
  const requestedTemplate = settings.resumeNamingTemplate || 'general'
  namingTemplate.value = namingTemplates.value.some((item) => item.value === requestedTemplate) ? requestedTemplate : 'general'
}

async function uploadAndBindResume(option: any) {
  resumeUploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', option.file)
    const resume = await resumeApi.upload(fd) as unknown as Resume
    await loadResumes()
    form.resumeId = resume.id
    resumeAliasAuto.value = true
    regenerateResumeAlias()
    ElMessage.success('简历已上传并自动绑定')
  } finally {
    resumeUploading.value = false
  }
}

async function loadStatusOptions() {
  const options = await applicationApi.statusOptions() as unknown as string[]
  dynamicStatusOptions.value = mergeOptions(statusOptions, options)
}

async function loadFilterOptions() {
  const [statuses, positionTypes, resumeCategories] = await Promise.all([
    applicationApi.statusOptions(),
    applicationApi.positionTypeOptions(),
    applicationApi.resumeCategoryOptions()
  ])
  dynamicStatusOptions.value = mergeOptions(statusOptions, statuses as unknown as string[])
  dynamicTypeOptions.value = mergeOptions(typeOptions, positionTypes as unknown as string[])
  dynamicResumeCategoryOptions.value = mergeOptions(resumeCategoryOptions, resumeCategories as unknown as string[])
}

function mergeOptions(...groups: string[][]) {
  return Array.from(new Set(groups.flat().map((item) => item?.trim()).filter(Boolean)))
}

function resetQuery() {
  Object.assign(query, {
    companyName: '',
    currentStatus: '',
    positionType: '',
    recruitmentType: '',
    resumeCategory: '',
    sortField: 'appliedTime',
    sortOrder: 'desc',
    pageNo: 1
  })
  sortMode.value = 'appliedTime-desc'
  dateRange.value = []
  load()
}

function selectableRow(row: ApplicationRow) {
  return !row.isGroup
}

function handleSelectionChange(selection: ApplicationRow[]) {
  selectedRows.value = selection.filter((row) => !row.isGroup)
}

function clearSelection() {
  selectedRows.value = []
  applicationTableRef.value?.clearSelection()
}

const allCurrentPageSelected = computed(() => {
  const currentRows = displayRows.value.filter((row) => !row.isGroup)
  return currentRows.length > 0 && selectedRows.value.length === currentRows.length
})

function enterBatchMode() {
  groupMode.value = 'none'
  query.pageNo = 1
  batchMode.value = true
  clearSelection()
  void load()
}

function exitBatchMode() {
  clearSelection()
  batchMode.value = false
}

function selectAllCurrentPage() {
  const table = applicationTableRef.value
  if (!table) return
  table.clearSelection()
  displayRows.value
    .filter((row) => !row.isGroup)
    .forEach((row) => table.toggleRowSelection(row, true))
}

function handlePageSizeChange() {
  query.pageNo = 1
  load()
}

function selectedIds() {
  return Array.from(new Set(selectedRows.value.flatMap((row) => row.multiPreference
    ? (row.preferences || []).map(item => item.id)
    : [row.id]
  ).filter((id): id is number => typeof id === 'number')))
}

async function batchRemove() {
  const ids = selectedIds()
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条投递记录吗？此操作无法恢复。`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  await applicationApi.batchRemove(ids)
  ElMessage.success('投递记录已批量删除')
  if (rows.value.length === ids.length && query.pageNo > 1) query.pageNo -= 1
  await Promise.all([load(), loadFilterOptions()])
}

function splitMultiValue(value?: string) {
  return value ? value.split(/[、,，;；]/).map(item => item.trim()).filter(Boolean) : []
}

function visibleMultiTags(value?: string) {
  return splitMultiValue(value)
}

function hiddenMultiTagCount(_value?: string) {
  return 0
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

type ProgressStep = { name: string; result?: string; operatedTime?: string }

function parseProgressSteps(value?: string): ProgressStep[] {
  if (!value?.trim()) return []
  const raw = value.trim()
  if (raw.startsWith('[')) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => ({
            name: String(item?.name || '').trim(),
            result: String(item?.result || '').trim() || undefined,
            operatedTime: String(item?.operatedTime || '').trim() || undefined
          }))
          .filter((item) => item.name)
      }
    } catch {
      // 兼容旧文本格式，失败后继续按行解析。
    }
  }
  return raw.split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => ({ name: line.split('|')[0].trim() }))
    .filter((item) => item.name)
}

function parseProgressFlow(value?: string) {
  return parseProgressSteps(value).map((item) => item.name)
}

function statusFromProgressJson(value?: string) {
  const steps = parseProgressSteps(value)
  if (!steps.length) return ''
  return [...steps].reverse().find((item) => item.operatedTime || item.result)?.name || steps[0].name
}

function normalizedCurrentStatus(row: JobApplication) {
  const value = row.currentStatus || ''
  if (!value.trim().startsWith('[')) return value
  return statusFromProgressJson(value) || statusFromProgressJson(row.progressFlow) || '待投递'
}

function displayStatus(row: JobApplication) {
  return normalizedCurrentStatus(row) || '待投递'
}

function openCreate() {
  resetForm()
  quickAddSourceId.value = undefined
  parentSubmissionId.value = undefined
  submissionMode.value = 'independent'
  formPositionTypes.value = []
  formResumeCategories.value = []
  resumeAliasAuto.value = true
  dialogVisible.value = true
}

function openEdit(row: JobApplication) {
  resetForm()
  Object.assign(form, row)
  quickAddSourceId.value = undefined
  parentSubmissionId.value = undefined
  submissionMode.value = row.preferenceOrder ? 'preference' : 'independent'
  form.currentStatus = normalizedCurrentStatus(row)
  const options = parseProgressFlow(row.progressFlow)
  if (options.length && !options.includes(form.currentStatus)) {
    form.currentStatus = statusFromProgressJson(row.progressFlow) || options[0]
  }
  formPositionTypes.value = splitMultiValue(row.positionType)
  formResumeCategories.value = splitMultiValue(row.resumeCategory)
  resumeAliasAuto.value = false
  dialogVisible.value = true
}

function openPreferenceDialog(row: ApplicationRow) {
  if (!row.multiPreference) return
  selectedSubmission.value = row
  preferenceDialogVisible.value = true
}

function openSubmissionEditor() {
  const row = selectedSubmission.value
  if (!row?.submissionGroupId) return
  Object.keys(submissionForm).forEach(key => delete (submissionForm as any)[key])
  Object.assign(submissionForm, {
    companyName: row.companyName || '',
    recruitmentType: row.recruitmentType,
    remark: row.remark,
    submissionOrganizationId: row.submissionOrganizationId,
    employerOrganizationId: row.employerOrganizationId,
    organizationUnitId: row.organizationUnitId,
    organizationPathSnapshot: row.organizationPathSnapshot,
    employerNameSnapshot: row.employerNameSnapshot,
    groupNameSnapshot: row.groupNameSnapshot
  })
  editingSubmissionId.value = row.submissionGroupId
  submissionDialogVisible.value = true
}

async function saveSubmission() {
  if (!editingSubmissionId.value) return
  if (!submissionForm.organizationUnitId) {
    ElMessage.warning('请指定父记录归属')
    return
  }
  const saved = await applicationApi.updateSubmission(editingSubmissionId.value, submissionForm)
  Object.assign(submissionForm, saved)
  submissionDialogVisible.value = false
  await load()
  const refreshed = rows.value.find(item => item.submissionGroupId === editingSubmissionId.value)
  if (refreshed) selectedSubmission.value = refreshed
  ElMessage.success('本次网申公共信息已保存')
}

function handleRowClick(row: ApplicationRow) {
  if (row.multiPreference) openPreferenceDialog(row)
}

function preferenceSource(row: ApplicationRow) {
  const preferences = [...(row.preferences || [])]
    .sort((left, right) => (left.preferenceOrder || 0) - (right.preferenceOrder || 0))
  return preferences[preferences.length - 1] || row
}

function editPreference(row: JobApplication) {
  preferenceDialogVisible.value = false
  openEdit(row)
}

function addPreferenceFromDialog() {
  if (!selectedSubmission.value) return
  preferenceDialogVisible.value = false
  openPreferenceFromParent(selectedSubmission.value)
}

function resetForm() {
  Object.keys(form).forEach(key => delete (form as any)[key])
  Object.assign(form, emptyForm())
}

function openQuickAdd(row: JobApplication, sameSubmission: boolean) {
  if (!row.id) return
  resetForm()
  parentSubmissionId.value = undefined
  Object.assign(form, {
    companyName: row.companyName,
    submissionOrganizationId: row.submissionOrganizationId,
    employerOrganizationId: row.employerOrganizationId,
    organizationUnitId: row.organizationUnitId,
    organizationPathSnapshot: row.organizationPathSnapshot,
    employerNameSnapshot: row.employerNameSnapshot,
    groupNameSnapshot: row.groupNameSnapshot,
    recruitmentType: row.recruitmentType,
    currentStatus: '待投递'
  })
  if (!sameSubmission) Object.assign(form, {
    resumeCategory: row.resumeCategory,
    workLocation: row.workLocation,
    source: row.source,
    resumeId: row.resumeId,
    profileId: row.profileId
  })
  quickAddSourceId.value = row.id
  submissionMode.value = sameSubmission ? 'preference' : 'independent'
  formPositionTypes.value = []
  formResumeCategories.value = sameSubmission ? [] : splitMultiValue(row.resumeCategory)
  resumeAliasAuto.value = true
  regenerateResumeAlias()
  dialogVisible.value = true
}

function openPreferenceFromParent(row: ApplicationRow) {
  if (!row.submissionGroupId) return
  resetForm()
  Object.assign(form, {
    companyName: row.companyName,
    submissionOrganizationId: row.submissionOrganizationId,
    employerOrganizationId: row.employerOrganizationId,
    organizationUnitId: row.organizationUnitId,
    organizationPathSnapshot: row.organizationPathSnapshot,
    employerNameSnapshot: row.employerNameSnapshot,
    groupNameSnapshot: row.groupNameSnapshot,
    recruitmentType: row.recruitmentType,
    currentStatus: '待投递'
  })
  quickAddSourceId.value = undefined
  parentSubmissionId.value = row.submissionGroupId
  submissionMode.value = 'preference'
  formPositionTypes.value = []
  formResumeCategories.value = []
  resumeAliasAuto.value = true
  dialogVisible.value = true
}

function handleRowCommand(command: string, row: JobApplication) {
  const applicationRow = row as ApplicationRow
  const source = preferenceSource(applicationRow)
  if (command === 'new-position') openQuickAdd(source, false)
  if (command === 'next-preference') applicationRow.submissionGroupId
    ? openPreferenceFromParent(applicationRow)
    : openQuickAdd(source, true)
  if (command === 'delete') {
    applicationRow.multiPreference ? removeSubmission(applicationRow) : (row.id && remove(row.id))
  }
}

function handleResumeChange(resumeId?: number) {
  resumeAliasAuto.value = true
  if (!resumeId) {
    form.resumeAlias = ''
    return
  }
  regenerateResumeAlias()
}

function regenerateResumeAlias() {
  const resume = resumes.value.find((item) => item.id === form.resumeId)
  const extension = fileExtension(resume?.fileName)
  const customTemplates = parseSavedResumeNamingTemplates(namingSettings.resumeCustomNamingTemplates, namingSettings.resumeCustomNamingTemplate)
  const template = templateForSelection(namingTemplate.value, customTemplates)
  const name = renderResumeName(template, {
    ownerName: namingSettings.resumeOwnerName,
    ownerSchool: namingSettings.resumeOwnerSchool,
    graduationYear: namingSettings.resumeGraduationYear,
    companyName: form.companyName,
    positionName: form.positionName,
    positionType: formPositionTypes.value.join('、'),
    recruitmentType: form.recruitmentType,
    resumeCategory: formResumeCategories.value.join('、'),
    workLocation: form.workLocation
  })
  form.resumeAlias = name ? `${name}${extension}` : (resume?.fileName || '')
  resumeAliasAuto.value = true
}

function fileExtension(fileName?: string) {
  const index = fileName?.lastIndexOf('.') ?? -1
  return index >= 0 && fileName ? fileName.slice(index) : ''
}

function profileVersionName(profileId?: string) {
  if (!profileId) return '未关联'
  const item = profileVersions.value.find(profile => profile.profileId === profileId)
  return item ? (item.defaultProfile ? `${item.name}（默认）` : item.name) : '档案版本已不存在'
}

async function save() {
  form.positionType = formPositionTypes.value.join('、')
  form.resumeCategory = formResumeCategories.value.join('、')
  if (!formStatusOptions.value.includes(form.currentStatus)) {
    form.currentStatus = formStatusOptions.value[0] || '待投递'
  }
  if (!form.companyName?.trim() || !form.positionName?.trim()) {
    ElMessage.warning('请填写公司名称和岗位名称')
    return
  }
  if (form.id) {
    await applicationApi.update(form.id, form)
  } else if (parentSubmissionId.value) {
    await applicationApi.createPreference(parentSubmissionId.value, form)
  } else if (quickAddSourceId.value) {
    await applicationApi.createFrom(quickAddSourceId.value, form, submissionMode.value === 'preference')
  } else {
    form.submissionGroupId = undefined
    form.preferenceOrder = submissionMode.value === 'preference' ? 1 : undefined
    await applicationApi.create(form)
  }
  dialogVisible.value = false
  quickAddSourceId.value = undefined
  parentSubmissionId.value = undefined
  await Promise.all([load(), loadFilterOptions()])
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('确认删除这条投递记录？', '删除投递记录', { type: 'warning' })
  } catch {
    return
  }
  await applicationApi.remove(id)
  await load()
}

async function removeSubmission(row: ApplicationRow) {
  const ids = (row.preferences || []).map(item => item.id).filter((id): id is number => typeof id === 'number')
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除这次投递及其 ${ids.length} 个志愿吗？此操作无法恢复。`, '删除整次投递', { type: 'warning' })
  } catch {
    return
  }
  await applicationApi.batchRemove(ids)
  await load()
}
</script>
