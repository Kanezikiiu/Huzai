import sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da/"
S = W + "app/src/main/java/com/java/myapplication/"
F = S + "ui/pages/PlayerDetailPage.kt"

anchors = [
    "private fun PlayerHeader",
    "private fun IntroCard",
    "private fun MyScoreCard",
    "private fun DistributionCard",
    "private fun HottestCard",
    "private fun CommentRow",
    "data class SubCommentSheetData",
    "fun SubCommentSheet(",
    "private fun SubCommentRow",
    "private fun ScorePanelOverlay",
    "private fun canSendScoreReply",
    "private fun ScoreEmojiTabChip",
    "private fun ScoreStickerPane",
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
first_block_start = starts[0]
print("keep (main) 1..", first_block_start)

if len(sys.argv) > 1 and sys.argv[1] == "apply":
    groups = {
        "PlayerDetailCards.kt": ["PlayerHeader", "IntroCard", "MyScoreCard", "DistributionCard", "HottestCard"],
        "PlayerDetailComment.kt": ["CommentRow", "SubCommentRow"],
        "PlayerDetailSubCommentSheet.kt": ["SubCommentSheetData", "SubCommentSheet"],
        "PlayerDetailScore.kt": ["ScorePanelOverlay", "canSendScoreReply", "ScoreEmojiTabChip", "ScoreStickerPane"],
    }
    by_name = {n: (s, e) for n, s, e in blocks}

    def to_internal(text):
        out = []
        for l in text.split("\n"):
            if l.startswith("private fun "):
                l = "internal fun " + l[len("private fun "):]
            elif l.startswith("private suspend fun "):
                l = "internal suspend fun " + l[len("private suspend fun "):]
            elif l.startswith("private data class "):
                l = "internal data class " + l[len("private data class "):]
            out.append(l)
        return "\n".join(out)

    for fname, names in groups.items():
        body = [to_internal("\n".join(lines[by_name[n][0]:by_name[n][1] + 1])) for n in names]
        content = header + "\n\n" + "\n\n".join(body) + "\n"
        open(S + "ui/pages/" + fname, "w", encoding="utf-8").write(content)
        print("wrote", fname, len(content.split("\n")), "lines")

    keep = lines[0:first_block_start]
    while keep and keep[-1].strip() == "":
        keep.pop()
    open(F, "w", encoding="utf-8").write("\n".join(keep) + "\n")
    print("PlayerDetailPage.kt ->", len(keep), "lines")