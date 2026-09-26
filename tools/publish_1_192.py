#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 发布：创建 GitHub Release v1.192 并上传 APK 资产（可重入）。

前置：
  · 环境变量 GH_TOKEN（GitHub PAT，repo 权限）
  · 远端 main 已包含 release: v1.192 提交，且 tag v1.192 已推送

行为（与 1.191 同构）：
  · POST /repos/{repo}/releases          → 201 新建；422 表示已存在 → 复用
  · 同名资产先 DELETE 再上传（保证可重入，重复执行不会报冲突）
  · 资产必须经 uploads.github.com 上传（api.github.com 不接受二进制）

用法：GH_TOKEN=... python3 tools/publish_1_192.py
"""
import io, json, os, sys
import urllib.request, urllib.error

TOKEN = os.environ["GH_TOKEN"]
REPO = "Kanezikiiu/Huzai"
TAG = "v1.192"
ASSET = "huzai-1.192-release.apk"
BODY_FILE = "tools/release_body_1_192.md"
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
        with urllib.request.urlopen(req, timeout=600) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def main():
    body = io.open(BODY_FILE, encoding="utf-8").read()
    print("body 字符数 = %d" % len(body))

    payload = json.dumps({
        "tag_name": TAG, "name": TAG, "body": body,
        "draft": False, "prerelease": False,
    }).encode("utf-8")
    st, res = call("https://api.github.com/repos/%s/releases" % REPO,
                   data=payload, method="POST")
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

    st, assets = call(rel["assets_url"])
    if isinstance(assets, list):
        for a in assets:
            if a["name"] == ASSET:
                call(a["url"], method="DELETE")
                print("deleted old asset id=%s" % a["id"])

    blob = io.open(APK, "rb").read()
    print("apk bytes = %d" % len(blob))
    url = "https://uploads.github.com/repos/%s/releases/%s/assets?name=%s" % (REPO, rel["id"], ASSET)
    st, up = call(url, data=blob, method="POST", ctype="application/vnd.android.package-archive")
    assert st == 201, (st, up)
    print("asset id=%s name=%s size=%s state=%s" % (up["id"], up["name"], up["size"], up["state"]))
    print("browser_download_url=%s" % up["browser_download_url"])
    print("release html_url=%s" % rel["html_url"])
    print("PUBLISH 1.192 OK")


main()