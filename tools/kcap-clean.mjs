#!/usr/bin/env node
/**
 * 教务处「课程总表（本科生）」xlsx -> App 资源里的课表数据。
 *
 * 为什么在电脑上洗：教务处的原始 xlsx 里，一节课的信息挤在「上课时间」这一个单元格里
 * （`周一第3、4节{第1-16周};周三第7、8节{第1,3,5周};`），单元格样式与合并规则也只有桌面
 * Excel 才处理得干净。Android 侧不引 POI（几 MB 依赖）、也不引 fastexcel（它靠 StAX，
 * 而 android.jar 里没有 javax.xml.stream），所以把清洗留在开发机上，
 * 手机只读已经结构化的 JSONL——一个学期一个文件，随包发布，每学期换一次包。
 *
 * 用法：
 *   node tools/kcap-clean.mjs <原始.xlsx> --semester 2026-2027-1 \
 *        --label "2026-2027-1" --term-start 2026-09-07 --out app/src/main/assets/kcap
 *
 * 原始文件从 https://jwc.qdu.edu.cn/xxgk/kcap.htm 的当期文章里下载附件获得。
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const DAY_INDEX = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 日: 7 };

function arg(name, fallback = null) {
  const at = process.argv.indexOf(`--${name}`);
  return at >= 0 ? process.argv[at + 1] : fallback;
}

const input = process.argv[2];
if (!input || !fs.existsSync(input)) {
  console.error('用法：node tools/kcap-clean.mjs <原始.xlsx> --semester <学期标识> --term-start <第一周周一>');
  process.exit(1);
}
const semester = arg('semester');
const termStart = arg('term-start');
const label = arg('label', semester);
const outDir = arg('out', 'app/src/main/assets/kcap');
if (!semester || !termStart) {
  console.error('缺 --semester 或 --term-start，两者都不能猜。');
  process.exit(1);
}

// ---------- xlsx 就是一个 zip，只取需要的两个成员 ----------
function zipEntries(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0 && i > buf.length - 66000; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('不是 zip：找不到中央目录结尾');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const out = new Map();
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('中央目录结构不认识');
    const method = buf.readUInt16LE(p + 10);
    const compSize = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localOff = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
    out.set(name, { method, compSize, localOff });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return { buf, out };
}

function readMember(zip, name) {
  const entry = zip.out.get(name);
  if (!entry) throw new Error(`压缩包里缺少 ${name}`);
  const { buf } = zip;
  const localOff = entry.localOff;
  if (buf.readUInt32LE(localOff) !== 0x04034b50) throw new Error(`${name} 的本地头不认识`);
  const nameLen = buf.readUInt16LE(localOff + 26);
  const extraLen = buf.readUInt16LE(localOff + 28);
  const start = localOff + 30 + nameLen + extraLen;
  const raw = buf.subarray(start, start + entry.compSize);
  return entry.method === 0 ? raw : zlib.inflateRawSync(raw);
}

// ---------- 共享字符串与行 ----------
const decode = (s) =>
  s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'").replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
    .replace(/&amp;/g, '&');

function sharedStrings(xml) {
  const list = [];
  for (const si of xml.matchAll(/<si>([\s\S]*?)<\/si>/g)) {
    list.push(decode([...si[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((m) => m[1]).join('')));
  }
  return list;
}

function rowsOf(xml, strings) {
  const rows = [];
  for (const row of xml.matchAll(/<row[^>]*r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g)) {
    const cells = {};
    for (const c of row[2].matchAll(/<c r="([A-Z]+)\d+"([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const inner = c[3] || '';
      const v = inner.match(/<v[^>]*>([\s\S]*?)<\/v>/);
      const isText = inner.match(/<is>[\s\S]*?<t[^>]*>([\s\S]*?)<\/t>/);
      let text = '';
      if (v) text = /t="s"/.test(c[2]) ? (strings[Number(v[1])] ?? '') : decode(v[1]);
      else if (isText) text = decode(isText[1]);
      if (text !== '') cells[c[1]] = text;
    }
    rows.push({ n: Number(row[1]), cells });
  }
  return rows;
}

// ---------- 「上课时间」单元格 -> 结构化段 ----------
function parseWeeks(spec) {
  // 原始写法两种：第1-16周 区间、第1,3,5周 枚举；清洗后统一成同样的字符串交给手机解析。
  return spec.replace(/^第/, '').replace(/周$/, '').trim();
}

function parseTime(cell) {
  const segments = [];
  for (const raw of cell.split(';')) {
    const s = raw.trim();
    if (!s) continue;
    const day = s.match(/^周([一二三四五六日])/);
    const weeks = s.match(/\{([^}]*)\}/);
    const periods = [...s.matchAll(/第([\d、,]+)节/g)]
      .flatMap((p) => p[1].split(/[,、]/))
      .map((x) => Number(x.trim()))
      .filter((x) => Number.isInteger(x) && x > 0 && x <= 20);
    if (!day || !weeks || periods.length === 0) {
      segments.push(null);
      continue;
    }
    segments.push({
      day: DAY_INDEX[day[1]],
      periods: [...new Set(periods)].sort((a, b) => a - b),
      weeks: parseWeeks(weeks[1]),
    });
  }
  return segments;
}

// ---------- 主流程 ----------
const zip = zipEntries(fs.readFileSync(input));
const strings = sharedStrings(readMember(zip, 'xl/sharedStrings.xml').toString('utf8'));
const sheetName = 'xl/worksheets/sheet1.xml';
const rows = rowsOf(readMember(zip, sheetName).toString('utf8'), strings);

const headerRow = rows.find((r) => r.cells.A === '通知单号' || Object.values(r.cells).includes('上课时间'));
if (!headerRow) throw new Error('没找到表头行（A 列应为「通知单号」），教务处的表样大概变了，先人工看一眼再改脚本');

const byName = new Map(Object.entries(headerRow.cells).map(([col, text]) => [text.replace(/\s/g, ''), col]));
const REQUIRED = ['校区', '开课院系', '教师姓名', '课程编号', '课程名称', '上课班级', '上课时间', '上课地点', '学分'];
const missing = REQUIRED.filter((k) => !byName.has(k));
if (missing.length) {
  console.error('表头缺列：', missing.join('、'));
  console.error('当前表头：', [...byName.keys()].join(' | '));
  process.exit(1);
}
const col = (name) => byName.get(name);
const cellOf = (row, name) => row.cells[col(name)] ?? '';

// FNV-1a：同一门课的同一节课可能出现在多行（不同班级、不同老师），
// 行号会变，但内容不会，所以用内容做主键，重新下载同一份表能得到同样的 id。
function hash(...parts) {
  let h = 0x811c9dc5;
  for (const s of parts.join('\u0001')) {
    h ^= s.charCodeAt(0);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(16).padStart(8, '0');
}

const records = [];
const classesSeen = new Set();
let dropped = 0;
let droppedRows = 0;
for (const row of rows) {
  if (row.n <= headerRow.n) continue;
  const name = cellOf(row, '课程名称');
  if (!name) continue;
  const parsed = parseTime(cellOf(row, '上课时间'));
  const usable = parsed.filter(Boolean);
  dropped += parsed.length - usable.length;
  if (!usable.length) { droppedRows++; continue; }
  const classes = cellOf(row, '上课班级').split(',').map((x) => x.trim()).filter(Boolean);
  classes.forEach((c) => classesSeen.add(c));
  const rooms = cellOf(row, '上课地点').split(',').map((x) => x.trim()).filter(Boolean);
  const code = cellOf(row, '课程编号');
  usable.forEach((seg, i) => {
    const room = rooms[i] ?? rooms[0] ?? '';
    const teacher = cellOf(row, '教师姓名');
    records.push({
      id: `${code}#${seg.day}#${seg.periods.join(',')}#${hash(name, teacher, seg.weeks, room, classes.join(','))}`,
      code,
      name,
      teacher,
      dept: cellOf(row, '开课院系'),
      campus: cellOf(row, '校区'),
      classes,
      kind: cellOf(row, '课程大类') || cellOf(row, '课程类别'),
      day: seg.day,
      periods: seg.periods.join(','),
      weeks: seg.weeks,
      room,
      credits: Number(cellOf(row, '学分')) || 0,
      hours: Number.parseInt(cellOf(row, '总学时'), 10) || 0,
    });
  });
}

// ---------- 按班级重建课表 ----------
// 「上课班级」里的 `26自动化[01-07]班` 是区间写法，指 01 到 07 共 7 个班，先展开成行政班。
// 同一门课同一时段可能开十几个平行教学班（大学英语等公共课），这些行的「上课班级」
// 都是同一批班的并集，总表没有「某个班的学生分到哪个教学班」的信息。
// 每个班的课表里，这样的组只留一条：教师把组里的都列上（任选其一），地点留空（去了才知道），
// 不合并的话同一格会并排十几条，挤成没有文字的细条带。
function expandClass(raw) {
  const m = raw.match(/^(.*?)\[(\d+)-(\d+)\](.*)$/);
  if (!m) return [raw];
  const a = Number(m[2]);
  const b = Number(m[3]);
  if (!Number.isInteger(a) || !Number.isInteger(b) || b < a || b - a > 99) return [raw];
  const out = [];
  for (let i = a; i <= b; i++) out.push(m[1] + String(i).padStart(m[2].length, '0') + m[4]);
  return out;
}

// ---------- 整体班名展开 ----------
// 「26新闻班」这种不带编号的整体名，按教务处的口径指该专业下全部编号班
// （26新闻01班…），与「26音乐学[01-02]班」的区间写法同义；创新班、方向班
// （小教PD/学前YS这类）是独立班，不在其中。表里同时出现编号班的，把整体名
// 的课并进每个编号班、整体名本身不再是独立条目——否则勾了编号班的学生
// 会漏掉写给全专业的课，勾了整体名的人又不知道它指哪些班。
// 表里只出现整体名的专业（如各年级预防班、口腔班），整体名就是唯一条目，
// 语义即「全专业」，课表内容与按编号班选完全一致，保留原样不拆。
const classNameUniverse = new Set();
for (const r of records) for (const c of r.classes.flatMap(expandClass)) classNameUniverse.add(c);

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

const bareRemap = new Map(); // 整体班名 -> 编号班数组
for (const name of classNameUniverse) {
  const m = name.match(/^(.+)班$/);
  if (!m || /\d$/.test(m[1])) continue; // 编号班（尾字符是数字）不是整体名
  const re = new RegExp(`^${escapeRegExp(m[1])}\\d+班$`);
  const sibs = [...classNameUniverse].filter((x) => x !== name && re.test(x)).sort();
  if (sibs.length) bareRemap.set(name, sibs);
}
if (bareRemap.size) {
  console.log('整体班名展开（全专业名 → 编号班，创新班等独立班不受影响）：');
  for (const [k, v] of bareRemap) console.log(`  ${k} → ${v.join('、')}`);
}

// ---------- 学院分类 ----------
// 对照教务处《本科专业设置一览表（2025年9月）》（docs/interfaces/jwc-kcap.md 引用的
// 官方 PDF，86 个专业 / 28 个学院）给每个班归属学院。班名是专业简称（26预防、24化学师、
// 26图灵），词典按「去年级、去编号、去班字」后的关键词人工核对录入；拿不准的简称
// 用该班专业课（排除体育/大外/马院/学生处等公共教学单位）的开课院系多数票裁决过。
// 词典没有的键退回「开课院系多数票」，仍解不出留空——勾选列表里归入「未分类」，
// 并在清洗日志点名，不许静默安一个学院。
const MAJOR_COLLEGE = {
  // 历史学院
  '历史': '历史学院', '历史师': '历史学院',
  // 经济学院
  '财政': '经济学院', '金融': '经济学院', '金融创新': '经济学院', '经济': '经济学院',
  '经济方向': '经济学院', '经济理论': '经济学院', '数量经济': '经济学院',
  '数量经济创新': '经济学院', '国贸': '经济学院', '跨境电商': '经济学院',
  '金融fx': '经济学院', '金融学fx': '经济学院',
  // 法学院
  '法学': '法学院', '法学创新': '法学院', '法学fx': '法学院', '法学（创新班）': '法学院',
  // 马克思主义学院
  '思政': '马克思主义学院',
  // 政治与公共管理学院
  '行管': '政治与公共管理学院', '行管数创': '政治与公共管理学院',
  '行管（数创）': '政治与公共管理学院', '国政': '政治与公共管理学院',
  '国政（创新班）': '政治与公共管理学院',
  // 教育科学学院
  '小教': '教育科学学院', '小教G': '教育科学学院', '小教PD': '教育科学学院',
  '小教PD创新': '教育科学学院', '学前': '教育科学学院', '学前G': '教育科学学院',
  '学前YS': '教育科学学院', '心理': '教育科学学院', '心理创新': '教育科学学院',
  '教技智育': '教育科学学院', '智能教育': '教育科学学院', '新疆预科': '教育科学学院',
  // 体育学院
  '体育': '体育学院', '体育师': '体育学院',
  // 文学与新闻传播学院
  '编导': '文学与新闻传播学院', '新闻': '文学与新闻传播学院', '中文': '文学与新闻传播学院',
  '中文fx': '文学与新闻传播学院', '汉语师': '文学与新闻传播学院', '汉语师G': '文学与新闻传播学院',
  // 历史学院（24人文班的归属由专业课开课院系多数票裁决为历史学院）
  '人文': '历史学院',
  // 外语学院
  '英语': '外语学院', '英语师': '外语学院', '英语师G': '外语学院', '英语演讲': '外语学院',
  '德语': '外语学院', '法语': '外语学院', '西班牙语': '外语学院', '西班牙语01': '外语学院',
  '日语': '外语学院', '朝鲜语': '外语学院', '英俄创新': '外语学院',
  '英俄创新班01': '外语学院', '英俄创新班02': '外语学院', '语言创新': '外语学院',
  '语言创新班01': '外语学院', '语言创新班02': '外语学院', '医学英语': '外语学院',
  // 数学与统计学院
  '应数': '数学与统计学院', '应数师': '数学与统计学院', '应数创新': '数学与统计学院',
  '统计': '数学与统计学院',
  // 物理科学学院
  '光电': '物理科学学院', '新能源': '物理科学学院', '应物': '物理科学学院',
  '应物创新': '物理科学学院', '物理师': '物理科学学院',
  // 化学化工学院
  '化工': '化学化工学院', '应化': '化学化工学院', '应化创新': '化学化工学院',
  '化学师': '化学化工学院',
  // 生命科学学院
  '生物': '生命科学学院', '生物(中外)': '生命科学学院', '生物（中外）': '生命科学学院',
  '生物华大': '生命科学学院', '生物技术（华大）': '生命科学学院', '食品': '生命科学学院',
  '巴斯德': '生命科学学院',
  // 机电工程学院
  '机械': '机电工程学院', '机械创新': '机电工程学院', '机械创新班01': '机电工程学院',
  '机械创新班02': '机电工程学院', '工业设计': '机电工程学院', '能动': '机电工程学院',
  '测控': '机电工程学院', '制冷': '机电工程学院', '热能': '机电工程学院',
  '储能': '机电工程学院', '智能制造': '机电工程学院', '智能': '机电工程学院',
  '智能创新': '机电工程学院',
  // 材料科学与工程学院
  '高材': '材料科学与工程学院', '复材': '材料科学与工程学院', '智材': '材料科学与工程学院',
  '高创': '材料科学与工程学院', '先进材料创新': '材料科学与工程学院', '材料类': '材料科学与工程学院',
  // 自动化学院
  '自动化': '自动化学院', '机器人': '自动化学院', '机器人工程': '自动化学院',
  '张嗣瀛创新': '自动化学院',
  // 电气工程学院
  '电气': '电气工程学院',
  // 电子信息学院（微纳技术学院）
  '电信': '电子信息学院（微纳技术学院）', '电信中外': '电子信息学院（微纳技术学院）',
  '集成电路': '电子信息学院（微纳技术学院）', '微纳英才': '电子信息学院（微纳技术学院）',
  '通信': '电子信息学院（微纳技术学院）',
  // 计算机科学技术学院
  '计科': '计算机科学技术学院', '计算机(中外)': '计算机科学技术学院',
  '计算机（中外）': '计算机科学技术学院', '软件': '计算机科学技术学院',
  '软件外包': '计算机科学技术学院', '信息安全': '计算机科学技术学院',
  '安全': '计算机科学技术学院', '图灵': '计算机科学技术学院', '人工智能': '计算机科学技术学院',
  '人工智能创新': '计算机科学技术学院', '智科': '计算机科学技术学院',
  // 环境与地理科学学院
  '环境': '环境与地理科学学院', '地理师': '环境与地理科学学院',
  // 纺织服装学院
  '纺织': '纺织服装学院', '纺工创新': '纺织服装学院', '非织造': '纺织服装学院',
  '轻化': '纺织服装学院', '服装': '纺织服装学院', '服装创新': '纺织服装学院',
  '服表': '纺织服装学院', '服设': '纺织服装学院', '表演': '纺织服装学院', '纺装': '纺织服装学院',
  // 青岛医学院
  '临床': '青岛医学院', '临床（5+3）': '青岛医学院', '临床（5+3）沈福彭': '青岛医学院',
  '临床（5+3）ek': '青岛医学院', '临床沈福彭班（5+3）06': '青岛医学院',
  '影像': '青岛医学院', '检验': '青岛医学院', '智能医学': '青岛医学院',
  '数字智慧医学': '青岛医学院', '数字智慧医学w': '青岛医学院',
  // 口腔医学院
  '口腔': '口腔医学院',
  // 公共卫生学院
  '预防': '公共卫生学院', '预防创新': '公共卫生学院', '环境与健康': '公共卫生学院',
  '公共卫生学导论': '公共卫生学院', '医药数据': '公共卫生学院',
  // 药学院
  '药学': '药学院',
  // 护理学院
  '护理': '护理学院',
  // 商学院
  '工商': '商学院', '工商（国商）': '商学院', '工商（企管）': '商学院',
  '工商（人力）': '商学院', '工商（数营）': '商学院', '工商（营销）': '商学院',
  '会计': '商学院', '会计（财管）': '商学院', '会计创新': '商学院', '会计学': '商学院',
  '会计fx': '商学院', '会计fx02（ACCA）': '商学院', '供应链': '商学院', '物流': '商学院',
  '旅游': '商学院', '旅游（中外）': '商学院', '旅游DK': '商学院', '酒店dk': '商学院',
  '标准': '商学院', '信管': '商学院', '信管01': '商学院', '信管02': '商学院',
  '商学院24信管': '商学院',
  '级旅游管理01': '商学院', '级旅游管理02': '商学院', '级旅游管理03': '商学院',
  '级旅游管理04': '商学院',
  // 艺术学院
  '声乐': '艺术学院', '键盘': '艺术学院', '民族管弦': '艺术学院', '西洋管弦': '艺术学院',
  '作曲': '艺术学院', '舞蹈': '艺术学院', '国画': '艺术学院', '油画': '艺术学院',
  '版画': '艺术学院', '绘画': '艺术学院', '绘画创新': '艺术学院', '视传': '艺术学院',
  '视创': '艺术学院', '环设': '艺术学院', '环境设计': '艺术学院', '公共艺术': '艺术学院',
  '音乐表演': '艺术学院', '音乐学': '艺术学院', '数媒': '艺术学院', '时创': '艺术学院',
  '空间创新': '艺术学院', '景观': '艺术学院', '室内': '艺术学院', '艺科': '艺术学院',
  '电子音乐w': '艺术学院', '人居环境': '艺术学院', '装雕': '艺术学院',
};

/** 去年级、去尾部编号与「班」字、去空白后的专业关键词；带学院前缀的先剥掉前缀。 */
function majorKeyOf(name) {
  let n = name.replace(/\s+/g, '');
  n = n.replace(/^[一-龥]{1,8}学院/, ''); // 原始表偶尔把学院名写进班名（「商学院 24信管01班」）
  n = n.replace(/^2\d/, '');
  n = n.replace(/\d+班$/, '');
  n = n.replace(/班$/, '');
  return n.replace(/(\(|（)/g, '（').replace(/(\)|）)/g, '）'); // 全角括号归一（「临床（5+3)01班」半角混用）
}

