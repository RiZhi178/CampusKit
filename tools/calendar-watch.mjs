#!/usr/bin/env node
/**
 * 学年校历自动更新链路的检测与组装端（解析端在 tools/calendar-extract.py）。
 *
 * 校历来源：教务处校历栏目 jwc.qdu.edu.cn/xl.htm，每学年一篇通知，正文是一个
 * PDF 附件（CMS 会同时渲染逐页 JPG，但 PDF 是文字型矢量表格，pdfplumber 直读）。
 * 解析产物经自洽校验（README《校历数据说明》第七节的口径）后组装成两份东西：
 *   - app/src/main/assets/calendar/<学年>.json   （HOLIDAY 假期日；MAKEUP 配对必须
 *     人工照教务处教学安排通知补 followsDate，校历只写「某日正常上课」不写补周几的课）
 *   - tools/term-starts.json                     （每学期第一周周一，供 kcap-watch
 *     自动清洗课表用，替代人工填 --term-start）
 *
 * 用法：
 *   node tools/calendar-watch.mjs check
 *     输出 JSON：{ found, academicYear, articleUrl, pdfUrl }。学期比对基准是
 *     tools/term-starts.json 里已有的学年。
 *   node tools/calendar-watch.mjs download --article <文章页URL> --out <pdf路径>
 *   node tools/calendar-watch.mjs assemble --extract <extract.py输出的JSON> [--terms-start <tools/term-starts.json>]
 *     把 python 解析结果组装进资产与映射文件；makeupCandidates 原样透传给 PR 描述。
 */
import fs from 'node:fs';
import path from 'node:path';
import https from 'node:https';

const JWC = 'https://jwc.qdu.edu.cn';
const CAL_LIST = '/xl.htm';

function arg(name) {
  const at = process.argv.indexOf(`--${name}`);
  return at >= 0 ? process.argv[at + 1] : null;
}

/**
 * 不走全局 fetch：CI runner 上 undici 访问 jwc.qdu.edu.cn 一律 ETIMEDOUT，
 * 而同一台 runner 上 curl、node:https + family:4、直连 211.64.164.2 全是 200
 * （DNS 的 A 记录本来就排在最前，ipv4first 也救不了）。所以这里直接用 node:https，
 * 强制 IPv4，手动跟随最多 5 次跳转。
 */
function httpGet(url, { headers = {}, timeout = 30_000, binary = false } = {}) {
  return new Promise((resolve, reject) => {
    const go = (target, hops) => {
      if (hops > 5) return reject(new Error(`跳转超过 5 次 @ ${url}`));
      const req = https.get(target, { family: 4, headers, timeout }, (res) => {
        const status = res.statusCode ?? 0;
        if (status >= 300 && status < 400 && res.headers.location) {
          res.resume();
          return go(new URL(res.headers.location, target).href, hops + 1);
        }
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const buf = Buffer.concat(chunks);
          resolve({ status, headers: res.headers, body: binary ? buf : buf.toString('utf8') });
        });
      });
      req.on('timeout', () => req.destroy(new Error(`请求超时 ${timeout}ms @ ${target}`)));
      req.on('error', reject);
    };
    go(url, 0);
  });
}

async function fetchText(url, extra = {}) {
  const res = await httpGet(url, {
    headers: { 'User-Agent': 'Mozilla/5.0', Referer: JWC + CAL_LIST, ...extra },
  });
  if (res.status < 200 || res.status >= 300) throw new Error(`HTTP ${res.status} @ ${url}`);
  return res.body;
}

function readTermStarts(file) {
  if (!fs.existsSync(file)) return {};
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

async function check() {
  const termsFile = arg('term-starts') ?? 'tools/term-starts.json';
  const known = readTermStarts(termsFile);
  const listHtml = await fetchText(JWC + CAL_LIST);
  const articles = [];
  for (const m of listHtml.matchAll(/<a[^>]+href="([^"]*info\/\d+\/\d+\.htm)"[^>]*>([\s\S]{0,160}?)<\/a>/g)) {
    const url = new URL(m[1], JWC + CAL_LIST).href;
    const title = m[2].replace(/<[^>]+>/g, '').replace(/\s+/g, '').trim();
    const sm = /青岛大学(\d{4})[~～~](\d{4})学年校历/.exec(title);
    if (sm) articles.push({ academicYear: `${sm[1]}-${sm[2]}`, title, url });
  }
  // 栏目页最新在前；只看最新一篇——历史上没入库的旧学年不算「新学年」
  const latest = articles[0];
  if (!latest || known[`${latest.academicYear}-1`]) {
    console.log(JSON.stringify({ found: false }));
    return;
  }
  // 从文章页提取 PDF 地址（正文 showVsbpdfIframe("...pdf") 或附件链接）
  const article = await fetchText(latest.url);
  const pdf =
    /showVsbpdfIframe\("([^"]+\.pdf)"/.exec(article)?.[1] ??
    /href="([^"]*download\.jsp[^"]*)"/.exec(article)?.[1] ??
    /href="([^"]*__local[^"]*\.pdf)"/.exec(article)?.[1];
  if (!pdf) throw new Error(`校历文章 ${latest.url} 里没有 PDF（正文或附件都没有），人工看一眼`);
  console.log(JSON.stringify({
    found: true,
    academicYear: latest.academicYear,
    articleUrl: latest.url,
    pdfUrl: new URL(pdf, latest.url).href,
  }));
}

