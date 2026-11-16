package com.example.ippurewidget

import android.graphics.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object ChartBitmapRenderer {

    fun renderDashboard(info: IPPureInfo, history: List<MetricPoint>): Bitmap {
        val width = 720
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawFlClashCards(canvas, width.toFloat(), height.toFloat(), info.isRefreshing)
        drawFlClashGaugeCard(canvas, 10f, 10f, 252f, 350f, info)
        drawNodeHeader(canvas, 276f, 696f, info)
        drawDualComparisonChart(canvas, 276f, 186f, 696f, 334f, history, getRiskColor(info.fraudScore))
        return bitmap
    }

    private fun drawFlClashCards(canvas: Canvas, w: Float, h: Float, isRefreshing: Boolean) {
        val rootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D1017") }
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 32f, 32f, rootPaint)

        val rootBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isRefreshing) 3f else 2f
            color = if (isRefreshing) Color.parseColor("#00E5FF") else Color.parseColor("#23293A")
        }
        canvas.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), 32f, 32f, rootBorder)

        val subCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#161B26") }
        val subCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#283146")
        }
        val leftRect = RectF(10f, 10f, 252f, h - 10f)
        canvas.drawRoundRect(leftRect, 24f, 24f, subCardPaint)
        canvas.drawRoundRect(leftRect, 24f, 24f, subCardBorder)

        val rightRect = RectF(262f, 10f, w - 10f, h - 10f)
        canvas.drawRoundRect(rightRect, 24f, 24f, subCardPaint)
        canvas.drawRoundRect(rightRect, 24f, 24f, subCardBorder)
    }

    private fun drawFlClashGaugeCard(canvas: Canvas, cardLeft: Float, cardTop: Float, cardRight: Float, cardBottom: Float, info: IPPureInfo) {
        val cx = (cardLeft + cardRight) / 2f
        val cy = cardTop + 114f
        val radius = 84f
        val score = info.fraudScore.coerceIn(0, 100)
        val riskColor = getRiskColor(score)

        val startAngle = 140f
        val sweepMax = 260f
        val activeSweep = sweepMax * (score / 100f)

        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND }
        for (i in 0..26) {
            val fraction = i.toFloat() / 26
            val rad = Math.toRadians((startAngle + sweepMax * fraction).toDouble())
            val isLit = fraction <= (score / 100f)
            tickPaint.color = if (isLit) riskColor else Color.parseColor("#2E384F")
            tickPaint.alpha = if (isLit) 235 else 140
            canvas.drawLine((cx + (radius - 19f) * cos(rad)).toFloat(), (cy + (radius - 19f) * sin(rad)).toFloat(),
                (cx + (radius - 12f) * cos(rad)).toFloat(), (cy + (radius - 12f) * sin(rad)).toFloat(), tickPaint)
        }

        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 15f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#252D3F")
        }
        canvas.drawArc(arcRect, startAngle, sweepMax, false, trackPaint)

        if (activeSweep > 0f) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 18f; strokeCap = Paint.Cap.ROUND
                color = riskColor; alpha = 135; maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawArc(arcRect, startAngle, activeSweep, false, glowPaint)

            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 15f; strokeCap = Paint.Cap.ROUND; color = riskColor
            }
            canvas.drawArc(arcRect, startAngle, activeSweep, false, progressPaint)
        }

        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 46f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$score%", cx, cy + 12f, numPaint)

        val statusText = when { score <= 25 -> "纯净 · PURE"; score <= 50 -> "轻度 · LOW"; score <= 70 -> "中度 · WARN"; else -> "高危 · RISK" }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = riskColor; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(statusText, cx, cy + 40f, statusPaint)

        drawLatencyPill(canvas, cardLeft + 14f, cardBottom - 98f, cardRight - 14f, cardBottom - 56f, "🌐 代理", info.proxyPingMs)
        drawLatencyPill(canvas, cardLeft + 14f, cardBottom - 48f, cardRight - 14f, cardBottom - 12f, "⚡ 直连", info.directPingMs)
    }

    private fun drawLatencyPill(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, prefix: String, pingMs: Int) {
        val color = getPingColor(pingMs)
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 34 })
        canvas.drawRoundRect(rect, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; this.color = color; alpha = 145
        })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val valText = if (pingMs > 0) "$prefix ${pingMs}ms" else "$prefix 超时"
        canvas.drawText(valText, (left + right) / 2f, top + (bottom - top) / 2f + 6.5f, textPaint)
    }

    private fun drawNodeHeader(canvas: Canvas, left: Float, right: Float, info: IPPureInfo) {
        val ipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 33f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(info.ip, left, 48f, ipPaint)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (info.isRefreshing) Color.parseColor("#00E5FF") else Color.parseColor("#94A3B8")
            textSize = 16f; typeface = if (info.isRefreshing) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(if (info.isRefreshing) "↻ 测速中..." else "↻ ${info.updatedAt}", right, 46f, timePaint)

        val orgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
        val orgDisplay = if (info.org.isNotEmpty()) truncateText(info.org, orgPaint, right - left) else "AS${info.asn} 未知运营商"
        canvas.drawText(orgDisplay, left, 78f, orgPaint)

        val flag = IPPureRepository.countryCodeToFlag(info.countryCode)
        val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#94A3B8"); textSize = 19f }
        canvas.drawText(truncateText("$flag ${info.country}${info.city}".trim(), locPaint, right - left), left, 106f, locPaint)

        var curX = left
        curX += drawBadge(canvas, curX, 118f, 150f, if (info.isResidential) "🏠 住宅家宽" else "🏢 机房IDC",
            if (info.isResidential) Color.parseColor("#38BDF8") else Color.parseColor("#A78BFA")) + 8f
        curX += drawBadge(canvas, curX, 118f, 150f, if (info.isBroadcast) "📡 广播IP" else "✨ 原生IP",
            if (info.isBroadcast) Color.parseColor("#F59E0B") else Color.parseColor("#10B981")) + 8f
        if (info.asn.isNotEmpty() && info.asn != "0") {
            drawBadge(canvas, curX, 118f, 150f, "AS${info.asn}", Color.parseColor("#94A3B8"))
        }
    }

    private fun drawBadge(canvas: Canvas, left: Float, top: Float, bottom: Float, text: String, color: Int): Float {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 17f; typeface = Typeface.DEFAULT_BOLD }
        val width = textPaint.measureText(text) + 24f
        val rect = RectF(left, top, left + width, bottom)
        canvas.drawRoundRect(rect, 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 36 })
        canvas.drawRoundRect(rect, 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f; this.color = color; alpha = 110
        })
        canvas.drawText(text, left + 12f, top + (bottom - top) / 2f + 6f, textPaint)
        return width
    }

    private fun drawDualComparisonChart(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, history: List<MetricPoint>, riskColor: Int) {
        val data = if (history.size >= 2) history else listOf(MetricPoint(20, 85), MetricPoint(16, 72), MetricPoint(22, 90), history.firstOrNull() ?: MetricPoint(15, 65))
        val w = right - left
        val h = bottom - top

        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        legendPaint.color = Color.parseColor("#F59E0B")
        canvas.drawText("● 风险分(${data.last().score}%)", left, top - 8f, legendPaint)

        val maxPing = max(100, data.maxOf { it.proxyPingMs.coerceAtLeast(10) })
        legendPaint.color = Color.parseColor("#00E5FF")
        val lastPingStr = if (data.last().proxyPingMs > 0) "${data.last().proxyPingMs}ms" else "超时"
        canvas.drawText("● 代理延迟($lastPingStr / 峰值${maxPing}ms)", left + 150f, top - 8f, legendPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A3449"); strokeWidth = 1.2f; pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }
        for (i in 0..2) {
            val gy = top + (h / 2f) * i
            canvas.drawLine(left, gy, right, gy, gridPaint)
        }

        val stepX = w / (data.size - 1).coerceAtLeast(1)
        val pingPoints = data.mapIndexed { idx, pt ->
            val validPing = if (pt.proxyPingMs > 0) pt.proxyPingMs else maxPing
            val ratio = (validPing.toFloat() / maxPing).coerceIn(0.06f, 1f)
            PointF(left + idx * stepX, bottom - ratio * (h - 16f) - 8f)
        }
        val scoreLineColor = if (riskColor == Color.parseColor("#10B981")) Color.parseColor("#F59E0B") else riskColor
        val scorePoints = data.mapIndexed { idx, pt ->
            PointF(left + idx * stepX, bottom - (pt.score.coerceIn(0, 100) / 100f) * (h - 16f) - 8f)
        }

        drawSmoothSeries(canvas, pingPoints, bottom, top, Color.parseColor("#00E5FF"), 55, 3.5f)
        drawSmoothSeries(canvas, scorePoints, bottom, top, scoreLineColor, 45, 3.5f)
    }

    private fun drawSmoothSeries(canvas: Canvas, points: List<PointF>, bottom: Float, top: Float, strokeColor: Int, fillAlpha: Int, strokeWidth: Float) {
        if (points.size < 2) return
        val linePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]; val p1 = points[i + 1]; val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }
        val fillPath = Path(linePath).apply { lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close() }
        canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, top, 0f, bottom,
                intArrayOf(Color.argb(fillAlpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
        })
        canvas.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; this.strokeWidth = strokeWidth + 3.5f; color = strokeColor; alpha = 95
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        })
        canvas.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; this.strokeWidth = strokeWidth; strokeCap = Paint.Cap.ROUND; color = strokeColor
        })
        val last = points.last()
        canvas.drawCircle(last.x, last.y, 8.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; alpha = 120 })
        canvas.drawCircle(last.x, last.y, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return if (end > 0) text.substring(0, end) + "…" else text
    }

    private fun getRiskColor(score: Int): Int = when {
        score <= 25 -> Color.parseColor("#10B981")
        score <= 50 -> Color.parseColor("#F59E0B")
        score <= 70 -> Color.parseColor("#F97316")
        else -> Color.parseColor("#F43F5E")
    }

    private fun getPingColor(pingMs: Int): Int = when {
        pingMs <= 0 -> Color.parseColor("#F43F5E")
        pingMs <= 80 -> Color.parseColor("#00E5FF")
        pingMs <= 180 -> Color.parseColor("#10B981")
        pingMs <= 300 -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#F43F5E")
    }
}
