package com.navrot.aifuelassistant.ui.map

data class MarkerDiffResult<T>(
    val toRemoveIds: Set<Int>,
    val toAdd: List<T>,
    val toUpdate: List<T>
)

object MarkerDiffCalculator {
    fun <T> calculateDiff(
        existingIds: Set<Int>,
        newList: List<T>,
        idSelector: (T) -> Int
    ): MarkerDiffResult<T> {
        val newIds = newList.map(idSelector).toSet()
        val toRemoveIds = existingIds - newIds
        val toAdd = newList.filter { idSelector(it) !in existingIds }
        val toUpdate = newList.filter { idSelector(it) in existingIds }
        return MarkerDiffResult(
            toRemoveIds = toRemoveIds,
            toAdd = toAdd,
            toUpdate = toUpdate
        )
    }
}
