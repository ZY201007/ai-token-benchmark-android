package com.example.aitokenbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批量队列的纯 JVM 单元测试：容量、派生入队、满队列拒绝、FIFO 消费。 */
class BatchQueueTest {

    @Test
    fun `默认容量为20`() {
        assertEquals(20, BatchQueue.MAX_GROUPS)
        val queue = BatchQueue()
        for (i in 1..20) {
            assertTrue("第 $i 组应入队成功", queue.enqueue("内容$i"))
        }
        assertEquals(20, queue.size)
    }

    @Test
    fun `满队列拒绝新任务`() {
        val queue = BatchQueue(capacity = 3)
        assertTrue(queue.enqueue("A"))
        assertTrue(queue.enqueue("B"))
        assertTrue(queue.enqueue("C"))
        assertEquals(3, queue.size)
        assertFalse("超过容量应拒绝入队", queue.enqueue("D"))
        assertEquals(3, queue.size)
    }

    @Test
    fun `消费后可再次入队`() {
        val queue = BatchQueue(capacity = 1)
        assertTrue(queue.enqueue("A"))
        assertFalse(queue.enqueue("B"))
        assertEquals("A", queue.poll()?.content)
        assertTrue(queue.isEmpty)
        assertTrue(queue.enqueue("B"))
    }

    @Test
    fun `按组数派生任务并附加组序号`() {
        val queue = BatchQueue()
        val added = queue.enqueueDerived("工作内容", 5)
        assertEquals(5, added)
        assertEquals(5, queue.size)
        val all = queue.all()
        assertEquals(1, all[0].index)
        assertEquals(5, all[4].index)
        assertTrue(all[0].content.contains("工作内容"))
        assertTrue(all[0].content.contains("第 1 组 / 共 5 组"))
        assertTrue(all[4].content.contains("第 5 组 / 共 5 组"))
    }

    @Test
    fun `派生组数收敛到容量上限`() {
        val queue = BatchQueue()
        val added = queue.enqueueDerived("工作内容", 100)
        assertEquals(20, added)
        assertEquals(20, queue.size)
        assertEquals(20, queue.all().last().index)
        assertFalse(queue.enqueue("额外任务"))
    }

    @Test
    fun `派生至少一组并清空旧任务`() {
        val queue = BatchQueue()
        queue.enqueue("旧任务")
        val added = queue.enqueueDerived("新内容", 1)
        assertEquals(1, added)
        assertEquals(1, queue.size)
        assertEquals("新内容", queue.all().single().content.substringBefore("\n"))
    }

    @Test
    fun `poll按FIFO顺序消费且空队列返回null`() {
        val queue = BatchQueue()
        assertNull(queue.poll())
        queue.enqueueDerived("X", 3)
        assertEquals(1, queue.poll()?.index)
        assertEquals(2, queue.poll()?.index)
        assertEquals(3, queue.poll()?.index)
        assertNull(queue.poll())
        assertTrue(queue.isEmpty)
        assertNotNull(queue.all())
    }
}
