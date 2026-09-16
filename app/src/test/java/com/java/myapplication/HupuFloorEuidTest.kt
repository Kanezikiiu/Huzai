package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.50: bbs-reply-detail 的 user 无 euid，解析层应以 puid 回填 euid 供主页跳转 */
class HupuFloorEuidTest {
    @Test
    fun `parseFloorReplies fills euid with puid`() {
        val json = """
            {"data":{"title":"t","post":null,"forum":{},"current":1,"nextPage":0,
              "replies":[
                {"pid":"114894001","content":"<p>hi</p>","lights":3,"lz":true,"location":"广东","replies":"2",
                 "user":{"username":"测试甲","puid":"71454151","header":"https://i1.hoopchina.com.cn/a.png","createDt":"11-24"}},
                {"pid":"114894002","content":"<p>yo</p>","lights":1,"lz":false,"location":"","replies":"0",
                 "user":{"puname":"测试乙","puid":"123","header":"","createDt":""}}
              ]},"status":"200","msg":"success"}
        """.trimIndent()
        val parent = HupuReply(pid = "114894", floor = 12, totalReplies = 19)
        val fr = HupuParser.parseFloorReplies(json, parent)
        assertNotNull(fr)
        fr!!
        assertEquals(2, fr.subReplies.size)
        assertEquals(19, fr.parent.totalReplies)
        fr.subReplies[0].author!!.let { a ->
            assertEquals("测试甲", a.name)
            assertEquals("71454151", a.puid)
            assertEquals("71454151", a.euid)
        }
        fr.subReplies[1].author!!.let { a ->
            assertEquals("测试乙", a.name)
            assertEquals("123", a.euid)
        }
        assertTrue(!fr.hasMore)
    }
}