// 公共教学单位：他们的课全校都上，不能当班级归属的证据
const PUBLIC_DEPTS = new Set(['体育学院', '公共外语教育学院', '马克思主义学院', '学生处']);

function collegeOf(cls) {
  const hit = MAJOR_COLLEGE[majorKeyOf(cls)];
  if (hit) return hit;
  // 词典没有：用专业课开课院系多数票（教学班号、分项编号这类噪声班名大多能落回开出学院）
  const votes = [...(deptVotes.get(cls) ?? new Map())].sort((a, b) => b[1] - a[1]);
  // 开课院系列用的是官方简称，与专业设置表的学院全名对齐（电子信息学院全名带微纳括注）
  const dept = votes[0]?.[0] ?? '';
  return dept === '电子信息学院' ? '电子信息学院（微纳技术学院）' : dept;
}

// 同一节课在表里可能重复出现（多行同课程同时间同地点同班级）。id 相同即视为同一条，
// 保留第一条——重复行不会多上一节课。
const unique = [];
const seenIds = new Set();
for (const r of records) {
  if (seenIds.has(r.id)) continue;
  seenIds.add(r.id);
  unique.push(r);
}

// 每个班自己的专业课开课院系分布，词典查不到时按多数票裁决
const deptVotes = new Map(); // 班名 -> Map(开课院系 -> 行数)
for (const r of unique) {
  for (const cls of r.classes.flatMap(expandClass).flatMap((c) => bareRemap.get(c) ?? [c])) {
    if (PUBLIC_DEPTS.has(r.dept)) continue;
    const m = deptVotes.get(cls) ?? new Map();
    deptVotes.set(cls, m);
    m.set(r.dept, (m.get(r.dept) ?? 0) + 1);
  }
}

