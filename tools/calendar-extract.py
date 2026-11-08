#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""青岛大学学年校历 PDF → 结构化草稿（App 资产 days + term-starts）。

输入：教务处校历栏目（jwc.qdu.edu.cn/xl.htm）发布的学年校历 PDF——单页，
一张上下两半的学年周历表（秋季段 + 春季段，各 20 周列 × 星期行），底部是说明文字。
解析器用 pdfplumber 直读矢量表格，不依赖 MinerU（人工链路的重型工具）。

产物（JSON，stdout）：
{
  "academicYear": "2026-2027",
  "terms": [ { "semesterId": "2026-2027-1", "firstMonday": "2026-09-07", ... } ],
  "holidays": [ { "date": ..., "name": ... } ],     # 假期逐日（含区间内的周六日）
  "makeupCandidates": [ { "date": ..., "name": ... } ],  # 调休上课日，followsDate 必须人工照教务处配对通知补
  "verification": { ... }                            # 全部自洽性校验结果
}

校验口径照《校历数据说明》第七节：按第 1 周周一推算每天日期，与表格逐格比对；
说明文字里括注星期的日期反查推算结果。任何一格对不上 verification 就报 failure，
调用方（CI/人工）不得使用未过校验的产物。

运行：python calendar-extract.py <校历.pdf> [--year 2026]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import date, timedelta

import pdfplumber

WEEKDAY_ROWS = {"星期一": 0, "星期二": 1, "星期三": 2, "星期四": 3, "星期五": 4, "星期六": 5, "星期日": 6}
# 标题里的学年波浪号有 ~ ～ ~ 三种写法，统一归一
WAVE = "[~～~]"


def cell_text(c) -> str:
    return (c or "").replace("\n", "").replace(" ", "").strip()


def norm_year(text: str) -> int | None:
    m = re.search(r"(\d{4})年", text)
    return int(m.group(1)) if m else None


def find_term_sections(table) -> list[dict]:
    """按「星期一」行切分段落；每段向上找学期带行（含 X年秋季/春季学期）、月份表头行、周次行。"""
    sections = []
    for i, row in enumerate(table):
        if cell_text(row[0]) != "星期一":
            continue
        week_row = month_row = term_row = None
        for j in range(i - 1, max(i - 12, -1), -1):
            cells = [cell_text(c) for c in table[j]]
            joined = "".join(cells)
            if week_row is None:
                nums = [c for c in cells[1:] if c.isdigit()]
                if len(nums) >= 18 and nums[:3] == ["1", "2", "3"]:
                    week_row = j
                    continue
            if month_row is None and any(re.search(r"\d{4}年\d+月", c) for c in cells if c):
                month_row = j
                continue
            if term_row is None and re.search(r"\d{4}年(秋季|春季)学期", joined):
                term_row = j
                continue
        if week_row is None or month_row is None or term_row is None:
            raise ValueError(f"第 {i} 行的星期一找不到配套的周次/月份/学期行，表样变了")
        term_text = "".join(cell_text(c) for c in table[term_row])
        season = "秋季" if "秋季学期" in term_text else ("春季" if "春季学期" in term_text else None)
        if season is None:
            raise ValueError(f"学期带行认不出季节：{term_text[:60]}")
        year = norm_year(term_text)
        if year is None:
            raise ValueError(f"学期带行没有年份：{term_text[:60]}")
        sections.append({
            "season": season, "year": year,
            "week_row": week_row, "month_row": month_row,
            "mon_row": i, "days_rows": list(range(i, min(i + 7, len(table)))),
        })
    if not sections:
        raise ValueError("表格里找不到任何「星期一」行，表样变了")
    return sections


def month_of_column(table, month_row: int, col: int, section_year: int) -> tuple[int, int]:
    """列所属的（年, 月）：月份表头行的「2026年9月」标记向后填充到后续列；
    「2月」这类缺年份的标记继承上一个标记的年份（跨年时上一个已是下一年）。"""
    row = table[month_row]
    mapping: dict[int, tuple[int, int]] = {}
    current: tuple[int, int] | None = None
    for c in range(1, len(row)):
        text = cell_text(row[c])
        m = re.search(r"(?:(\d{4})年)?(\d{1,2})月", text)
        if m:
            y = int(m.group(1)) if m.group(1) else (current[0] if current else section_year)
            current = (y, int(m.group(2)))
        if current is not None:
            mapping[c] = current
    if col not in mapping:
        raise ValueError(f"列 {col} 落在月份表头之外（表样变了？）")
    return mapping[col]


