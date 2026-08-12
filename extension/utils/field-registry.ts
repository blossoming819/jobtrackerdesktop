export type FieldSignals = {
  primary: string[]
  attributes: string[]
  context: string[]
}

type FieldDefinition = {
  key: string
  aliases: string[]
  scopes?: string[]
  excludes?: string[]
}

const COMMON_EXCLUDES = ['内推码', '推荐码', '验证码', '密码', '搜索', 'keyword', 'captcha', 'password', 'referral code']

const FIELDS: FieldDefinition[] = [
  { key: 'basic.nameCn', aliases: ['中文姓名', '真实姓名', '候选人姓名', '姓名', 'full name', 'legal name', 'candidate name'], excludes: ['项目名称', '公司名称', '学校名称', '奖项名称', '用户名'] },
  { key: 'basic.gender', aliases: ['性别', 'gender', 'sex'] },
  { key: 'contact.phone', aliases: ['手机号码', '手机号', '联系电话', '移动电话', '手机', 'mobile phone', 'phone number', 'telephone', 'mobile'] },
  { key: 'contact.email', aliases: ['电子邮箱', '邮箱地址', '联系邮箱', '邮箱', 'email address', 'e-mail', 'email'] },
  { key: 'contact.currentCity', aliases: ['当前城市', '现居城市', '居住城市', '所在地', '现居地', 'current city', 'city of residence', 'location'] },

  { key: 'education.school', aliases: ['毕业院校', '学校名称', '就读学校', '院校名称', '学校', 'university', 'college name', 'school'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.degree', aliases: ['最高学历', '学历层次', '教育程度', '学历', '学位', 'degree', 'education level'], excludes: ['学历类型', '学历形式', '学习形式', '培养方式'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.major', aliases: ['所学专业', '专业名称', '主修专业', '专业', 'major', 'field of study'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.college', aliases: ['学院名称', '所在学院', '院系', '学院', 'faculty', 'department'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.startDate', aliases: ['入学时间', '入学日期', '教育开始时间', '开始时间', '开始日期', 'start date', 'from'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.expectedGraduation', aliases: ['预计毕业时间', '毕业时间', '毕业日期', '结束时间', '结束日期', 'graduation date', 'end date', 'to'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.studyMode', aliases: ['学历类型', '学历形式', '学习形式', '培养方式', '是否全日制', '学习类型', 'study mode', 'education type'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },
  { key: 'education.gpa', aliases: ['绩点', '成绩排名', '专业排名', 'gpa', 'grade point'], scopes: ['教育经历', '教育背景', '学历信息', 'education'] },

  { key: 'work.company', aliases: ['公司名称', '单位名称', '雇主名称', '任职公司', '实习公司', 'company', 'employer'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.role', aliases: ['职位名称', '岗位名称', '实习岗位', '担任职位', '职位', '岗位', 'job title', 'position', 'role'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.department', aliases: ['所属部门', '任职部门', '部门', 'department'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.city', aliases: ['工作城市', '工作地点', '任职地点', 'city', 'location'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.startDate', aliases: ['入职时间', '开始时间', '开始日期', 'start date', 'from'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.endDate', aliases: ['离职时间', '结束时间', '结束日期', 'end date', 'to'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.employmentType', aliases: ['工作类型', '雇佣类型', '任职类型', 'employment type'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.industry', aliases: ['所属行业', '公司行业', '行业', 'industry'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.responsibilities', aliases: ['工作职责', '工作内容', '职责描述', '主要职责', 'responsibilities', 'job description'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.achievements', aliases: ['工作成果', '业绩成果', '主要业绩', '成果与量化', 'achievements'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },
  { key: 'work.technologies', aliases: ['使用技术', '技术工具', '技术栈', 'technologies', 'tools'], scopes: ['工作经历', '实习经历', '职业经历', 'work experience', 'internship'] },

  { key: 'projects.name', aliases: ['项目名称', 'project name'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.role', aliases: ['项目角色', '担任角色', 'role'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.startDate', aliases: ['开始时间', '开始日期', 'start date', 'from'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.endDate', aliases: ['结束时间', '结束日期', 'end date', 'to'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.type', aliases: ['项目类型', 'project type'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.teamSize', aliases: ['团队规模', '团队人数', 'team size'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.url', aliases: ['项目链接', '作品链接', 'project url', 'project link'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.technologies', aliases: ['技术栈', '项目技术', 'technologies', 'tech stack'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.description', aliases: ['项目简介', '项目描述', '项目内容', 'project description'], scopes: ['项目经历', '项目经验', 'project experience'] },
  { key: 'projects.contributions', aliases: ['个人贡献', '负责内容', '项目职责', 'contributions'], scopes: ['项目经历', '项目经验', 'project experience'] },

  { key: 'honors.name', aliases: ['奖项名称', '荣誉名称', '获奖名称', 'award name'], scopes: ['荣誉', '奖项', '获奖', '竞赛', 'honors', 'awards'] },
  { key: 'honors.level', aliases: ['奖项等级', '获奖等级', '荣誉级别', 'award level'], scopes: ['荣誉', '奖项', '获奖', '竞赛', 'honors', 'awards'] },
  { key: 'honors.date', aliases: ['获奖日期', '获奖时间', 'award date'], scopes: ['荣誉', '奖项', '获奖', '竞赛', 'honors', 'awards'] },
  { key: 'honors.category', aliases: ['奖项类型', '荣誉类型', 'award type'], scopes: ['荣誉', '奖项', '获奖', '竞赛', 'honors', 'awards'] },
  { key: 'honors.description', aliases: ['奖项描述', '荣誉描述', '获奖描述', 'award description'], scopes: ['荣誉', '奖项', '获奖', '竞赛', 'honors', 'awards'] },
]

function normalize(value: string) {
  return value
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/[\s_:：/\\|·*（）()\[\]【】<>-]+/g, ' ')
    .trim()
}

function contains(value: string, candidate: string) {
  const alias = normalize(candidate)
  if (!alias) return false
  if (/^[a-z0-9 ]+$/.test(alias)) return (` ${value} `).includes(` ${alias} `) || value === alias
  return value.includes(alias)
}

function aliasScore(values: string[], aliases: string[], exact: number, partial: number) {
  let best = 0
  for (const raw of values) {
    const value = normalize(raw)
    for (const alias of aliases) {
      const normalizedAlias = normalize(alias)
      if (value === normalizedAlias) best = Math.max(best, exact)
      else if (contains(value, alias)) best = Math.max(best, partial + Math.min(3, normalizedAlias.length / 8))
    }
  }
  return best
}

export function matchField(signals: FieldSignals) {
  const all = [...signals.primary, ...signals.attributes, ...signals.context].map(normalize).filter(Boolean)
  const direct = [...signals.primary.slice(0, 3), ...signals.attributes].map(normalize).filter(Boolean)
  if (direct.some(value => COMMON_EXCLUDES.some(exclude => contains(value, exclude)))) return undefined

  let winner: { key: string, score: number } | undefined
  let tied = false
  for (const field of FIELDS) {
    const excluded = field.excludes?.some(exclude => all.some(value => contains(value, exclude)))
    if (excluded) continue
    let score = aliasScore(signals.primary, field.aliases, 12, 8)
    score = Math.max(score, aliasScore(signals.attributes, field.aliases, 9, 8))
    if (score === 0) continue
    if (field.scopes?.some(scope => signals.context.some(value => contains(normalize(value), scope)))) score += 5
    if (!winner || score > winner.score) { winner = { key: field.key, score }; tied = false }
    else if (score === winner.score && field.key !== winner.key) tied = true
  }
  return winner && winner.score >= 8 && !tied ? winner.key : undefined
}
