package com.example.aitokenbench

/** 一个待测评的任务组（一次批量测评最多 20 组） */
data class TaskGroup(val index: Int, val content: String)

/**
 * 批量任务队列：串行消费，便于展示进度。
 * 单次运行最多 20 组；每组会分发给所有启用的模型。
 */
class BatchQueue(private val capacity: Int = MAX_GROUPS) {

    companion object {
        const val MAX_GROUPS = 20
    }

    private val tasks = mutableListOf<TaskGroup>()

    val size: Int get() = tasks.size
    val isEmpty: Boolean get() = tasks.isEmpty()

    fun enqueue(content: String): Boolean {
        if (tasks.size >= capacity) return false
        tasks.add(TaskGroup(tasks.size + 1, content))
        return true
    }

    /**
     * 由一段输入内容派生 count 组任务（演示用：附加组序号）。
     * 返回实际入队数量。
     */
    fun enqueueDerived(content: String, count: Int): Int {
        val n = count.coerceIn(1, capacity)
        clear()
        for (i in 1..n) {
            tasks.add(TaskGroup(i, "$content\n（第 $i 组 / 共 $n 组）"))
        }
        return n
    }

    fun poll(): TaskGroup? = if (tasks.isEmpty()) null else tasks.removeAt(0)

    fun all(): List<TaskGroup> = tasks.toList()

    fun clear() = tasks.clear()
}