def build_days(table, section: dict) -> tuple[list, str]:
    """周次列 → 每天（date, weekday），并求解第 1 周周一。

    月份表头与周列在原表里错位一格（合并单元格的版面残留），不可靠；
    改为：取首周列的星期一日号，枚举（年, 月）组合，唯一取「全部周列 × 全部
    星期行日号自洽」的解——140 格约束下不存在第二解，且任何一格不符即排除。
    """
    week_cells = [cell_text(c) for c in table[section["week_row"]]]
    week_cols = [i for i, c in enumerate(week_cells[1:], start=1) if c.isdigit()]
    grid: dict[int, tuple[int, list]] = {}
    for col in week_cols:
        week_no = int(week_cells[col])
        entries: list = []
        for text, weekday in WEEKDAY_ROWS.items():
            row = table[section["mon_row"] + weekday]
            if cell_text(row[0]) != text:
                raise ValueError(f"星期行错位：期待 {text}，实为 {cell_text(row[0])}")
            day = cell_text(row[col])
            if day.isdigit():
                entries.append((weekday, int(day)))
        grid[col] = (week_no, entries)
    if not week_cols:
        raise ValueError("周次行里找不到 1..20 的周次列")

    first_col = week_cols[0]
    first_week_no = grid[first_col][0]
    first_day_number = next(d for w, d in grid[first_col][1] if w == 0)

    def try_month(year: int, month: int) -> list | None:
        try:
            base = date(year, month, first_day_number)
        except ValueError:
            return None
        if base.weekday() != 0:
            return None
        days: list = []
        for col in week_cols:
            week_no, entries = grid[col]
            col_base = base + timedelta(days=7 * (week_no - first_week_no))
            for weekday, day_number in entries:
                d = col_base + timedelta(days=weekday)
                if d.day != day_number:
                    return None
                days.append({"date": d.isoformat(), "week": week_no, "weekday": weekday})
        return days

    for year in (section["year"], section["year"] - 1, section["year"] + 1):
        for month in range(1, 13):
            days = try_month(year, month)
            if days is not None:
                first_monday = next(d["date"] for d in days if d["week"] == first_week_no and d["weekday"] == 0)
                return days, first_monday
    raise ValueError("枚举所有（年, 月）都无法让全表日号自洽，说明解析或表样有问题")


def extract_notes(table) -> str:
    """说明文字：底部几行（「说明：」「一、」「二、」开头的行）。"""
    parts = []
    for row in table:
        c = cell_text(row[0])
        if re.match(r"^(说明：|[一二三四五六七八九十]、|教务处编制)", c):
            parts.append(c)
    return "\n".join(parts)


def parse_holidays_and_makeup(notes: str, academic_year: str) -> tuple[list, list]:
    """从说明文字解析假期区间与调休上课日。

    已知句式（2026~2027 实测）：
      「中秋节：9月25日至9月27日放假」「国庆节：10月1日至10月7日放假调休」
      「寒假：放假时间为2027年1月25日～3月5日」「暑假：放假时间为2027年7月26日～9月3日」
      「9月20日、10月10日正常上课」
    假期名取句首的节日/假期名；跨年区间（寒假 1 月~3 月）沿用句中带年份的部分，缺省用学年推断。
    """
    holidays: list = []
    makeup: list = []
    autumn_year = int(academic_year.split("-")[0])

    date_pat = r"(?:(\d{4})年)?(\d{1,2})月(\d{1,2})日"

    def resolve_date(groups: tuple, default_year: int) -> date | None:
        y, mo, d = groups
        return date(int(y) if y else default_year, int(mo), int(d))

    # 假期区间：「放假时间为YYYY年X月X日～(YYYY年)X月X日」结尾是「放假」或「，共N周」；
    # 寒假/暑假这类学期级长假不入 App 资产（假期里无排课，标了没意义，跨学年还会落空），只用于核对
    for m in re.finditer(
        rf"(?P<name>[^，。；：\n]{{1,6}}?)[:：]?\s*放假时间为?{date_pat}\s*[～～\-至]{date_pat}(?:放假|，\s*共\d+周)",
        notes,
    ):
        start = resolve_date(m.group(2, 3, 4), autumn_year)
        end = resolve_date(m.group(5, 6, 7), autumn_year)
        if start is None or end is None or end < start:
            continue
        # 区间跨年（寒假 1 月→3 月）：结束段没有年份时可能被推到错误年份，取跨度最合理的一组
        if (end - start).days > 80:
            end = date(end.year - 1, end.month, end.day)
            if (end - start).days > 80 or end < start:
                end = date(end.year + 2, end.month, end.day)
        name = m.group("name").strip("：: ") or "假期"
        d = start
        while d <= end:
            holidays.append({"date": d.isoformat(), "name": name})
            d += timedelta(days=1)

    # 节假日单段：「中秋节：9月25日至9月27日放假」「国庆节：10月1日至10月7日放假调休」
    for m in re.finditer(
        rf"(?P<name>中秋节|国庆节|元旦|清明节|劳动节|端午节)[:：]?\s*{date_pat}\s*[至～～\-]\s*{date_pat}放假",
        notes,
    ):
        start = resolve_date(m.group(2, 3, 4), autumn_year)
        end = resolve_date(m.group(5, 6, 7), autumn_year)
        if start is None or end is None:
            continue
        if end < start:
            end = date(end.year + (1 if end < date(autumn_year, 1, 1) else 0), end.month, end.day)
        name = m.group("name")
        d = start
        while d <= end:
            holidays.append({"date": d.isoformat(), "name": name})
            d += timedelta(days=1)

    # 调休上课：「9月20日、10月10日正常上课」
    for m in re.finditer(rf"((?:{date_pat}[、，,]?)+)正常上课", notes):
        for dm in re.finditer(date_pat, m.group(1)):
            d = resolve_date(dm.groups(), autumn_year)
            if d:
                makeup.append({"date": d.isoformat(), "name": "调休正常上课"})
    return holidays, makeup


