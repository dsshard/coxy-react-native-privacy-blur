package com.privacyblur

import android.animation.TimeInterpolator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.UiThread
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil.runOnUiThread
import com.facebook.react.module.annotations.ReactModule
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@ReactModule(name = PrivacyBlurModule.NAME)
class PrivacyBlurModule(private val reactCtx: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactCtx) {

  companion object { const val NAME = "PrivacyBlur" }

  // Config
  @Volatile private var enabled: Boolean = true
  @Volatile private var blurRadiusPx: Int = dp(reactCtx, 20f) // паритет с iOS (dp->px)
  @Volatile private var durationMs: Long = 200L

  // Views
  @Volatile private var hostActivityRef: WeakReference<Activity>? = null
  private var container: FrameLayout? = null
  private var imageView: ImageView? = null
  private var solidView: View? = null

  // Anim
  private val interpolator: TimeInterpolator = AccelerateDecelerateInterpolator()

  override fun getName(): String = NAME

  // ---------- Public API (RN) ----------

  @ReactMethod
  fun configure(conf: ReadableMap) {
    if (conf.hasKey("duration") && !conf.isNull("duration")) {
      val ms = conf.getDouble("duration")
      durationMs = ms.coerceAtLeast(0.0).toLong()
    }
    if (conf.hasKey("blurRadius") && !conf.isNull("blurRadius")) {
      val r = conf.getInt("blurRadius")
      blurRadiusPx = dp(reactCtx, r.toFloat()).coerceAtLeast(0)
    }
  }

  @ReactMethod
  fun enable() { enabled = true }

  @ReactMethod
  fun disable() {
    enabled = false
    runOnUiThread { hideOverlayImmediate() }
  }

  @ReactMethod
  fun isEnabled(promise: Promise) { promise.resolve(enabled) }

  @ReactMethod
  fun showNow() { runOnUiThread { if (enabled) { showOverlay() } } }

