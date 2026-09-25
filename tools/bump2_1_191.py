#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 发版（续）：README + version.json（build.gradle.kts 已由 bump_1_191.py 改好）。"""
import io, json

def patch(path, old, new, expect):
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    n = s.count(old)
    assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:48], n)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s.replace(old, new))
    print("OK %s: %d hit(s)" % (path, n))

# README：v1.190 -> v1.191（正文 5 处）
patch("README.md", "1.190", "1.191", 5)

CHANGELOG = (
    "· 中秋节快乐，吃月饼了吗\n"
    "· 表情包搜索：表情面板新增「搜索表情」入口，输入关键词即可在线搜索表情包\n"
    "· 专区页新增「收藏专区」：具体专区右上角可收藏/取消，收藏后专区首页顶部出现「收藏专区」并默认选中\n"
    "· 全站弹窗升级为毛玻璃风格：实时背景模糊 + Q 弹入场动画\n"
    "· 修复全屏看视频时会自动熄屏的问题\n"
    "· 详情页底部「转发」改为「分享」，并重绘评论区跳转图标，与其它图标对齐\n"
    "· 输入框去掉黑色描边、改用柔和底色，搜索/信息流设置等文本框更协调"
)
d = {
    "versionCode": 201,
    "versionName": "1.191",
    "apkUrl": "https://github.com/Kanezikiiu/Huzai/releases/download/v1.191/huzai-1.191-release.apk",
    "releaseUrl": "https://github.com/Kanezikiiu/Huzai/releases/tag/v1.191",
    "changelog": CHANGELOG,
    "forceUpdate": False,
}
with io.open("version.json", "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write("\n")

back = json.load(io.open("version.json", encoding="utf-8"))
assert back["versionCode"] == 201 and back["versionName"] == "1.191"
assert len(back["changelog"].split("\n")) == 7
assert "\\n" not in back["changelog"]
print("version.json OK")
print("BUMP2 OK")