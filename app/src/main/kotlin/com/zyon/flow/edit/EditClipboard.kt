/**
 * Flow — in-memory clipboard for a whole look.
 *
 * Deliberately not persisted: this is a "carry it to the next photo" action, not
 * a preset. Saved looks live in RecipeStore.
 */

package com.zyon.flow.edit

object EditClipboard {

    private var held: EditState? = null

    val hasContent: Boolean get() = held != null

    /** Snapshot the look, minus anything tied to this specific photo. */
    fun copy(state: EditState) {
        held = state.copy(
            transform = Transform(),
            overlays = Overlays(),
            useMask = false,
            customLut = null,
            customLutName = null,
        )
    }

    /**
     * Paste onto [target]. Geometry, overlays and the mask are left alone so a
     * paste never silently moves the frame or the caption of the new photo.
     */
    fun paste(target: EditState): EditState {
        val clip = held ?: return target
        return target.copy(
            lutKey = clip.lutKey,
            lutIntensity = clip.lutIntensity,
            effectId = clip.effectId,
            effectIntensity = clip.effectIntensity,
            effectParam = clip.effectParam,
            presetId = clip.presetId,
            adjustments = clip.adjustments,
            curves = clip.curves,
            hsl = clip.hsl,
            splitShadow = clip.splitShadow,
            splitHighlight = clip.splitHighlight,
            border = clip.border,
        )
    }

    fun clear() {
        held = null
    }
}
