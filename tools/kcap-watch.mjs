#!/usr/bin/env node
/**
 * 教务处「课程总表（本科生）」新学期检测与下载。
 *
 * 每学期的更新链路是：教务处在 kcap.htm 发新课表文章 → 本脚本发现并下载 xlsx →
 * kcap-clean.mjs 清洗 → kcap-validate.mjs 门禁 → 随包发布。本脚本负责前两步，
 * 供 CI 定时任务（.github/workflows/kcap-watch.yml）与人工命令行共用。
 *
 * 用法：
 *   node tools/kcap-watch.mjs check
 *     扫描列表页，与 assets/kcap/index.json 已登记学期比对。
 *     发现新学期：stdout 打印 JSON（semesterId/label/articleUrl/downloadUrl）退出码 0；
 *     没有新学期：打印 {"found":false} 退出码 0；网络失败：stderr 说明，退出码 2。
 *
 *   node tools/kcap-watch.mjs download --article <文章页URL> --out <xlsx保存路径>
 *     下载附件。必须带同源 Referer 指回文章页（不带会拿到 3805 字节的校验页，
 *     两种都回 200，见 docs/interfaces/jwc-kcap.md 第一节），下载后按体积与
 *     content-type 复核，防止把 HTML 当 Excel 存下来。
 *
 * 表样随时可能改版：解析不到结构时脚本明确报错退出，绝不猜。
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import dns from 'node:dns';

// 校方站点对外先给 AAAA，而 CI runner 只有 IPv4 出口：不强制 IPv4 会一路 ETIMEDOUT
// （runner 上实测：curl 默认与 -4 都是 200，-6 立刻失败，node fetch 则卡到超时）。
dns.setDefaultResultOrder('ipv4first');

const JWC = 'https://jwc.qdu.edu.cn';
const LIST_PATH = '/xxgk/kcap.htm';
// 2026 秋季实测附件 1.19 MB；低于 512 KB 的一定是校验页而不是 Excel
const MIN_XLSX_BYTES = 512 * 1024;

function arg(name) {
  const at = process.argv.indexOf(`--${name}`);
  return at >= 0 ? process.argv[at + 1] : null;
}

async function fetchText(url, extraHeaders = {}) {
  const res = await fetch(url, {
    headers: { 'User-Agent': 'Mozilla/5.0', Referer: JWC + LIST_PATH, ...extraHeaders },
    redirect: 'follow',
    signal: AbortSignal.timeout(30_000),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} @ ${url}`);
  return res.text();
}

async function downloadXlsx(articleUrl, outPath) {
  const article = await fetchText(articleUrl, { Referer: articleUrl });
  const dl = /href="([^"]*download\.jsp[^"]*)"/.exec(article)?.[1];
  if (!dl) throw new Error(`文章页 ${articleUrl} 里没有 download.jsp 附件链接，表样可能改版，人工看一眼`);
  const dlUrl = new URL(dl, articleUrl).href;
  const res = await fetch(dlUrl, {
    headers: { 'User-Agent': 'Mozilla/5.0', Referer: articleUrl },
    redirect: 'follow',
    signal: AbortSignal.timeout(120_000),
  });
  const type = res.headers.get('content-type') ?? '';
  if (!res.ok) throw new Error(`下载失败 HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length < MIN_XLSX_BYTES) {
    throw new Error(`附件只有 ${buf.length} 字节（低于 ${MIN_XLSX_BYTES} 下限），拿到的是校验页而非 Excel，别把它当数据用`);
  }
  if (/text\/html/i.test(type)) throw new Error(`content-type=${type}，是 HTML 校验页而非 Excel`);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, buf);
  return { dlUrl, bytes: buf.length, contentType: type };
}

/**
 * 「青岛大学2026年秋季学期课程总表（本科生）」→ 2026-2027-1。
 * 学年归属：秋季属于当年学年的上学期；春季/夏季属于上一学年（2027 春 = 2026-2027-2）。
 */
function semesterFromTitle(title) {
  const m = /青岛大学(\d{4})年(秋季|春季|夏季)学期课程总表/.exec(title);
  if (!m) return null;
  const year = Number(m[1]);
  const term = { 秋季: 1, 春季: 2, 夏季: 3 }[m[2]];
  const academicStart = m[2] === '秋季' ? year : year - 1;
  return { id: `${academicStart}-${academicStart + 1}-${term}`, label: `${academicStart}-${academicStart + 1}学年 ${m[2]}` };
}

async function check() {
  const indexPath = path.resolve('app/src/main/assets/kcap/index.json');
  const known = new Set();
  if (fs.existsSync(indexPath)) {
    for (const s of JSON.parse(fs.readFileSync(indexPath, 'utf8')).semesters ?? []) known.add(s.id);
  }

  const listHtml = await fetchText(JWC + LIST_PATH);
  // 列表页锚点：href 指向 /info/1095/<id>.htm，锚文本含「课程总表」。只看本科那篇。
  const articles = [];
  for (const m of listHtml.matchAll(/<a[^>]+href="([^"]*\/info\/\d+\/\d+\.htm)"[^>]*>([^<]*课程总表[^<]*)<\/a>/g)) {
    const url = new URL(m[1], JWC).href;
    const title = m[2].replace(/<[^>]+>/g, '').trim();
    const isUndergraduate = /本科生/.test(title) || !/研究生/.test(title);
    const semester = semesterFromTitle(title);
    if (semester && isUndergraduate) articles.push({ url, title, ...semester });
  }
  // 列表页最新在前；「新学期」只看最上面那篇——列表里更老的、历史上没清洗过的
  // 学期不算新（比如 2025 春季没入过库，但它不是「新」学期，不该触发更新链路）。
  const latest = articles[0];
  if (!latest || known.has(latest.id)) {
    console.log(JSON.stringify({ found: false, known: [...known] }));
    return;
  }
  console.log(JSON.stringify({ found: true, ...latest }));
}

const cmd = process.argv[2];
try {
  if (cmd === 'check') {
    await check();
  } else if (cmd === 'download') {
    const article = arg('article');
    const out = arg('out');
    if (!article || !out) {
      console.error('用法：node tools/kcap-watch.mjs download --article <文章页URL> --out <xlsx路径>');
      process.exit(1);
    }
    const result = await downloadXlsx(article, out);
    console.log(JSON.stringify({ ok: true, ...result, savedTo: out }));
  } else {
    console.error('用法：node tools/kcap-watch.mjs <check|download> [...参数]');
    process.exit(1);
  }
} catch (e) {
  // undici 的 fetch 失败只给一句 "fetch failed"，真正原因在 cause 里（ENOTFOUND / 连接超时 / 证书）
  const c = e?.cause;
  const detail = c ? [c.code, c.message, c.hostname].filter(Boolean).join(' ') : '';
  console.error(`[kcap-watch] ${e.message}${detail ? `（cause: ${detail}）` : ''}`);
  process.exit(2);
}
