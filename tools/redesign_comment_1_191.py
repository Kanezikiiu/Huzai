#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 第四批：
   1) 帖子详情底部操作条「转发」->「分享」（含相关注释同步）；
   2) 重绘评论区跳转图标：气泡体下移到画布中心，消除左下角尾巴把整体「顶高」导致的不对齐。
   带命中数断言，防止静默失效。"""
import io

def patch(path, old, new, expect):
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    n = s.count(old)
    assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:48], n)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s.replace(old, new))
    print("OK %s: %d hit(s)" % (path, n))

B = "app/src/main/java/com/java/myapplication/"

# ---- 1. 文案：转发 -> 分享（这些文件里 "转发" 只出现在该按钮相关处）----
patch(B + "ui/pages/ThreadActionBar.kt", "转发", "分享", 5)
patch(B + "ui/pages/ThreadDetailPage.kt", "转发", "分享", 1)
patch(B + "ui/components/HupuIcons.kt", "转发箭头", "分享箭头", 1)

# ---- 2. 评论图标重绘 ----
# 2-a 路径替换（弧线参数用空格分隔，避免 flag 粘连解析歧义）
patch(B + "ui/components/HupuIcons.kt",
      '"M20,2L4,2c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2z"',
      '"M6,4H18A4,4 0 0 1 22,8V16A4,4 0 0 1 18,20H6L2,24V8A4,4 0 0 1 6,4Z"', 1)

# 2-b 文档注释同步
patch(B + "ui/components/HupuIcons.kt",
      "    /** 评论气泡（Material chat_bubble 24px） */",
      "    /**\n"
      "     * 1.191 评论气泡（自绘，取代 Material chat_bubble）。\n"
      "     * 原 chat_bubble 的气泡体在 24 画布中只占 y2..18（重心 y10），左下角挂着一条 y18..22 的小尾巴，\n"
      "     * 内容盒被撑到 y2..22；在定高图标槽里居中后，气泡视觉重心比其它图标高约 1.6dp，\n"
      "     * 真机上表现为「评论图标被顶高、和点亮/收藏/分享不齐」。\n"
      "     * 新路径把气泡体下移到 y4..20（重心正好落在画布中心 y12），尾巴并入左下角、不再额外撑高，\n"
      "     * 因此与其余三个图标天然对齐（内容盒仍 20x20，尺寸档位 iconSize 不变）。\n"
      "     */", 1)

print("REDESIGN OK")