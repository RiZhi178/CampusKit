#!/usr/bin/env node
/**
 * kcap 资产校验门禁。
 *
 * 每学期重跑 tools/kcap-clean.mjs 之后、提交资产之前跑一遍本脚本：
 * 解析器写错了、表样变了、清洗半途坏了，都会在这里以非零退出码拦下，
 * 而不是把退化数据打进 APK 等用户发现课表空白。
 *
 * 本脚本刻意**不读原始 xlsx、不复用 kcap-clean.mjs 的任何代码**：
 * 它只看 `app/src/main/assets/kcap/` 里的最终产物，用独立的实现做同一套判断。
 * 两条路得出一致的结论，才敢说产物是对的；复用同一份解析代码只能查出「写坏了」，
 * 查不出「理解错了」。
 *
 * 范围校验的口径与 core 解析器对齐（KcapParser.kt）：
 * - 节次上限 12：core 只收 1..12，超出的节次会在手机端被静默丢掉；
 * - 周次上限 40：core 的 MAX_WEEKS，周次串解出来为空的课在手机端整条消失。
 * 校验脚本按同一口径判错，才能在打 APK 之前发现「这条课手机上根本看不见」。
 *
 * 用法：
 *   node tools/kcap-validate.mjs                    # 校验默认资产目录
 *   node tools/kcap-validate.mjs --dir <资产目录>    # 换目录
 *   node tools/kcap-validate.mjs --min-records 5000 --min-classes 800
 *
 * 数量下限默认值按 2026 秋季实测（9500 条 / 1321 班）留足余量；
 * 换学期如量级确实变化（扩招、培养方案大改），用参数放宽，不要直接改默认值。
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

function arg(name, fallback = null) {
  const at = process.argv.indexOf(`--${name}`);
  return at >= 0 ? process.argv[at + 1] : fallback;
}

const dir = arg('dir', 'app/src/main/assets/kcap');
// 下限故意放宽到实测值的一半左右：门禁的职责是拦「量级塌方」（表样变了、解析坏了），
// 不是拦正常的学期间波动。
const MIN_RECORDS = Number(arg('min-records', 5000));
const MIN_CLASSES = Number(arg('min-classes', 800));
// 与 core/src/main/java/qdu/campus/data/remote/jwc/KcapParser.kt 的常量保持一致
const MAX_PERIOD = Number(arg('max-period', 12));
const MAX_WEEKS = Number(arg('max-weeks', 40));

const errors = [];
const warn = (msg) => console.log(`  [警告] ${msg}`);
const fail = (msg) => errors.push(msg);

// ---------- 周次串按 core 的语法解析 ----------
// 「1-16」「1,3,5」「1-6,15-16」混排；分隔符兼容全角逗号/分号，区间兼容全角减号与波浪线。
// core 解不出来为空的课在手机端整条消失，所以这里同样以「非空集合」为过线标准。
function parseWeeks(text) {
  if (typeof text !== 'string' || !text.trim()) return new Set();
  const out = new Set();
  for (const token of text.split(/[,，;；]/)) {
    const t = token.trim();
    if (!t) continue;
    const parts = t.split(/[-－~～]/).map((x) => x.trim());
    const from = Number.parseInt(parts[0], 10);
    if (!Number.isInteger(from)) continue;
    if (parts.length === 1) { out.add(from); continue; }
    const to = Number.parseInt(parts[1], 10);
    if (!Number.isInteger(to)) { out.add(from); continue; }
    for (let i = Math.min(from, to); i <= Math.max(from, to); i++) out.add(i);
  }
  return out;
}

// ---------- index.json ----------
const indexPath = path.join(dir, 'index.json');
if (!fs.existsSync(indexPath)) {
  console.error(`[FAIL] 找不到 ${indexPath}`);
  process.exit(1);
}
let index;
try {
  index = JSON.parse(fs.readFileSync(indexPath, 'utf8'));
} catch (e) {
  console.error(`[FAIL] index.json 不是合法 JSON：${e.message}`);
  process.exit(1);
}
if (!Array.isArray(index.semesters) || index.semesters.length === 0) {
  fail('index.json 的 semesters 为空或不是数组');
}

const seenIds = new Set();
for (const s of index.semesters ?? []) {
  const tag = `index[${s.id ?? '?'}]`;
  for (const key of ['id', 'label', 'termStart', 'file', 'records', 'classes', 'source', 'generatedAt']) {
    if (s[key] === undefined || s[key] === null || s[key] === '') fail(`${tag} 缺字段 ${key}`);
  }
  if (seenIds.has(s.id)) fail(`${tag} 学期 id 重复`);
  seenIds.add(s.id);
  const start = new Date(`${s.termStart}T00:00:00`);
  if (s.termStart && (Number.isNaN(start.getTime()) || start.getDay() !== 1)) {
    fail(`${tag} termStart「${s.termStart}」不是合法的周一日期（term-start 是第一周周一，不能猜）`);
  }
  if (!/^https:\/\/jwc\.qdu\.edu\.cn/.test(s.source ?? '')) {
    fail(`${tag} source 不是教务处域名：${s.source}`);
  }
  const file = path.join(dir, s.file ?? '');
  if (!fs.existsSync(file)) fail(`${tag} 指向的学期文件不存在：${s.file}`);
}

// ---------- 各学期产物 ----------
let totalLessons = 0;
for (const s of index.semesters ?? []) {
  const file = path.join(dir, s.file ?? '');
  if (!fs.existsSync(file)) continue; // 上面已经报过
  console.log(`校验 ${s.file} …`);
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    fail(`${s.file} 不是合法 JSON：${e.message}`);
    continue;
  }
  if (data.id !== s.id) fail(`${s.file} 的 id「${data.id}」与 index 不一致（${s.id}）`);
  if (!Array.isArray(data.classes) || data.classes.length === 0) {
    fail(`${s.file} classes 为空或不是数组`);
    continue;
  }

  // 班级数：与 index 精确对账，再卡数量下限
  if (data.classes.length !== s.classes) {
    fail(`${s.file} 实际 ${data.classes.length} 个班，index 写的是 ${s.classes}`);
  }
  if (data.classes.length < MIN_CLASSES) {
    fail(`${s.file} 班级数 ${data.classes.length} 低于下限 ${MIN_CLASSES}（量级塌方多半是表样变了）`);
  }
  if (s.records < MIN_RECORDS) {
    fail(`${s.file} index 记录数 ${s.records} 低于下限 ${MIN_RECORDS}`);
  }

  const classNames = new Set();
  let mergedCount = 0; // 地点留空的平行教学班组
  let noTeacherNoRoom = 0; // 地点与教师都空：PBL 等课程原始表教师列就是空的，已知形态，汇总不逐条刷
  for (const cls of data.classes) {
    const where = `${s.file}/${cls.name ?? '?'}`;
    if (!cls.name || typeof cls.name !== 'string') fail(`${where} 班级名为空`);
    else if (classNames.has(cls.name)) fail(`${where} 班级名重复`);
    else classNames.add(cls.name);

    if (!Array.isArray(cls.lessons) || cls.lessons.length === 0) {
      warn(`${where} 没有任何课程（空课表班级，确认是否预期）`);
      continue;
    }
    const lessonIds = new Set();
    let prevKey = null;
    for (const l of cls.lessons) {
      const at = `${where}「${l.name ?? '?'}」`;
      if (!l.name || typeof l.name !== 'string') fail(`${at} 课程名为空`);
      if (!/^[0-9a-f]{8}$/.test(l.id ?? '')) fail(`${at} id 不是 8 位内容哈希：${l.id}`);
      else if (lessonIds.has(l.id)) fail(`${at} 同班内课程 id 重复：${l.id}`);
      else lessonIds.add(l.id);

      if (!Number.isInteger(l.day) || l.day < 1 || l.day > 7) {
        fail(`${at} 星期越界：${l.day}`);
      }
      if (!Array.isArray(l.periods) || l.periods.length === 0) {
        fail(`${at} periods 为空（core 要求 List<Int>，逗号串会整学期解不出来）`);
      } else {
        for (const p of l.periods) {
          if (!Number.isInteger(p) || p < 1 || p > MAX_PERIOD) {
            // 超出 core 口径的节次在手机端被静默丢弃，这里必须拦
            fail(`${at} 节次 ${p} 超出 1..${MAX_PERIOD}（手机端会静默丢掉这条课）`);
          }
        }
        const sorted = [...l.periods].sort((a, b) => a - b);
        if (sorted.length !== new Set(l.periods).size || sorted.some((p, i) => p !== l.periods[i])) {
          warn(`${at} periods 未去重升序：${JSON.stringify(l.periods)}`);
        }
      }

      const weeks = parseWeeks(l.weeks);
      if (weeks.size === 0) {
        fail(`${at} 周次串「${l.weeks}」按 core 语法解出来为空（手机端整条消失）`);
      } else if ([...weeks].some((w) => w < 1 || w > MAX_WEEKS)) {
        fail(`${at} 周次越界 1..${MAX_WEEKS}：${l.weeks}`);
      }

      if (typeof l.teachers !== 'string') fail(`${at} teachers 不是字符串`);
      if (l.room === '') {
        mergedCount++;
        if (!l.teachers) noTeacherNoRoom++;
      }
      for (const k of ['credits', 'hours']) {
        if (typeof l[k] !== 'number' || l[k] < 0) fail(`${at} ${k} 不是非负数：${l[k]}`);
      }

      // 排序契约：day 升序，再按第一节升序（clean 侧的承诺，core 与页面都按序消费）
      const key = l.day * 100 + (l.periods[0] ?? 0);
      if (prevKey !== null && key < prevKey) warn(`${where} 课程未按 day/节次排序（${l.name}）`);
      prevKey = key;
      totalLessons++;
    }
  }
  const bytes = fs.statSync(file).size;
  const gz = zlib.gzipSync(fs.readFileSync(file)).length;
  console.log(`  ${data.classes.length} 班 / 共 ${totalLessons} 条课程 / 平行班组(地点留空) ${mergedCount} 条（其中教师也为空 ${noTeacherNoRoom} 条，PBL 类课程原始表教师列即空，属已知形态）/ ${(bytes / 1024).toFixed(0)} KB，压入 APK 约 ${(gz / 1024).toFixed(0)} KB`);
}

// ---------- 结论 ----------
if (errors.length) {
  console.error(`\n[FAIL] 校验未通过，共 ${errors.length} 项：`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`\n[OK] kcap 资产校验通过：${index.semesters.length} 个学期，共 ${totalLessons} 条课程记录`);
