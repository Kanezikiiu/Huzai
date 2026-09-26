#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 发布：创建/复用 GitHub Release，并上传 APK 资产（可重入）。需要环境变量 GH_TOKEN。"""
import io, json, os, sys
import urllib.request, urllib.error

TOKEN = os.environ["GH_TOKEN"]
REPO = "Kanezikiiu/Huzai"
TAG = "v1.191"
ASSET = "huzai-1.191-release.apk"
APK = "app/build/outputs/apk/release/app-release.apk"


def call(url, data=None, method="GET", ctype="application/json"):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "huzai-release")
    if data is not None:
        req.add_header("Content-Type", ctype)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


# 1) 建 release（已存在则复用）
body = io.open("tools/release_notes_1_191.md", encoding="utf-8").read()
payload = json.dumps({
    "tag_name": TAG, "name": TAG, "body": body,
    "draft": False, "prerelease": False,
}).encode("utf-8")
st, res = call("https://api.github.com/repos/%s/releases" % REPO, data=payload, method="POST")
if st == 201:
    rel = res
    print("release created id=%s" % rel["id"])
elif st == 422:
    st2, rel = call("https://api.github.com/repos/%s/releases/tags/%s" % (REPO, TAG))
    assert st2 == 200, (st2, rel)
    print("release exists id=%s" % rel["id"])
else:
    print("release FAIL", st, res)
    sys.exit(1)

# 2) 上传资产（同名先删，保证可重入）
st, assets = call(rel["assets_url"])
if isinstance(assets, list):
    for a in assets:
        if a["name"] == ASSET:
            call(a["url"], method="DELETE")
            print("deleted old asset id=%s" % a["id"])

blob = io.open(APK, "rb").read()
url = "https://uploads.github.com/repos/%s/releases/%s/assets?name=%s" % (REPO, rel["id"], ASSET)
st, up = call(url, data=blob, method="POST", ctype="application/vnd.android.package-archive")
assert st == 201, (st, up)
print("asset id=%s name=%s size=%s state=%s" % (up["id"], up["name"], up["size"], up["state"]))
print("browser_download_url=%s" % up["browser_download_url"])
print("release html_url=%s" % rel["html_url"])
print("PUBLISH OK")