def verify(notes: str, all_days: list) -> dict:
    """自洽性校验：说明文字里括注星期的日期（X月X日（星期Y））反查推算结果。"""
    checks, passed = [], 0
    lookup = {d["date"]: d["weekday"] for d in all_days}
    for m in re.finditer(r"(\d{1,2})月(\d{1,2})日（星期([一二三四五六日])）", notes):
        mo, dd = int(m.group(1)), int(m.group(2))
        label = m.group(3)
        expect = WEEKDAY_ROWS["星期" + label]
        # 年份：在秋季学期日期范围里的用秋季年，其余 +1（说明覆盖两个学期）
        for year in sorted({d["date"][:4] for d in all_days}):
            iso = f"{year}-{mo:02d}-{dd:02d}"
            if iso in lookup:
                ok = lookup[iso] == expect
                checks.append({"date": iso, "expect": expect, "ok": ok})
                passed += ok
                break
    return {"weekday_cross_checks": checks, "passed": passed, "total": len(checks)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("--year", type=int, default=None, help="学年起始年（如 2026），缺省从 PDF 标题取")
    args = ap.parse_args()

    with pdfplumber.open(args.pdf) as pdf:
        page = pdf.pages[0]
        tables = page.extract_tables()
        if not tables:
            raise ValueError("PDF 里没有表格（扫描版？）——退回人工 MinerU 链路")
        table = max(tables, key=len)
        title_year = norm_year(cell_text(table[0][0])) if table else None
        notes = extract_notes(table)
        text = page.extract_text() or ""

    year = args.year or title_year
    if year is None:
        m = re.search(r"(\d{4})[~～~]\d{4}学年", text)
        year = int(m.group(1)) if m else None
    if year is None:
        raise ValueError("认不出学年，用 --year 显式指定")

    sections = find_term_sections(table)
    terms, all_days = [], []
    for sec in sections:
        days, first_monday = build_days(table, sec)
        semester_id = f"{year}-{year + 1}-{'1' if sec['season'] == '秋季' else '2'}"
        terms.append({
            "semesterId": semester_id,
            "season": sec["season"],
            "firstMonday": first_monday,
            "firstDate": min(d["date"] for d in days),
            "lastDate": max(d["date"] for d in days),
        })
        all_days.extend(days)
        if first_monday is None:
            raise ValueError(f"{sec['season']}学期推不出第 1 周周一")

    academic_year = f"{year}-{year + 1}"
    holidays, makeup = parse_holidays_and_makeup(notes, academic_year)
    # 假期逐日去重（多段说明可能重复）；寒暑假是学期级长假，不入 App 资产（口径见 docs/interfaces/calendar.md）
    seen = set()
    holidays = [h for h in holidays if not (h["date"] in seen or seen.add(h["date"]))]
    holidays = [h for h in holidays if h["name"] not in ("寒假", "暑假")]
    makeup = [m for m in makeup if m["date"] not in {h["date"] for h in holidays}]
    v = verify(notes, all_days)

    result = {
        "academicYear": academic_year,
        "terms": terms,
        "holidays": sorted(holidays, key=lambda x: x["date"]),
        "makeupCandidates": sorted(makeup, key=lambda x: x["date"]),
        "verification": v,
        "dayCount": len(all_days),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if v["total"] == 0 or v["passed"] != v["total"]:
        print("星期括注交叉核对失败或缺样本，产物不可直接上线", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
