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

// 同一节课在表里可能重复出现（多行同课程同时间同地点同班级）。id 相同即视为同一条，
// 保留第一条——重复行不会多上一节课。
const unique = [];
const seenIds = new Set();
for (const r of records) {
  if (seenIds.has(r.id)) continue;
  seenIds.add(r.id);
  unique.push(r);
}

const classMap = new Map(); // 行政班 -> Map(slotKey -> 同slot的排课行)
for (const r of unique) {
  for (const cls of r.classes.flatMap(expandClass)) {
    classesSeen.add(cls);
    const slot = classMap.get(cls) ?? new Map();
    classMap.set(cls, slot);
    const key = `${r.name}#${r.day}#${r.periods}#${r.weeks}`;
    const group = slot.get(key) ?? [];
    group.push(r);
    slot.set(key, group);
  }
}

const classesOut = [...classMap.entries()]
  .sort(([a], [b]) => (a < b ? -1 : 1))
  .map(([cls, slot]) => ({
    name: cls,
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
  }));

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
