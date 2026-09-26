#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 发版：升版号 + 写 version.json + 更新 README 版本引用

改动落点（全部带命中数断言，任一不符则整批不写盘）：
  · app/build.gradle.kts : versionCode 201 → 202 / versionName 1.191 → 1.192（各 1 处）
  · version.json         : 整文件重写（code / name / apkUrl / releaseUrl / changelog）
  · README.md            : 「1.191」共 5 处 → 1.192（v1.191 ×3 + huzai-1.191-release.apk ×2）

用法：
    python3 tools/bump_1_192.py            # 预演（只打印，不写盘）
    python3 tools/bump_1_192.py --write    # 真正落盘
"""
import io, json, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
GRADLE = W + "/app/build.gradle.kts"
VJSON = W + "/version.json"
README = W + "/README.md"

NEW_CODE = 202
NEW_NAME = "1.192"

# changelog 条目（顺序即展示顺序）
# 注意：旧版本的更新弹窗约 8 行就截断，所以这里**刻意压到 6 条、每条一行**，
# 保证旧客户端也能看全。长文说明放在 GitHub Release 的 body 里。
ENTRIES = [
    "顶部频道条外观优化",
    "部分按钮外观优化",
    "频道支持隐藏固定项，并修正「重置」行为",
    "阅读字号条、打分面板升级材质",
    "搜索历史改多行，专区面板更规整",
    "修复底部 Tab 栏消失、登录卡顿等问题",
]

FAILS = []


def check(cond, msg):
    if cond:
        print("  ok   " + msg)
    else:
        FAILS.append(msg)
        print("  FAIL " + msg)


def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def main():
    write = "--write" in sys.argv

    print("== 1. app/build.gradle.kts ==")
    g = read(GRADLE)
    check(g.count("versionCode = 201") == 1, "versionCode = 201 命中 1 处")
    check(g.count('versionName = "1.191"') == 1, "versionName = \"1.191\" 命中 1 处")

    print("== 2. README.md ==")
    r = read(README)
    check(r.count("1.191") == 5, "「1.191」命中 5 处（实际 %d）" % r.count("1.191"))

    print("== 3. version.json 新内容 ==")
    changelog = "\\n".join("· " + e for e in ENTRIES)
    vj = (
        "{\n"
        '  "versionCode": %d,\n' % NEW_CODE +
        '  "versionName": "%s",\n' % NEW_NAME +
        '  "apkUrl": "https://github.com/Kanezikiiu/Huzai/releases/download/v%s/huzai-%s-release.apk",\n' % (NEW_NAME, NEW_NAME) +
        '  "releaseUrl": "https://github.com/Kanezikiiu/Huzai/releases/tag/v%s",\n' % NEW_NAME +
        '  "changelog": "%s",\n' % changelog +
        '  "forceUpdate": false\n'
        "}\n"
    )
    try:
        parsed = json.loads(vj)
        check(parsed["versionCode"] == NEW_CODE, "version.json 可解析且 versionCode = %d" % NEW_CODE)
        check(parsed["versionName"] == NEW_NAME, "versionName = %s" % NEW_NAME)
        check(isinstance(parsed["changelog"], str), "changelog 是字符串（旧客户端可解析）")
        check(parsed["changelog"].count("\n· ") == len(ENTRIES) - 1,
              "changelog 条目数 = %d（实际 %d）" % (len(ENTRIES), parsed["changelog"].count("\n· ") + 1))
        check(parsed["apkUrl"].endswith("huzai-%s-release.apk" % NEW_NAME), "apkUrl 资产名与版本一致")
    except Exception as e:
        check(False, "version.json 解析失败：%s" % e)

    if FAILS:
        print("\n!!! 断言失败 %d 项，未写盘：" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    print("\n---- changelog 预览 ----")
    for e in ENTRIES:
        print("· " + e)

    if not write:
        print("\n(预演模式：未写盘。加 --write 执行)")
        return

    g = g.replace("versionCode = 201", "versionCode = %d" % NEW_CODE, 1)
    g = g.replace('versionName = "1.191"', 'versionName = "%s"' % NEW_NAME, 1)
    with io.open(GRADLE, "w", encoding="utf-8") as f:
        f.write(g)
    with io.open(VJSON, "w", encoding="utf-8") as f:
        f.write(vj)
    with io.open(README, "w", encoding="utf-8") as f:
        f.write(r.replace("1.191", NEW_NAME))
    print("\n>>> 已写盘：build.gradle.kts / version.json / README.md")
    print("BUMP 1.192 OK")


main()