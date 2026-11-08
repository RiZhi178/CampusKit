#!/usr/bin/env node
/**
 * 版本位自动 bump：新学期资产合并进 main 后，把版本走第二位（功能/资产批）。
 *
 * 版本号规则见 README-状态.md：versionCode = 第一位×10⁴ + 第二位×10³ + 第三位，
 * 三个位都定义在 app/build.gradle.kts 的 defaultConfig 里（appMajor/appMinor/appPatch）。
 * 本脚本把第二位 +1、第三位归零，改写后打印新版本，供 workflow 打 tag 用。
 *
 * 用法：node tools/release-bump.mjs
 * 输出：v1.1.0（stdout 最后一行，workflow 用它建 tag）
 */
import fs from 'node:fs';

const FILE = 'app/build.gradle.kts';
const src = fs.readFileSync(FILE, 'utf8');

const read = (name) => {
  const m = new RegExp(`val ${name} = (\\d+)`).exec(src);
  if (!m) throw new Error(`${FILE} 里找不到 ${name}，版本位定义挪过了？先人工看一眼`);
  return Number(m[1]);
};
const major = read('appMajor');
const minor = read('appMinor');
if (minor >= 9) {
  // 第二位上限 9（README 版本号规则）；顶到上限时进位第一位并归零第二位
  const next = `val appMajor = ${major + 1}\n        val appMinor = 0`;
  const re = /val appMajor = \d+\n        val appMinor = \d+/;
  if (!re.test(src)) throw new Error('appMajor/appMinor 不在相邻两行，进位改不动，人工处理');
  fs.writeFileSync(FILE, src.replace(re, next).replace(/val appPatch = \d+/, 'val appPatch = 0'));
  console.log(`v${major + 1}.0.0`);
} else {
  fs.writeFileSync(
    FILE,
    src
      .replace(/val appMinor = \d+/, `val appMinor = ${minor + 1}`)
      .replace(/val appPatch = \d+/, 'val appPatch = 0'),
  );
  console.log(`v${major}.${minor + 1}.0`);
}
