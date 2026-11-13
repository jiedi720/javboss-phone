package com.javboss.phone.data

/**
 * 服务端「调整封面」（Web 端 `KE` 弹窗里的 `aT` / `oT`）对应的纯规则，v0.3.73 引入。
 *
 * 服务端把宽幅 JAV 原图裁成竖版封面，用 `cover_crop_position`（0..1，默认 1＝靠右）
 * 记录横向裁切位置，接口 `PUT /jav/items/{id}` body `{cover_crop_position: r}`。
 *
 * Web 端关键常量与公式（bundle 646k 附近）：
 * - `rT = .47`，`iT = 800 * rT / 538 ≈ 0.6989`（竖版封面目标宽高比）；
 * - `oT(size, t)`：`i = w/h`，`a = min(1, iT/i)`、`o = min(1, i/iT)`，
 *   裁切框 `{left: t*(1-a), top: (1-o)/2, width: a, height: o}`；
 * - 只有 `a < 1`（原图比目标更宽、横向有裁切余地）时才允许调整；
 * - 拖动反解 `t = (x - a/2) / (1-a)`，x 为触点相对容器宽度的 0..1 比例；
 * - 滑块 0..1 显示 `round(t*100)%`，「重置」即 t = 1。
 *
 * 客户端列表卡片用 [com.javboss.phone.ui.components.coverCropAlignment] 把同一个 t
 * 换算成 Coil 的 BiasAlignment，因此这里保存的值与卡片显示天然一致。
 */
object CoverCropRules {

    /** 服务端默认位置：null / 空 / 非数字一律按 1（靠右）处理 */
    const val DEFAULT_POSITION = 1.0

    /** Web `iT = 800 * 0.47 / 538`，竖版封面的目标宽高比 */
    const val PORTRAIT_RATIO: Double = 800.0 * 0.47 / 538.0

    /** Web `aT(e)`：null / 非有限值 → 1，否则 clamp 到 0..1 */
    fun normalize(value: Double?): Double {
        if (value == null || !value.isFinite()) return DEFAULT_POSITION
        return value.coerceIn(0.0, 1.0)
    }

    /**
     * 预览用的裁切框，全部为相对原图的 0..1 比例；图片尺寸无效时返回 null
     * （对应 Web 端图片未加载 / 加载失败，此时隐藏裁切框并禁用保存）。
     */
    data class CropWindow(
        val left: Double,
        val top: Double,
        val width: Double,
        val height: Double,
    ) {
        /** 横向没有裁切余地时，位置不可调整（Web `m = p.width < 1`） */
        val adjustable: Boolean get() = width < 1.0
    }

    /** Web `oT(e, t)`：按图片真实宽高比计算裁切框 */
    fun cropWindow(imageWidth: Int, imageHeight: Int, position: Double?): CropWindow? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val ratio = imageWidth.toDouble() / imageHeight.toDouble()
        if (!ratio.isFinite() || ratio <= 0.0) return null
        val width = minOf(1.0, PORTRAIT_RATIO / ratio)
        val height = minOf(1.0, ratio / PORTRAIT_RATIO)
        return CropWindow(
            left = normalize(position) * (1.0 - width),
            top = (1.0 - height) / 2.0,
            width = width,
            height = height,
        )
    }

    /**
     * Web 拖动反解：把触点相对容器宽度的比例换算回 position。
     * 不可调整时原样返回 [current]（Web 的 `h()` 直接 return，不改状态）。
     */
    fun positionFromDrag(xRatio: Double, window: CropWindow, current: Double): Double {
        if (!window.adjustable) return normalize(current)
        val span = 1.0 - window.width
        return normalize((xRatio - window.width / 2.0) / span)
    }

    /** Web 滑块右侧的百分比文案数值（position 允许为 null，按服务端默认 1 处理） */
    fun percent(position: Double?): Int = Math.round(normalize(position) * 100.0).toInt()
}
