/**
 * Flow — undo and redo over the whole edit state.
 *
 * [EditState] is already an immutable data class, so history is just a list of
 * snapshots plus a cursor. That makes undo exact: no inverse operations to write,
 * no drift between what a command did and what its undo claims to do.
 *
 * The stack is coalesced by a key so that dragging a slider produces one entry
 * instead of two hundred. Every push from the same key within the coalesce window
 * replaces the top instead of stacking.
 */

package com.zyon.flow.edit

class EditHistory(
    private val limit: Int = 60,
    private val coalesceMs: Long = 600L,
) {
    private val stack = ArrayList<EditState>()
    private var cursor = -1
    private var lastKey: String? = null
    private var lastAt: Long = 0L

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor >= 0 && cursor < stack.lastIndex
    val size: Int get() = stack.size

    /** Seed the history with the state the editor opened with. */
    fun reset(initial: EditState) {
        stack.clear()
        stack.add(initial)
        cursor = 0
        lastKey = null
        lastAt = 0L
    }

    /**
     * Record [next]. When [key] is the same as the previous push and it happened
     * recently, the top entry is replaced: a slider drag is one undo step.
     */
    fun push(next: EditState, key: String? = null) {
        if (cursor < 0) {
            reset(next)
            return
        }
        if (stack[cursor] == next) return

        val now = System.currentTimeMillis()
        val sameKey = key != null && key == lastKey && (now - lastAt) < coalesceMs

        if (sameKey) {
            stack[cursor] = next
        } else {
            // drop everything ahead of the cursor: a new edit kills the redo tail
            while (stack.size > cursor + 1) stack.removeAt(stack.lastIndex)
            stack.add(next)
            if (stack.size > limit) {
                stack.removeAt(0)
            }
            cursor = stack.lastIndex
        }

        lastKey = key
        lastAt = now
    }

    fun undo(): EditState? {
        if (!canUndo) return null
        cursor--
        lastKey = null
        return stack[cursor]
    }

    fun redo(): EditState? {
        if (!canRedo) return null
        cursor++
        lastKey = null
        return stack[cursor]
    }

    /** The state at the cursor, for callers that need to resync. */
    fun current(): EditState? = if (cursor in stack.indices) stack[cursor] else null
}
