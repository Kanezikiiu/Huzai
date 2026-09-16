package com.java.myapplication

import com.java.myapplication.data.HupuMatchApi
import com.java.myapplication.data.HupuScoreComment
import com.java.myapplication.data.mergeWithOptimistic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.151 修复的两条链路：
 *  ① 响应可用性判定不再用字节长度猜（合法的「成功但空」响应很短）；
 *  ② 服务端 ⊕ 乐观条目合并：按真实 commentId 去重 + 图片以信息更全一方为准。
 */
class HupuOptimisticMergeTest {

    // ---------- ① isJsonBody ----------

    /** 真机根因样本：母评论无子回复时 subCommentList 的「成功但空」响应（约 64 字节）。 */
    private val emptySuccess =
        """{"code":1,"type":"COMMON","msg":"成功","data":null,"success":true}"""

    @Test
    fun `成功但空的短响应必须被接受`() {
        assertTrue(emptySuccess.length < 100)
        assertTrue(HupuMatchApi.isJsonBody(emptySuccess))
    }

    @Test
    fun `正常大响应被接受`() {
        assertTrue(HupuMatchApi.isJsonBody("""{"code":1,"data":{"comments":[]}}"""))
    }

    @Test
    fun `空体_HTML_非 JSON 仍被拒`() {
        assertFalse(HupuMatchApi.isJsonBody(null))
        assertFalse(HupuMatchApi.isJsonBody(""))
        assertFalse(HupuMatchApi.isJsonBody("   "))
        assertFalse(HupuMatchApi.isJsonBody("<html><body>502 Bad Gateway</body></html>"))
        assertFalse(HupuMatchApi.isJsonBody("""{"code":1,"data":{"comments":["""))
    }

    // ---------- ② mergeWithOptimistic ----------

    private fun c(
        id: String,
        content: String = "内容",
        images: List<String> = emptyList(),
    ) = HupuScoreComment(
        commentId = id, userName = "u$id", userHead = null,
        content = content, score = 0, lightCount = 0L, date = "刚刚", ipLocation = "",
        images = images,
    )

    @Test
    fun `同 id 不重复且图片取更全一方`() {
        val server = listOf(c("1", images = emptyList()), c("2", images = listOf("a")))
        val local = listOf(c("1", images = listOf("x", "y")))
        val merged = mergeWithOptimistic(server, local)
        assertEquals(2, merged.size)
        assertEquals(listOf("x", "y"), merged.first { it.commentId == "1" }.images)
        assertEquals(listOf("a"), merged.first { it.commentId == "2" }.images)
    }

    @Test
    fun `服务端图片更全时不覆盖`() {
        val merged = mergeWithOptimistic(
            listOf(c("1", images = listOf("a", "b"))),
            listOf(c("1", images = listOf("x"))),
        )
        assertEquals(listOf("a", "b"), merged.single().images)
    }

    @Test
    fun `服务端还没有的乐观条目追加在末尾`() {
        val merged = mergeWithOptimistic(
            listOf(c("1"), c("2")),
            listOf(c("3")),
        )
        assertEquals(listOf("1", "2", "3"), merged.map { it.commentId })
    }

    @Test
    fun `无乐观条目时原样返回`() {
        val server = listOf(c("1"), c("2"))
        assertEquals(server, mergeWithOptimistic(server, emptyList()))
    }
}
