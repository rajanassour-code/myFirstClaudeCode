package com.liveassist.sdk

import android.graphics.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement

/*
 * Jetpack Compose markers (only used if your app uses Compose; the SDK doesn't bundle Compose).
 *
 *   Text(cardNumber, Modifier.liveAssistMasked())
 *   Button(onClick = confirm, modifier = Modifier.liveAssistNoRemote()) { Text("Confirm") }
 */

/** Hide this composable from the advisor (painted dark in every frame). */
fun Modifier.liveAssistMasked(): Modifier = this then MarkerElement(LiveAssistRegistry.Kind.MASKED)

/** The advisor can never tap or change this composable, even with control. */
fun Modifier.liveAssistNoRemote(): Modifier = this then MarkerElement(LiveAssistRegistry.Kind.NO_REMOTE)

private class MarkerElement(val kind: LiveAssistRegistry.Kind) : ModifierNodeElement<MarkerNode>() {
    override fun create() = MarkerNode(kind)
    override fun update(node: MarkerNode) { node.kind = kind }
    override fun equals(other: Any?) = other is MarkerElement && other.kind == kind
    override fun hashCode() = kind.hashCode()
}

private class MarkerNode(var kind: LiveAssistRegistry.Kind) : Modifier.Node(), GlobalPositionAwareModifierNode {
    private val key = Any()
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val b = coordinates.boundsInWindow()
        LiveAssistRegistry.set(key, Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()), kind)
    }
    override fun onDetach() { LiveAssistRegistry.remove(key) }
}
