import sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da/"
S = W + "app/src/main/java/com/java/myapplication/"
F = S + "ui/pages/ThreadDetailPage.kt"

anchors = [
    "private fun ThreadHeader",
    "private fun MainPost",
    "private fun VoteCard",
    "private sealed class HtmlBlock",
    "private data class HtmlSpan",
    "private fun parseHtmlBlocks",
    "private fun intrinsicSizeOfUrl",
    "private fun appendTextBlocks",
    "private fun parseTextSegment",
    "private fun BubbleTail",
    "private fun collectSticker",
    "private class RectRef",
    "private fun CommentImage",
    "private fun HtmlContent",
    "private class VideoHost",
    "private fun VideoPlayer",
    "private fun FullscreenVideo",
    "private fun ThreadReplyBox",
    "private fun ReplyRow",
    "private fun EmojiTabChip",
    "private fun StickerPane",
    "private fun PanelToKeyboardWatcher",
    "private fun FloorSheet",
    "private fun SubReplyRow",
]

lines = open(F, encoding="utf-8").read().split("\n")


def is_decoration(s):
    t = s.strip()
    return t.startswith("@") or t.startswith("/*") or t.startswith("*") or t.startswith("//")


def find_anchor(a):
    hits = [i for i, l in enumerate(lines) if a in l]
    assert len(hits) == 1, (a, hits)
    return hits[0]


idx = [(find_anchor(a), a) for a in anchors]
idx.sort()


def block_start(ai):
    s = ai
    while s - 1 >= 0 and is_decoration(lines[s - 1]):
        s -= 1
    return s


starts = [block_start(ai) for ai, _ in idx]
blocks = []
for k, (ai, a) in enumerate(idx):
    s = starts[k]
    e = (starts[k + 1] - 1) if k + 1 < len(idx) else len(lines) - 1
    while e >= s and lines[e].strip() == "":
        e -= 1
    name = a.split("fun ")[-1].split("class ")[-1].split("(")[0].strip()
    blocks.append((name, s, e))

print("=== blocks (dry) ===")
for name, s, e in blocks:
    print(f"{name:22s} lines {s+1}-{e+1}  ({e-s+1})")
imp_idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
header = "\n".join(lines[0:max(imp_idx) + 1])
print("keep (main) 1..", starts[0])

if len(sys.argv) > 1 and sys.argv[1] == "apply":
    groups = {
        "ThreadDetailHeader.kt": ["ThreadHeader", "MainPost"],
        "ThreadDetailVote.kt": ["VoteCard"],
        "ThreadDetailHtml.kt": ["HtmlBlock", "HtmlSpan", "parseHtmlBlocks", "intrinsicSizeOfUrl",
                                "appendTextBlocks", "parseTextSegment", "BubbleTail", "collectSticker",
                                "RectRef", "CommentImage", "HtmlContent"],
        "ThreadDetailVideo.kt": ["VideoHost", "VideoPlayer", "FullscreenVideo"],
        "ThreadDetailReply.kt": ["ThreadReplyBox", "ReplyRow", "EmojiTabChip", "StickerPane",
                                 "PanelToKeyboardWatcher"],
        "ThreadDetailFloor.kt": ["FloorSheet", "SubReplyRow"],
    }
    by_name = {n: (s, e) for n, s, e in blocks}

    def to_internal(text):
        out = []
        for l in text.split("\n"):
            if l.startswith("private sealed class "):
                l = "internal sealed class " + l[len("private sealed class "):]
            elif l.startswith("private data class "):
                l = "internal data class " + l[len("private data class "):]
            elif l.startswith("private class "):
                l = "internal class " + l[len("private class "):]
            elif l.startswith("private fun "):
                l = "internal fun " + l[len("private fun "):]
            out.append(l)
        return "\n".join(out)

    for fname, names in groups.items():
        body = [to_internal("\n".join(lines[by_name[n][0]:by_name[n][1] + 1])) for n in names]
        open(S + "ui/pages/" + fname, "w", encoding="utf-8").write(header + "\n\n" + "\n\n".join(body) + "\n")
        print("wrote", fname)

    keep = lines[0:starts[0]]
    while keep and keep[-1].strip() == "":
        keep.pop()
    open(F, "w", encoding="utf-8").write("\n".join(keep) + "\n")
    print("ThreadDetailPage.kt ->", len(keep), "lines")