// 临时班（「23临班383班」这类临床临时教学组）不是行政班：不进班级清单、不参与学院
// 归类，勾选列表里根本看不到。它们承载的课只是把行政班的课拆组重复排了一遍，
// 隐藏不会让任何行政班丢课（2026-11-07 用户口径）。
function isTemporaryClass(name) {
  return /^(2\d)?临班\d+班?$/.test(name);
}

const classMap = new Map(); // 行政班 -> Map(slotKey -> 同slot的排课行)
for (const r of unique) {
  for (const cls of r.classes.flatMap(expandClass).flatMap((c) => bareRemap.get(c) ?? [c])) {
    if (isTemporaryClass(cls)) continue;
    classesSeen.add(cls);
    const slot = classMap.get(cls) ?? new Map();
    classMap.set(cls, slot);
    const key = `${r.name}#${r.day}#${r.periods}#${r.weeks}`;
    const group = slot.get(key) ?? [];
    group.push(r);
    slot.set(key, group);
  }
}

const unmatchedKeys = new Set();
const classesOut = [...classMap.entries()]
  .sort(([a], [b]) => (a < b ? -1 : 1))
  .map(([cls, slot]) => {
    const college = collegeOf(cls);
    if (!college || (!MAJOR_COLLEGE[majorKeyOf(cls)] && college)) unmatchedKeys.add(`${cls} → ${college || '（空）'}`);
    return {
      name: cls,
      college,
      lessons: [...slot.values()].map((group) => {
      const first = group[0];
      const teachers = [...new Set(group.map((r) => r.teacher).filter(Boolean))].join('、');
      const rooms = [...new Set(group.map((r) => r.room).filter(Boolean))];
      return {
        id: hash(first.name, teachers, first.day, first.periods, first.weeks, rooms[0] ?? '', cls),
        name: first.name,
        code: first.code,
        teachers,
        dept: first.dept,
        campus: first.campus,
        kind: first.kind,
        day: first.day,
        // records 里的 periods 是逗号串（JSONL 时代的写法），按班级结构里必须是数组
        periods: first.periods.split(',').map((x) => Number(x.trim())),
        weeks: first.weeks,
        // 平行教学班组（多行同课同时段）里学生分到哪个教学班，总表没写：地点留空不误导。
        // 合班同教师的行（同一条时间多个班）是同一节课，地点照常保留。
        room: group.length > 1 ? '' : (rooms[0] ?? ''),
        credits: first.credits,
        hours: first.hours,
      };
    }).sort((a, b) => a.day - b.day || (a.periods[0] ?? 0) - (b.periods[0] ?? 0)),
    };
  });