async function download() {
  const article = arg('article');
  const out = arg('out');
  if (!article || !out) {
    console.error('用法：calendar-watch download --article <文章页URL> --out <pdf路径>');
    process.exit(1);
  }
  const res = await httpGet(article, {
    headers: { 'User-Agent': 'Mozilla/5.0', Referer: JWC + CAL_LIST },
  });
  const html = res.body;
  const pdf =
    /showVsbpdfIframe\("([^"]+\.pdf)"/.exec(html)?.[1] ??
    /href="([^"]*download\.jsp[^"]*)"/.exec(html)?.[1] ??
    /href="([^"]*__local[^"]*\.pdf)"/.exec(html)?.[1];
  if (!pdf) throw new Error(`校历文章 ${article} 里没有 PDF`);
  const pdfRes = await httpGet(new URL(pdf, article).href, {
    headers: { 'User-Agent': 'Mozilla/5.0', Referer: article },
    timeout: 120_000,
    binary: true,
  });
  const buf = pdfRes.body;
  if (buf.length < 100 * 1024) throw new Error(`PDF 只有 ${buf.length} 字节，不像校历`);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, buf);
  console.log(JSON.stringify({ ok: true, bytes: buf.length, savedTo: out }));
}

function assemble() {
  const extractFile = arg('extract');
  const calDir = arg('out') ?? 'app/src/main/assets/calendar';
  const termsFile = arg('term-starts') ?? 'tools/term-starts.json';
  if (!extractFile) {
    console.error('用法：calendar-watch assemble --extract <python输出JSON> [--out <资产目录>]');
    process.exit(1);
  }
  const draft = JSON.parse(fs.readFileSync(extractFile, 'utf8'));
  if (!draft.verification || draft.verification.passed !== draft.verification.total || draft.verification.total === 0) {
    throw new Error('解析产物未通过星期括注交叉校验，禁止上线');
  }
  const asset = {
    academicYear: draft.academicYear,
    // 只有 HOLIDAY 上自动链路；MAKEUP 的 followsDate 必须人工照教务处配对通知补
    days: draft.holidays.map((h) => ({ date: h.date, kind: 'HOLIDAY', name: h.name })),
  };
  fs.mkdirSync(calDir, { recursive: true });
  const assetFile = path.join(calDir, `${draft.academicYear}.json`);
  fs.writeFileSync(assetFile, JSON.stringify(asset));

  const termStarts = readTermStarts(termsFile);
  for (const t of draft.terms) termStarts[t.semesterId] = t.firstMonday;
  fs.mkdirSync(path.dirname(termsFile), { recursive: true });
  fs.writeFileSync(termsFile, JSON.stringify(termStarts, null, 2) + '\n');

  console.log(JSON.stringify({
    ok: true,
    assetFile,
    termStarts: draft.terms.map((t) => `${t.semesterId}=${t.firstMonday}`).join(', '),
    makeupPending: draft.makeupCandidates.map((m) => m.date),
  }));
}

const cmd = process.argv[2];
try {
  if (cmd === 'check') await check();
  else if (cmd === 'download') await download();
  else if (cmd === 'assemble') assemble();
  else {
    console.error('用法：node tools/calendar-watch.mjs <check|download|assemble> [...参数]');
    process.exit(1);
  }
} catch (e) {
  // undici 的 fetch 失败只给一句 "fetch failed"，真正原因在 cause 里（ENOTFOUND / 连接超时 / 证书）
  const c = e?.cause;
  const detail = c ? [c.code, c.message, c.hostname].filter(Boolean).join(' ') : '';
  console.error(`[calendar-watch] ${e.message}${detail ? `（cause: ${detail}）` : ''}`);
  process.exit(2);
}
