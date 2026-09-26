#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 发布后匿名复核（不带任何凭证，证明公开可见性）：

  1) Contents API 取远端 version.json → 与本地逐字节比对（并打印 blob sha）
  2) Releases API 取 tag=v1.192 → 打印 release id / 资产名 / size / digest / state
  3) 匿名下载 Release 资产 → MD5 与本地构建产物比对
  4) Git refs API 取 v1.192 → 打印指向的 commit

注意：故意**不用** raw.githubusercontent.com——它吃 CDN 缓存，上次 1.191 发布时
就在那里读到过旧内容，误判成发布失败。
"""
import base64, hashlib, io, json, urllib.request

REPO = "Kanezikiiu/Huzai"
TAG = "v1.192"
ASSET = "huzai-1.192-release.apk"
LOCAL_VJSON = "version.json"
LOCAL_APK = "app/build/outputs/apk/release/app-release.apk"

FAILS = []


def get(url, raw=False):
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "huzai-verify")
    with urllib.request.urlopen(req, timeout=600) as r:
        data = r.read()
    return data if raw else json.loads(data.decode("utf-8"))


def md5(b):
    return hashlib.md5(b).hexdigest()


def main():
    print("== 1) Contents API: version.json ==")
    c = get("https://api.github.com/repos/%s/contents/%s?ref=main" % (REPO, LOCAL_VJSON))
    remote = base64.b64decode(c["content"])
    local = io.open(LOCAL_VJSON, "rb").read()
    print("  远端 blob sha = %s" % c["sha"])
    print("  远端 size    = %d / 本地 size = %d" % (len(remote), len(local)))
    if remote == local:
        print("  ok   远端 version.json 与本地**逐字节一致**")
    else:
        FAILS.append("远端 version.json 与本地不一致")
        print("  FAIL 内容不一致")
    rv = json.loads(remote.decode("utf-8"))
    print("  远端版本 = %s (%d)" % (rv["versionName"], rv["versionCode"]))
    if rv["versionCode"] != 202 or rv["versionName"] != "1.192":
        FAILS.append("远端版本号不是 202/1.192")
        print("  FAIL 版本号不符")
    else:
        print("  ok   版本号 202 / 1.192")
    print("  远端 changelog:")
    for line in rv["changelog"].split("\n"):
        print("    " + line)

    print("== 2) Releases API: %s ==" % TAG)
    rel = get("https://api.github.com/repos/%s/releases/tags/%s" % (REPO, TAG))
    print("  release id=%s name=%s draft=%s prerelease=%s" % (
        rel["id"], rel["name"], rel["draft"], rel["prerelease"]))
    print("  html_url=%s" % rel["html_url"])
    hit = None
    for a in rel["assets"]:
        print("  asset id=%s name=%s size=%s state=%s digest=%s" % (
            a["id"], a["name"], a["size"], a["state"], a.get("digest")))
        if a["name"] == ASSET:
            hit = a
    if hit is None:
        FAILS.append("release 里没有 %s" % ASSET)
        print("  FAIL 资产缺失")
    else:
        print("  ok   资产存在")

    print("== 3) 匿名下载资产并比对 MD5 ==")
    data = get(hit["browser_download_url"], raw=True)
    lmd5 = md5(io.open(LOCAL_APK, "rb").read())
    rmd5 = md5(data)
    print("  本地 MD5 = %s" % lmd5)
    print("  远端 MD5 = %s（%d 字节）" % (rmd5, len(data)))
    if lmd5 == rmd5:
        print("  ok   线上 APK 与本地构建产物**逐字节一致**")
    else:
        FAILS.append("线上 APK MD5 与本地不一致")
        print("  FAIL MD5 不一致")

    print("== 4) Git refs API: tag %s ==" % TAG)
    ref = get("https://api.github.com/repos/%s/git/ref/tags/%s" % (REPO, TAG))
    print("  ref=%s type=%s sha=%s" % (ref["ref"], ref["object"]["type"], ref["object"]["sha"]))

    print()
    if FAILS:
        print("!!! 复核失败 %d 项：" % len(FAILS))
        for f in FAILS:
            print("   " + f)
    else:
        print("VERIFY 1.192 OK —— 全部通过")


main()