  @ReactMethod
  fun hideNow() { runOnUiThread { animateHide() } }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    runOnUiThread { hideOverlayImmediate() }
  }

  // ---------- Overlay ----------

  private fun getActivitySafely(): Activity? {
    val a = super.getCurrentActivity()
    if (a != null) {
      hostActivityRef = WeakReference(a)
      return a
    }
    return hostActivityRef?.get()
  }

  @UiThread
  private fun ensureOverlay(activity: Activity) {
    if (container?.parent != null) return

    val root = activity.window?.decorView as? ViewGroup ?: return

    val c = object : FrameLayout(activity) {
      override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bringToFront()
      }
    }
    c.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    c.isClickable = false
    c.isFocusable = false
    c.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    c.setWillNotDraw(true)
    c.alpha = 1f

    val sv = View(activity).apply {
      layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      setBackgroundColor(Color.WHITE)
      visibility = View.GONE
    }
    c.addView(sv)
    solidView = sv

    val iv = ImageView(activity).apply {
      layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      scaleType = ImageView.ScaleType.FIT_XY
      alpha = 1f
    }
    c.addView(iv)
    imageView = iv

    root.addView(c)
    c.bringToFront()
    container = c
  }

  @UiThread
  private fun showOverlay() {
    if (!enabled) return
    val activity = getActivitySafely() ?: return

    val snapshot = captureActivityBitmap(activity)

    val useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusPx > 0
    val blurredBitmap: Bitmap? = if (blurRadiusPx > 0 && snapshot != null) {
      if (useRenderEffect) snapshot
      else stackBlur(snapshot.copy(Bitmap.Config.ARGB_8888, true), (blurRadiusPx / 2).coerceAtLeast(1))
    } else null

    ensureOverlay(activity)

    if (blurredBitmap != null) {
      solidView?.visibility = View.GONE

      if (useRenderEffect) {
        imageView?.setRenderEffect(
          RenderEffect.createBlurEffect(
            blurRadiusPx.toFloat(),
            blurRadiusPx.toFloat(),
            Shader.TileMode.CLAMP
          )
        )
        imageView?.setImageBitmap(blurredBitmap)
      } else {
        imageView?.setRenderEffect(null)
        imageView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        imageView?.setImageBitmap(blurredBitmap)
      }

      val shouldAnimate = durationMs > 0 && isActivityActive(activity)
      if (shouldAnimate) {
        imageView?.alpha = 0f
        imageView?.animate()
          ?.alpha(1f)
          ?.setDuration(durationMs)
          ?.setInterpolator(interpolator)
          ?.start()
      } else {
        imageView?.alpha = 1f
      }
    } else {
      imageView?.setRenderEffect(null)
      imageView?.setImageDrawable(null)
      imageView?.alpha = 0f
      solidView?.visibility = View.VISIBLE
      container?.alpha = 1f
    }
  }

  @UiThread
  private fun animateHide() {
    val c = container ?: return
    c.animate()
      .alpha(0f)
      .setDuration(durationMs)
      .setInterpolator(interpolator)
      .withEndAction { hideOverlayImmediate() }
      .start()
  }

  @UiThread
  private fun hideOverlayImmediate() {
    imageView?.setRenderEffect(null)
    imageView?.setImageDrawable(null)
    (container?.parent as? ViewGroup)?.removeView(container)
    container = null
    imageView = null
    solidView = null
  }

  // ---------- Snapshot & Blur ----------

  private fun captureActivityBitmap(activity: Activity): Bitmap? {
    val root = activity.window?.decorView?.rootView ?: return null
    val width = root.width
    val height = root.height
    if (width <= 0 || height <= 0) return null

    val sample = 4
    val bmp = Bitmap.createBitmap(width / sample, height / sample, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(1f / sample, 1f / sample)
    root.draw(canvas)
    return bmp
  }

  // быстрый stack blur для API < 31
  private fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap? {
    if (radius < 1) return bitmap

    val w = bitmap.width
    val h = bitmap.height
    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)

    var rsum: Int
    var gsum: Int
    var bsum: Int
    var x: Int
    var y: Int
    var i: Int
    var p: Int
    var yp: Int
    var yi: Int
    var yw: Int

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)

    val r1 = radius + 1
    val divsum = r1 * r1
    val dv = IntArray(256 * divsum).apply {
      var k = 0
      while (k < size) { this[k] = k / divsum; k++ }
    }

    yw = 0
    yi = 0

    val vmin = IntArray(max(w, h))
    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int
    var stackstart: Int
    var sir: IntArray
    var routsum: Int
    var goutsum: Int
    var boutsum: Int
    var rinsum: Int
    var ginsum: Int
    var binsum: Int

    y = 0
    while (y < h) {
      rinsum = 0; ginsum = 0; binsum = 0
      routsum = 0; goutsum = 0; boutsum = 0
      rsum = 0; gsum = 0; bsum = 0

      i = -radius
      while (i <= radius) {
        p = pix[yi + min(wm, max(i, 0))]
        sir = stack[i + radius]
        sir[0] = (p and 0xff0000) shr 16
        sir[1] = (p and 0x00ff00) shr 8
        sir[2] = (p and 0x0000ff)
        val rbs = r1 - abs(i)
        rsum += sir[0] * rbs
        gsum += sir[1] * rbs
        bsum += sir[2] * rbs
        if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
        else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
        i++
      }
      stackpointer = radius
      x = 0
      while (x < w) {
        r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]

        rsum -= routsum; gsum -= goutsum; bsum -= boutsum

        stackstart = stackpointer - radius + div
        sir = stack[stackstart % div]

        routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

        if (y == 0) vmin[x] = min(x + r1, wm)
        p = pix[yw + vmin[x]]

        sir[0] = (p and 0xff0000) shr 16
        sir[1] = (p and 0x00ff00) shr 8
        sir[2] = (p and 0x0000ff)

        rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
        rsum += rinsum; gsum += ginsum; bsum += binsum

        stackpointer = (stackpointer + 1) % div
        sir = stack[stackpointer]

        routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
        rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]

        yi++; x++
      }
      yw += w
      y++
    }

    x = 0
    while (x < w) {
      rinsum = 0; ginsum = 0; binsum = 0
      routsum = 0; goutsum = 0; boutsum = 0
      rsum = 0; gsum = 0; bsum = 0
      yp = -radius * w
      i = -radius
      while (i <= radius) {
        yi = max(0, yp) + x
        sir = stack[i + radius]
        sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
        val rbs = r1 - abs(i)
        rsum += r[yi] * rbs
        gsum += g[yi] * rbs
        bsum += b[yi] * rbs
        if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
        else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
        if (i < hm) yp += w
        i++
      }
      yi = x
      stackpointer = radius
      y = 0
      while (y < h) {
        pix[yi] = (0xff000000.toInt()
          or (dv[rsum] shl 16)
          or (dv[gsum] shl 8)
          or dv[bsum])

        rsum -= routsum; gsum -= goutsum; bsum -= boutsum

        stackstart = stackpointer - radius + div
        sir = stack[stackstart % div]
        routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

        if (x == 0) vmin[y] = min(y + r1, hm) * w
        p = x + vmin[y]

        sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
        rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
        rsum += rinsum; gsum += ginsum; bsum += binsum

        stackpointer = (stackpointer + 1) % div
        sir = stack[stackpointer]
        routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
        rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]

        yi += w; y++
      }
      x++
    }

    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return bitmap
  }

  private fun isActivityActive(activity: Activity): Boolean {
    return !activity.isFinishing && !activity.isDestroyed
  }

  private fun dp(ctx: ReactApplicationContext, v: Float): Int {
    val d = ctx.resources.displayMetrics.density
    return (v * d).toInt()
  }
}
