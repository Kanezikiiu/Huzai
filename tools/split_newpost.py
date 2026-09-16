import sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da/"
S = W + "app/src/main/java/com/java/myapplication/"
F = S + "ui/pages/NewPostPage.kt"

# 锚点：顶层声明起始行的唯一子串
anchors = [
    "private val DECLARE_OPTIONS",
    "private fun declareLabel",
    "private fun DeclarePickerSheet",
    "private fun TagPickerSheet",
    "private fun PostTopicSheet",
    "private fun ZoneRow",
    "private fun queryDisplayName",
    "private data class UploadedImage",
    "private suspend fun uploadImage",
    "private fun AttachmentThumb",
    "private fun VoteBlockCard",
    "private fun ModeChip",
    "private fun StepBtn",
    "private fun VoteCreateSheet",
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
# 块终点 = 下一个块起点-1（按锚点顺序）
blocks = []
for k, (ai, a) in enumerate(idx):
    s = starts[k]
    e = (starts[k + 1] - 1) if k + 1 < len(idx) else len(lines) - 1
    # 去掉尾部空行
    while e >= s and lines[e].strip() == "":
        e -= 1
    name = a.split("fun ")[-1].split(" val ")[-1].split("class ")[-1].split("(")[0].strip()
    blocks.append((name, s, e))

print("=== blocks (dry) ===")
for name, s, e in blocks:
    print(f"{name:22s} lines {s+1}-{e+1}  ({e-s+1})")

# import 头部
imp_idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
header_end = max(imp_idx)
header = "\n".join(lines[0:header_end + 1])
first_block_start = starts[0]

if len(sys.argv) > 1 and sys.argv[1] == "apply":
    groups = {
        "NewPostTopicPicker.kt": ["DECLARE_OPTIONS", "declareLabel", "DeclarePickerSheet",
                                   "TagPickerSheet", "PostTopicSheet", "ZoneRow"],
        "NewPostCommon.kt": ["queryDisplayName", "UploadedImage", "uploadImage", "AttachmentThumb"],
        "NewPostVote.kt": ["VoteBlockCard", "ModeChip", "StepBtn", "VoteCreateSheet"],
    }
    by_name = {n: (s, e) for n, s, e in blocks}

    def to_internal(text):
        out = []
        for l in text.split("\n"):
            if l.startswith("private fun "):
                l = "internal fun " + l[len("private fun "):]
            elif l.startswith("private suspend fun "):
                l = "internal suspend fun " + l[len("private suspend fun "):]
            elif l.startswith("private val "):
                l = "internal val " + l[len("private val "):]
            elif l.startswith("private data class "):
                l = "internal data class " + l[len("private data class "):]
            out.append(l)
        return "\n".join(out)

    for fname, names in groups.items():
        body = []
        for n in names:
            s, e = by_name[n]
            body.append(to_internal("\n".join(lines[s:e + 1])))
        content = header + "\n\n" + "\n\n".join(body) + "\n"
        open(S + "ui/pages/" + fname, "w", encoding="utf-8").write(content)
        print("wrote", fname, len(content.split("\n")), "lines")

    # 重写 NewPostPage.kt：只保留 0..first_block_start-1，去掉尾部空行
    keep = lines[0:first_block_start]
    while keep and keep[-1].strip() == "":
        keep.pop()
    open(F, "w", encoding="utf-8").write("\n".join(keep) + "\n")
    print("NewPostPage.kt ->", len(keep), "lines")
