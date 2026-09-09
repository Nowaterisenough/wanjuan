#!/usr/bin/env python3
"""Build a content-free, per-source Markdown/CSV summary of the live audit."""
import argparse
from collections import Counter
import csv
from datetime import datetime
import json
from pathlib import Path
import re


def nodes(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from nodes(child)
    elif isinstance(value, list):
        for child in value:
            yield from nodes(child)


def summarize(report):
    all_nodes = list(nodes(report))
    chapters = [item for item in all_nodes if "chapterIndex" in item]
    passed = [item for item in chapters if item.get("status") == "readable"]
    images = [item for item in all_nodes if item.get("decoded")]
    errors = [item for item in all_nodes if item.get("error")]
    ranks = {"categories": 0, "list": 1, "info": 2, "chapters": 3, "content": 4, "image": 5}
    errors.sort(key=lambda item: ranks.get(item.get("stage", ""), -1), reverse=True)
    primary = str(errors[0]["error"]).splitlines()[0] if errors else ""
    # Keep error messages useful while excluding headers, URL query values and long rule excerpts.
    primary = re.sub(r"https?://[^\s)\]]+", "[URL]", primary)[:220]
    reason = primary
    attempted_errors = [item for item in nodes(report.get("attempts", [])) if item.get("error")]
    for followup in report.get("followUpAudits", []):
        attempted_errors += [item for item in nodes(followup.get("attempts", [])) if item.get("error")]
    if report.get("status") == "readable" and not attempted_errors:
        result = "样本通过"
        reason = f"{len(passed)} 个章节样本通过"
        if images:
            reason += f"，{len(images)} 张图片已解码"
    elif passed:
        result = "部分通过"
        reason = f"{len(passed)}/{len(chapters)} 个章节样本通过；{primary}"
    elif any(item.get("status") == "media_link_only" and not item.get("error") for item in chapters):
        result = "仅取得媒体链接"
        media_count = sum(item.get("status") == "media_link_only" and not item.get("error") for item in chapters)
        reason = f"{media_count}/{len(chapters)} 个章节样本取得媒体链接，未验证实际播放/下载"
        if primary:
            reason += f"；其他样本或入口：{primary}"
    elif any(item.get("status") == "download_link_only" for item in all_nodes):
        result = "仅取得下载链接"
        reason = "未验证文件下载与打开"
    elif report.get("hostTimedOut") or "TimeoutCancellationException" in report.get("error", ""):
        result = "超时未确认"
        reason = f"测试在 {report.get('activeStage', 'unknown')} 阶段达到时间上限"
    elif re.search(r"验证码|验证未完成|需要验证|登录|拒绝了当前网络|访问受限|403|Cloudflare", primary, re.I):
        result = "访问受限或需验证"
    elif re.search(r"UnknownHost|resolve host|No address associated|DNS", primary, re.I):
        result = "域名解析失败"
    elif re.search(r"SSL|Handshake|CertPath|certificate|trust anchor", primary, re.I):
        result = "TLS连接失败"
    elif re.search(r"EcmaError|EvaluatorException|ScriptException|ReferenceError|TypeError|SyntaxError|IllegalArgumentException|JsonSyntaxException|SelectorParseException", primary):
        result = "规则执行异常"
    elif re.search(r"Content is too short|access/error placeholder|raw HTML page|Empty content|ContentEmptyException|No media URL|icon or thumbnail|icons or thumbnails", primary):
        result = "正文无效或提示内容"
    elif re.search(r"Image is not decodable|Image pixel decoding|Image decryption|image URLs", primary):
        result = "图片加载失败"
    elif re.search(r"Empty chapter list|TocEmptyException", primary):
        result = "目录为空"
    elif "Empty book list" in primary:
        result = "列表为空"
    elif re.search(r"No search or usable|No direct discovery", primary):
        result = "缺少可用入口"
    elif re.search(r"timeout|timed out|Timed out|Interrupted|Canceled|cancelled", primary, re.I) or report.get("hostTimedOut"):
        result = "超时未确认"
        reason = primary or f"测试在 {report.get('activeStage', 'unknown')} 阶段达到时间上限"
    elif report.get("status") in ("harness_error", "process_failed"):
        result = "测试进程异常"
    elif re.search(r"ConnectException|failed to connect|connection|HTTP|status code|404|502|503|504|unexpected end of stream", primary, re.I):
        result = "站点或网络异常"
    else:
        result = "未验证通过"
        reason = primary or str(report.get("status", "unknown"))
    explanations = {
        "java.lang.IllegalStateException: Empty book list": "分类或搜索返回空列表，未能进入正文",
        "java.lang.IllegalStateException: Empty chapter list": "目录没有可读取章节",
        "java.lang.IllegalStateException: Empty content": "正文为空",
        "io.wanjuan.app.exception.ContentEmptyException: 内容为空": "正文解析结果为空",
        "io.wanjuan.app.exception.TocEmptyException: 目录列表为空": "目录解析结果为空",
        "java.lang.IllegalStateException: No search or usable discovery rule": "缺少搜索规则和可直接使用的分类入口",
        "java.lang.IllegalStateException: Images are only icons or thumbnails": "只取得小图或横条，未确认到漫画正文图片",
    }
    reason = explanations.get(reason, reason)
    if match := re.fullmatch(r"java.lang.IllegalStateException: Content is too short to verify \((\d+) characters\)", reason):
        reason = f"正文解析结果仅 {match[1]} 字，无法确认是完整正文"
    stage = max((item.get("stage", "") for item in all_nodes), key=lambda stage: ranks.get(stage, -1), default="")
    stage = {"categories": "分类", "list": "书籍列表", "info": "详情", "chapters": "目录",
             "content": "正文", "image": "图片"}.get(stage, stage)
    types = {0: "文本", 1: "音频", 2: "图片", 3: "文件", 4: "视频"}
    return {"序号": report["index"] + 1, "书源": report["source"],
            "声明类型": types.get(report.get("declaredType"), "未知"), "结果": result,
            "最远检查阶段": stage, "已读章节样本": len(passed), "尝试章节样本": len(chapters),
            "已解码图片": len(images), "耗时秒": report.get("hostElapsedSeconds", ""), "说明": reason}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    reports = [json.loads(path.read_text()) for path in sorted(args.directory.glob("[0-9][0-9][0-9].json"))]
    repair_dir = args.directory / "repair-checks"
    proxy_dir = args.directory / "proxy-retest"
    rows = []
    proxy_rows = []
    repair_checks = 0
    for report in reports:
        initial = summarize(report)
        repair_file = repair_dir / f"{report['index']:03d}.json"
        direct_report = json.loads(repair_file.read_text()) if repair_file.exists() else report
        if repair_file.exists():
            repair_checks += direct_report.get("includedChecks", 1)
        direct = summarize(direct_report)
        row = dict(direct)
        proxy_file = proxy_dir / f"{report['index']:03d}.json"
        proxy = summarize(json.loads(proxy_file.read_text())) if proxy_file.exists() else None
        network = "直连修复后" if repair_file.exists() else "直连"
        if proxy:
            proxy_rows.append(proxy)
            # Retain a working direct sample when this network's proxy cannot reach it.
            rank = {"样本通过": 3, "部分通过": 2, "仅取得媒体链接": 1, "仅取得下载链接": 1}
            def evidence(row):
                return rank.get(row["结果"], 0), row["已读章节样本"], row["已解码图片"]
            if evidence(proxy) >= evidence(direct):
                row = dict(proxy)
                network = "代理"
        row.update({"原报告结果": initial["结果"], "直连结果": direct["结果"],
                    "代理结果": proxy["结果"] if proxy else "未复测",
                    "结论依据": network})
        if proxy:
            row["说明"] = f"{network}：{row['说明']}；另一路径：" + (
                f"直连{direct['结果']}" if network == "代理" else f"代理{proxy['结果']}：{proxy['说明']}")
        rows.append(row)
    if not rows:
        raise SystemExit("No per-source reports found")
    counts = Counter(row["结果"] for row in rows)
    metadata = json.loads((args.directory / "metadata.json").read_text())
    root = Path(__file__).resolve().parents[1]
    definitions = {}
    for relative in metadata["inputs"]:
        for source in json.loads((root / relative).read_text()):
            definitions[source["bookSourceName"]] = source
    for row in rows:
        content_rule = definitions.get(row["书源"], {}).get("ruleContent") or {}
        if row["结果"] == "超时未确认" and "startBrowserAwait" in str(content_rule):
            row["说明"] += "；正文规则包含等待外部浏览器的操作，播放未验证"
    with (args.directory / "书源逐项测试.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    (args.directory / "summary.json").write_text(json.dumps({"tested": len(rows), "total": metadata["sourceCount"],
        "counts": counts, "proxyRetested": len(proxy_rows),
        "proxyCounts": Counter(row["结果"] for row in proxy_rows),
        "repairChecks": repair_checks, "sources": rows}, indent=2, ensure_ascii=False))
    started = min(report.get("startedAt", 0) for report in reports)
    checked_at = datetime.fromtimestamp(started / 1000).isoformat(sep=" ", timespec="seconds")
    build_path = args.directory / "tested-build.json"
    tested_build = json.loads(build_path.read_text()) if build_path.exists() else {}
    lines = ["# 书源逐项实测报告", "",
        f"已完成 {len(rows)} / {metadata['sourceCount']} 个书源。测试设备：{metadata['serial']}。", "",
        f"原报告包含 {metadata.get('sourceChecks', len(rows))} 轮逐源检查；本次另纳入 {len(proxy_rows)} 轮代理复测及 {repair_checks} 轮修复验证。", "",
        f"测试开始时间：{checked_at}（本机时区）。测试版本：{tested_build.get('versionName', '见构建记录')} / {tested_build.get('versionCode', '')}。", "",
        "## 测试口径", "",
        "- 范围：仓库 tests/shareBookSource.json 的样本及应用内置书源，按书源 URL 去重；总数以本次 metadata.json 为准。",
        "- 使用安装在模拟器上的签名 Release 测试构建，直接调用应用的分类/搜索、详情、目录、正文和图片解密/下载流程。",
        "- 测试构建 debuggable=false；为运行 instrumentation 关闭混淆，任务完成后恢复安装正常混淆的签名 Release。",
        "- 首轮每源最多尝试两个直接分类入口与搜索；每个入口最多两本书；每本抽查前两章，图片抽查首页与中间页。取得完整通过样本后停止该源的后续入口尝试。需要交互配置的分类不自动点击。",
        "- 小黄书另测图集分类；空列表书源追加替代关键词搜索。含正文通过样本但其他入口或样本失败的书源记为部分通过。复测原始记录在 followups 目录。",
        "- 文本检查非空、长度及常见错误占位内容；图片检查实际像素解码。链接、空目录、空列表和超时不计为正文通过。",
        "- 音视频/下载链接单独记录，未验证播放或下载。需要账号、订阅或人工验证的书源按当前可用配置测试。",
        "- 样本通过仅代表本次抽查内容可读取，不代表全站、全部分类、所有章节或后续网络环境都可用。",
        "- 网络错误保留为本次观测，不直接断言书源永久失效。测试记录不包含章节正文或图片。", "",
    ]
    selection_file = proxy_dir / "selection.json"
    if selection_file.exists():
        selection = json.loads(selection_file.read_text())
        proxy_build_file = proxy_dir / "tested-build.json"
        proxy_build = json.loads(proxy_build_file.read_text()) if proxy_build_file.exists() else {}
        excluded = "、".join(item["source"] for item in selection["excludedAggregates"])
        lines += ["## 代理复测与修复更新", "",
            f"- 复测范围：原报告未完全通过的 {len(selection['selected'])} 个非聚合源，包括部分通过和仅取得媒体链接的源。已完成 {len(proxy_rows)} 个。",
            f"- 按要求排除聚合源：{excluded}。保留其原始结果。",
            f"- 请求代理：`{selection['requestedProxy']}`；当前主机连接超时，无法使用。",
            f"- 实际代理：`{selection['upstreamProxy']}`，使用项目 AGENTS.md 中约定的备用地址；已验证 HTTPS CONNECT 和目标响应均为 200。",
            "- 模拟器通过 adb reverse 和临时 TCP 转发接入代理。每源记录 HTTP 客户端代理路由、连接次数及收发字节，复测结束恢复原网络设置。",
            f"- 代理测试版本：{proxy_build.get('versionName', '见构建记录')} / {proxy_build.get('versionCode', '')}，签名 Release、debuggable=false。",
            "- 小黄书合并两组图集修复后的实际解码结果；91Porna、好色TV 更新为视频类型并直接返回播放地址。媒体链接仍不计为播放通过。",
            "- 当前结论采用已取得的较完整验证：代理成功不会抹去直连失败，代理失败也不会否定仍有证据的直连正文样本；两条路径分别列出。",
            "- 前后测试版本不同，结果改善可能同时来自代码/规则修复和网络变化。",
            "- repair-checks 保存修复验证；proxy-retest 保存代理逐源记录；原始 000.json 至 097.json 和 before-proxy-report.md 保留历史。", "",
            "### 代理复测结果", "", "| 结果 | 数量 |", "| --- | ---: |"]
        lines += [f"| {key} | {count} |" for key, count in Counter(row["结果"] for row in proxy_rows).most_common()]
        lines.append("")
    lines += ["## 当前结果统计", "", "| 结果 | 数量 |", "| --- | ---: |"]
    lines += [f"| {key} | {count} |" for key, count in counts.most_common()]
    lines += ["", "## 逐源结果", "", "| 序号 | 书源 | 声明类型 | 直连/修复后 | 代理复测 | 当前结论 | 章节通过/尝试 | 图片 | 说明 |",
              "| ---: | --- | --- | --- | --- | --- | ---: | ---: | --- |"]
    for row in rows:
        values = [row["序号"], row["书源"], row["声明类型"], row["直连结果"], row["代理结果"], row["结果"],
                  f"{row['已读章节样本']}/{row['尝试章节样本']}", row["已解码图片"], row["说明"]]
        lines.append("| " + " | ".join(str(value).replace("|", "\\|").replace("\n", " ") for value in values) + " |")
    lines += ["", "原始分阶段记录位于同目录 000.json 至 097.json；CSV 可用于筛选与后续复测。", ""]
    (args.directory / "书源逐项测试报告.md").write_text("\n".join(lines))
    print(json.dumps({"tested": len(rows), "counts": counts}, ensure_ascii=False))


if __name__ == "__main__":
    main()