const byCollege = new Map();
for (const c of classesOut) byCollege.set(c.college || '（未分类）', (byCollege.get(c.college || '（未分类）') ?? 0) + 1);
console.log('学院分布：', [...byCollege.entries()].sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k}:${v}`).join(' '));
if (unmatchedKeys.size) {
  console.log('以下班级不是走词典（按专业课开课院系多数票裁决或留空），请人工核对：');
  for (const line of [...unmatchedKeys].sort()) console.log('  ' + line);
}

const outFile = path.join(outDir, `${semester}.timetables.json`);
fs.writeFileSync(outFile, JSON.stringify({ id: semester, classes: classesOut }));

const indexPath = path.join(outDir, 'index.json');
const index = fs.existsSync(indexPath) ? JSON.parse(fs.readFileSync(indexPath, 'utf8')) : { semesters: [] };
index.semesters = index.semesters.filter((s) => s.id !== semester);
index.semesters.unshift({
  id: semester,
  label: label || semester,
  termStart,
  file: `${semester}.timetables.json`,
  records: unique.length,
  classes: classMap.size,
  source: 'https://jwc.qdu.edu.cn/xxgk/kcap.htm',
  generatedAt: new Date().toISOString().slice(0, 10),
});
fs.writeFileSync(indexPath, JSON.stringify(index, null, 2) + '\n');

const bytes = fs.statSync(outFile).size;
const gz = zlib.gzipSync(fs.readFileSync(outFile)).length;
console.log(`学期 ${semester}：${unique.length} 条排课记录，展开为 ${classesOut.length} 个行政班的独立课表（合并重复行 ${records.length - unique.length} 条）`);
console.log(`写入 ${outFile}（${(bytes / 1024).toFixed(0)} KB，压入 APK 约 ${(gz / 1024).toFixed(0)} KB）`);
console.log(`跳过 ${dropped} 段没有节次的时间（集中性实践环节按周排，网格里放不下），涉及 ${droppedRows} 行整行无排课`);
