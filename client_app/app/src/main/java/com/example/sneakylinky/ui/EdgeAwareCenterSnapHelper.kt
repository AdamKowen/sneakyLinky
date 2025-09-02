import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Centers items normally, but clamps the first item to START (top/left)
 * and the last item to END (bottom/right). Uses OrientationHelper to
 * respect padding/decorations across devices.
 */
class EdgeAwareCenterSnapHelper : LinearSnapHelper() {

    private var verticalHelper: OrientationHelper? = null
    private var horizontalHelper: OrientationHelper? = null

    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        if (layoutManager !is LinearLayoutManager) return super.findSnapView(layoutManager)

        val helper = if (layoutManager.canScrollVertically())
            getVerticalHelper(layoutManager) else getHorizontalHelper(layoutManager)

        val first = layoutManager.findFirstVisibleItemPosition()
        val last  = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return null

        val startAfter = helper.startAfterPadding
        val endAfter   = helper.endAfterPadding

        // Clamp at start
        if (first == 0) {
            layoutManager.findViewByPosition(0)?.let { firstView ->
                val dStart = helper.getDecoratedStart(firstView)
                if (dStart >= startAfter) return firstView
            }
        }

        // Clamp at end
        if (last == layoutManager.itemCount - 1) {
            layoutManager.findViewByPosition(last)?.let { lastView ->
                val dEnd = helper.getDecoratedEnd(lastView)
                if (dEnd <= endAfter) return lastView
            }
        }

        // Otherwise, default center behavior
        return super.findSnapView(layoutManager)
    }

    override fun calculateDistanceToFinalSnap(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View
    ): IntArray? {
        val out = intArrayOf(0, 0)

        if (layoutManager is LinearLayoutManager) {
            if (layoutManager.canScrollVertically()) {
                val helper = getVerticalHelper(layoutManager)
                val pos = layoutManager.getPosition(targetView)
                val count = layoutManager.itemCount

                // Start clamp (top)
                if (pos == 0) {
                    out[1] = helper.getDecoratedStart(targetView) - helper.startAfterPadding
                    return out
                }
                // End clamp (bottom)
                if (pos == count - 1) {
                    out[1] = helper.getDecoratedEnd(targetView) - helper.endAfterPadding
                    return out
                }
                // Center
                val childCenter = helper.getDecoratedStart(targetView) +
                        helper.getDecoratedMeasurement(targetView) / 2
                val containerCenter = helper.startAfterPadding + helper.totalSpace / 2
                out[1] = childCenter - containerCenter
                return out
            } else if (layoutManager.canScrollHorizontally()) {
                val helper = getHorizontalHelper(layoutManager)
                val pos = layoutManager.getPosition(targetView)
                val count = layoutManager.itemCount

                if (pos == 0) {
                    out[0] = helper.getDecoratedStart(targetView) - helper.startAfterPadding
                    return out
                }
                if (pos == count - 1) {
                    out[0] = helper.getDecoratedEnd(targetView) - helper.endAfterPadding
                    return out
                }
                val childCenter = helper.getDecoratedStart(targetView) +
                        helper.getDecoratedMeasurement(targetView) / 2
                val containerCenter = helper.startAfterPadding + helper.totalSpace / 2
                out[0] = childCenter - containerCenter
                return out
            }
        }

        return super.calculateDistanceToFinalSnap(layoutManager, targetView)
    }

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int
    ): Int {
        val base = super.findTargetSnapPosition(layoutManager, velocityX, velocityY)
        if (layoutManager is LinearLayoutManager && base != RecyclerView.NO_POSITION) {
            return base.coerceIn(0, layoutManager.itemCount - 1)
        }
        return base
    }

    private fun getVerticalHelper(lm: RecyclerView.LayoutManager): OrientationHelper {
        if (verticalHelper == null || verticalHelper?.layoutManager !== lm) {
            verticalHelper = OrientationHelper.createVerticalHelper(lm)
        }
        return verticalHelper!!
    }

    private fun getHorizontalHelper(lm: RecyclerView.LayoutManager): OrientationHelper {
        if (horizontalHelper == null || horizontalHelper?.layoutManager !== lm) {
            horizontalHelper = OrientationHelper.createHorizontalHelper(lm)
        }
        return horizontalHelper!!
    }
}
