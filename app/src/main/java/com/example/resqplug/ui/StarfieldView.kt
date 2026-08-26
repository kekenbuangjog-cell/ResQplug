package com.example.resqplug.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * StarfieldView
 * Enhanced 8-bit starfield with:
 * - 80 stars with varied sizes (1-4px) and depth layers
 * - Slow downward drift
 * - Twinkling with random phase offsets
 * - Glow/bloom on bright stars
 * - Occasional shooting stars
 */
class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Star(
        val x: Float,
        var currentY: Float,
        val size: Float,
        val color: Int,
        val alpha: Int,
        val driftSpeed: Float,
        val twinkle: Boolean,
        var twinklePhase: Int,
        var bright: Boolean,
        val hasGlow: Boolean
    )

    private data class ShootingStar(
        var x: Float,
        var y: Float,
        val speedX: Float,
        val speedY: Float,
        val length: Float,
        var alpha: Int,
        var life: Int,
        val maxLife: Int
    )

    private val stars = mutableListOf<Star>()
    private val shootingStars = mutableListOf<ShootingStar>()
    private var initialized = false
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var tickCount = 0

    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    private val neonColors = intArrayOf(
        Color.parseColor("#FF1493"), // deep pink
        Color.parseColor("#00FFFF"), // cyan
        Color.parseColor("#F1C40F"), // yellow
        Color.parseColor("#00FF88"), // green
        Color.parseColor("#FF6B9D"), // soft pink
        Color.parseColor("#7DF9FF"), // electric blue
        Color.parseColor("#FFE066")  // warm yellow
    )

    fun tick() {
        tickCount++

        // Drift stars downward
        for (star in stars) {
            star.currentY += star.driftSpeed
            if (star.currentY > screenHeight) {
                star.currentY = -star.size
            }
        }

        // Twinkle stars with phase offsets
        for (star in stars) {
            if (star.twinkle) {
                star.twinklePhase++
                // Toggle every ~8 ticks, offset by phase
                star.bright = (star.twinklePhase / 8) % 2 == 0
            }
        }

        // Spawn shooting stars randomly
        if (tickCount % 40 == 0 && shootingStars.size < 2) {
            if (Random.nextFloat() < 0.3f) {
                spawnShootingStar()
            }
        }

        // Update shooting stars
        val iterator = shootingStars.iterator()
        while (iterator.hasNext()) {
            val ss = iterator.next()
            ss.x += ss.speedX
            ss.y += ss.speedY
            ss.life--
            ss.alpha = (255 * ss.life / ss.maxLife).coerceIn(0, 255)
            if (ss.life <= 0 || ss.x > screenWidth + 50 || ss.y > screenHeight + 50) {
                iterator.remove()
            }
        }

        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        if (!initialized || stars.isEmpty()) {
            screenWidth = w
            screenHeight = h
            initStars(w, h)
        }

        // Draw stars by depth layer (far to near)

        // Layer 1: Far stars (1px, dim)
        for (star in stars.filter { it.size <= 1f }) {
            drawStar(canvas, star, withGlow = false)
        }

        // Layer 2: Mid stars (2px)
        for (star in stars.filter { it.size > 1f && it.size <= 2f }) {
            drawStar(canvas, star, withGlow = false)
        }

        // Layer 3: Near stars (3px + glow)
        for (star in stars.filter { it.size > 2f && it.size <= 3f }) {
            drawStar(canvas, star, withGlow = true)
        }

        // Layer 4: Foreground stars (4px + glow)
        for (star in stars.filter { it.size > 3f }) {
            drawStar(canvas, star, withGlow = true)
        }

        // Layer 5: Shooting stars (on top)
        for (ss in shootingStars) {
            drawShootingStar(canvas, ss)
        }
    }

    private fun drawStar(canvas: Canvas, star: Star, withGlow: Boolean) {
        val alpha = if (star.twinkle) {
            if (star.bright) star.alpha else (star.alpha * 0.3f).toInt()
        } else {
            star.alpha
        }

        val r = Color.red(star.color)
        val g = Color.green(star.color)
        val b = Color.blue(star.color)

        // Glow effect for bright stars
        if (withGlow && star.hasGlow && alpha > 150) {
            val glowAlpha = (alpha * 0.25f).toInt()
            glowPaint.color = Color.argb(glowAlpha, r, g, b)
            val glowSize = star.size * 3f
            canvas.drawRect(
                star.x - glowSize / 2, star.currentY - glowSize / 2,
                star.x + star.size + glowSize / 2, star.currentY + star.size + glowSize / 2,
                glowPaint
            )
        }

        // Main star
        paint.color = Color.argb(alpha, r, g, b)
        canvas.drawRect(star.x, star.currentY, star.x + star.size, star.currentY + star.size, paint)
    }

    private fun drawShootingStar(canvas: Canvas, ss: ShootingStar) {
        // Draw streak trail
        val trailPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val segments = 6
        for (i in 0 until segments) {
            val segAlpha = (ss.alpha * (1f - i.toFloat() / segments) * 0.6f).toInt()
            trailPaint.color = Color.argb(segAlpha, 200, 240, 255)
            val segX = ss.x - ss.speedX * i * 0.5f
            val segY = ss.y - ss.speedY * i * 0.5f
            val segSize = 2f - (i * 0.2f)
            canvas.drawRect(segX, segY, segX + segSize, segY + segSize, trailPaint)
        }

        // Draw head
        paint.color = Color.argb(ss.alpha, 255, 255, 255)
        canvas.drawRect(ss.x, ss.y, ss.x + 3f, ss.y + 3f, paint)
    }

    private fun initStars(w: Float, h: Float) {
        stars.clear()
        val rng = Random.Default

        // Depth layer configs: [count, minSize, maxSize, minAlpha, maxAlpha]
        val layerCounts =   intArrayOf(30, 30, 15, 5)
        val layerMinSizes = floatArrayOf(1f, 2f, 3f, 4f)
        val layerMaxSizes = floatArrayOf(1f, 2f, 3f, 4f)
        val layerMinAlpha = intArrayOf(40, 100, 180, 220)
        val layerMaxAlpha = intArrayOf(80, 180, 240, 255)

        for (layer in 0 until layerCounts.size) {
            val count = layerCounts[layer]
            val minSize = layerMinSizes[layer]
            val maxSize = layerMaxSizes[layer]
            val minAlpha = layerMinAlpha[layer]
            val maxAlpha = layerMaxAlpha[layer]
            for (i in 0 until count) {
                val x = rng.nextFloat() * (w - maxSize)
                val startY = rng.nextFloat() * h
                val size = if (minSize == maxSize) minSize else minSize + rng.nextFloat() * (maxSize - minSize)
                val color = neonColors[rng.nextInt(neonColors.size)]
                val alpha = minAlpha + rng.nextInt(maxAlpha - minAlpha)
                val driftSpeed = 0.1f + rng.nextFloat() * 0.15f // 0.1-0.25 px per tick
                val twinkle = rng.nextFloat() < 0.25f // 25% of stars twinkle
                val twinklePhase = rng.nextInt(16)
                val hasGlow = size >= 3f

                stars.add(Star(
                    x = x,
                    currentY = startY,
                    size = size,
                    color = color,
                    alpha = alpha,
                    driftSpeed = driftSpeed,
                    twinkle = twinkle,
                    twinklePhase = twinklePhase,
                    bright = true,
                    hasGlow = hasGlow
                ))
            }
        }

        initialized = true
    }

    private fun spawnShootingStar() {
        val rng = Random.Default
        val startX = rng.nextFloat() * screenWidth * 0.7f
        val startY = rng.nextFloat() * screenHeight * 0.5f
        val speed = 6f + rng.nextFloat() * 4f
        val angle = 0.5f + rng.nextFloat() * 0.3f // ~30-45 degrees

        shootingStars.add(ShootingStar(
            x = startX,
            y = startY,
            speedX = speed * kotlin.math.cos(angle.toDouble()).toFloat(),
            speedY = speed * kotlin.math.sin(angle.toDouble()).toFloat(),
            length = 6f + rng.nextFloat() * 4f,
            alpha = 255,
            life = 12 + rng.nextInt(8),
            maxLife = 20
        ))
    }
}
