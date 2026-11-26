/**
 * Flow — user-created layers.
 *
 * A layer starts empty and the user decides what it becomes: a cube, a colour
 * grade, an artistic effect, a caption or a sticker. Layers are composited in
 * stack order, bottom first, so moving a layer up really does change how the
 * render looks — a caption under an effect gets graded along with the photo, and
 * one above it does not.
 *
 * The base look in [EditState] still exists and runs first. Layers are what the
 * user adds on top, which keeps old projects loading unchanged.
 */

package com.zyon.flow.edit

/** What a user layer holds. [EMPTY] is the state a freshly added layer starts in. */
enum class LayerContent(val label: String) {
    EMPTY("Empty"),
    CUBE("Cube"),
    COLOUR("Colour"),
    EFFECT("Effect"),
    TEXT("Text"),
    STICKER("Sticker"),
    ;

    /** True for the kinds that change pixels rather than draw on top. */
    val isAdjustment: Boolean
        get() = this == CUBE || this == COLOUR || this == EFFECT
}

data class PhotoLayer(
    val id: Long,
    val content: LayerContent = LayerContent.EMPTY,
    val visible: Boolean = true,
    val locked: Boolean = false,
    /** 0..1, blends this layer's contribution over what is already there. */
    val opacity: Float = 1f,

    // ---- CUBE
    val lutKey: String? = null,
    val lutName: String? = null,
    /** 0..1 blend of the cube against what is underneath this layer. */
    val lutIntensity: Float = 1f,

    // ---- COLOUR: a full manual grade, same struct the base uses
    val adjustments: Adjustments = Adjustments(),

    // ---- EFFECT
    val effectId: String = "none",
    val effectIntensity: Float = 1f,
    val effectParam: Float = 0.5f,

    // ---- TEXT
    val text: TextLayer? = null,

    // ---- STICKER
    val sticker: StickerLayer? = null,
) {
    /** Shown under the thumbnail. */
    val displayName: String
        get() = when (content) {
            LayerContent.EMPTY -> "Empty layer"
            LayerContent.CUBE -> lutName ?: lutKey ?: "Cube"
            LayerContent.COLOUR -> "Colour"
            LayerContent.EFFECT -> if (effectId == "none") "Effect (pick one)" else Effects.byId(effectId).name
            LayerContent.TEXT -> text?.text?.take(22)?.ifBlank { "Text" } ?: "Text"
            LayerContent.STICKER -> sticker?.emoji ?: "Sticker"
        }

    /** A layer that would render nothing is skipped, but still listed. */
    val isRenderable: Boolean
        get() = visible && when (content) {
            LayerContent.EMPTY -> false
            LayerContent.CUBE -> !lutKey.isNullOrEmpty() && lutIntensity > 0.004f
            LayerContent.COLOUR -> adjustments.hasAnyChange()
            LayerContent.EFFECT -> effectId != "none" && effectIntensity > 0.004f
            LayerContent.TEXT -> text != null && !text.isBlank()
            LayerContent.STICKER -> sticker != null && sticker.visible
        }

    /** Blend factor for adjustment layers. */
    val strength: Float
        get() = when (content) {
            LayerContent.CUBE -> lutIntensity
            LayerContent.EFFECT -> effectIntensity
            else -> 1f
        } * opacity
}

/** True when any slider in the grade would change a pixel. */
fun Adjustments.hasAnyChange(): Boolean =
    exposure != 0f || contrast != 0f || saturation != 0f || warmth != 0f ||
        fade != 0f || vignette != 0f || grain != 0f || sharpen != 0f ||
        texture != 0f || clarity != 0f || highlights != 0f || shadows != 0f ||
        whites != 0f || blacks != 0f || dehaze != 0f ||
        denoiseColour != 0f || denoiseLuma != 0f || sharpenMask != 0f ||
        vibrance != 0f || hueShift != 0f

/** Helpers for editing the layer list from the UI. */
object Layers {

    private var counter = 1L

    fun newLayer(): PhotoLayer = PhotoLayer(id = System.nanoTime() + counter++)

    /** Set what a layer holds, keeping its identity and visibility. */
    fun setContent(layer: PhotoLayer, content: LayerContent): PhotoLayer = when (content) {
        LayerContent.EMPTY -> layer.copy(content = LayerContent.EMPTY)
        LayerContent.CUBE -> layer.copy(content = content, lutKey = layer.lutKey, lutIntensity = 1f)
        LayerContent.COLOUR -> layer.copy(content = content, adjustments = Adjustments())
        LayerContent.EFFECT -> layer.copy(
            content = content,
            // Deliberately neutral. Auto-picking the first effect (ASCII) turned
            // the photo black the instant a layer was created, which read as a
            // bug. The layer stays inert until an effect is chosen in its tab.
            effectIntensity = if (layer.effectId == "none") 0f else 1f,
        )
        LayerContent.TEXT -> layer.copy(
            content = content,
            text = layer.text ?: TextLayer(id = layer.id, text = "FLOW"),
        )
        LayerContent.STICKER -> layer.copy(
            content = content,
            sticker = layer.sticker ?: StickerLayer(id = layer.id, emoji = "✦"),
        )
    }

    /** Append a fresh empty layer on top of the stack and select it. */
    fun add(state: EditState): EditState {
        val layer = newLayer()
        return state.copy(
            layers = state.layers + layer,
            selectedLayerId = layer.id,
        )
    }

    /** Replace a layer with a copy that holds [content], keeping its place. */
    fun applyContent(state: EditState, id: Long, content: LayerContent): EditState =
        update(state, id) { setContent(it, content) }

    fun remove(state: EditState, id: Long): EditState = state.copy(
        layers = state.layers.filterNot { it.id == id },
        selectedLayerId = if (state.selectedLayerId == id) null else state.selectedLayerId,
    )

    fun update(state: EditState, id: Long, block: (PhotoLayer) -> PhotoLayer): EditState =
        state.copy(layers = state.layers.map { if (it.id == id) block(it) else it })

    fun selected(state: EditState): PhotoLayer? =
        state.selectedLayerId?.let { sel -> state.layers.firstOrNull { it.id == sel } }

    /** Move a layer up or down the stack. Index 0 is the bottom. */
    fun move(state: EditState, id: Long, up: Boolean): EditState {
        val list = state.layers.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return state
        val target = if (up) i + 1 else i - 1
        if (target !in list.indices) return state
        val moved = list.removeAt(i)
        list.add(target, moved)
        return state.copy(layers = list)
    }

    fun toggleVisible(state: EditState, id: Long): EditState =
        update(state, id, { it.copy(visible = !it.visible) })

    fun toggleLock(state: EditState, id: Long): EditState =
        update(state, id, { it.copy(locked = !it.locked) })
}
