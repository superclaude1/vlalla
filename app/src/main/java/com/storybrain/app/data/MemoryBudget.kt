package com.storybrain.app.data

internal fun validateMemoryBudget(selected: List<MemoryItemEntity>, adding: MemoryItemEntity) {
    if (selected.any { it.id == adding.id }) return
    if (selected.size >= StoryRepository.MAX_SELECTED_MEMORIES) {
        throw MemorySelectionException("最多选择 ${StoryRepository.MAX_SELECTED_MEMORIES} 条记忆")
    }
    if (selected.sumOf { it.content.length } + adding.content.length > StoryRepository.MAX_MEMORY_CHARS) {
        throw MemorySelectionException("记忆内容已达到 ${StoryRepository.MAX_MEMORY_CHARS} 字上限")
    }
}
