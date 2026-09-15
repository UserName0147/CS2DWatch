package com.example.cs2dwatch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile private var running = false

    private lateinit var state: GameState
    private lateinit var joystick: Joystick

    private var shootPointerId: Int = -1
    private var joystickPointerId: Int = -1
    private var wantShoot = false

    private var screenW = 0f
    private var screenH = 0f

    private var shootBtnX = 0f
    private var shootBtnY = 0f
    private val shootBtnRadius = 62f

    private var lastTime = System.nanoTime()

    private val paintBg = Paint().apply { color = Color.rgb(18, 18, 22) }
    private val paintPlayer = Paint().apply { color = Color.rgb(80, 200, 255); isAntiAlias = true }
    private val paintEnemy = Paint().apply { color = Color.rgb(230, 70, 70); isAntiAlias = true }
    private val paintKamikaze = Paint().apply { color = Color.rgb(255, 140, 0); isAntiAlias = true }
    private val paintBoss = Paint().apply { color = Color.rgb(150, 0, 0); isAntiAlias = true }
    private val paintBulletPlayer = Paint().apply { color = Color.YELLOW; isAntiAlias = true }
    private val paintBulletEnemy = Paint().apply { color = Color.rgb(255, 140, 90); isAntiAlias = true }
    private val paintJoystickBase = Paint().apply { color = Color.argb(80, 255, 255, 255); isAntiAlias = true }
    private val paintJoystickKnob = Paint().apply { color = Color.argb(160, 255, 255, 255); isAntiAlias = true }
    private val paintShootBtn = Paint().apply { color = Color.argb(90, 255, 90, 90); isAntiAlias = true }
    private val paintHud = Paint().apply { color = Color.WHITE; textSize = 26f; isAntiAlias = true }
    private val paintParticle = Paint().apply { isAntiAlias = true }
    private val paintHealthBg = Paint().apply { color = Color.rgb(60, 20, 20) }
    private val paintHealthFg = Paint().apply { color = Color.rgb(60, 200, 90) }
    private val paintGameOver = Paint().apply {
        color = Color.WHITE; textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width.toFloat()
        screenH = height.toFloat()
        state = GameState(screenW, screenH)
        
        state.onImpact = {
            (context as? MainActivity)?.vibrate(60)
        }

        // Увеличиваем радиус джойстика и делаем зоны касания больше
        joystick = Joystick(screenW * 0.28f, screenH * 0.65f, 100f)
        shootBtnX = screenW * 0.75f
        shootBtnY = screenH * 0.65f
        // shootBtnRadius оставляем 62f, но в onTouchEvent увеличим зону захвата

        running = true
        gameThread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        gameThread?.join()
        gameThread = null
    }

    fun pauseGame() {
        running = false
        gameThread?.join()
        gameThread = null
    }

    fun resumeGame() {
        if (running || !::state.isInitialized) return
        running = true
        gameThread = Thread(this).also { it.start() }
    }

    override fun run() {
        lastTime = System.nanoTime()
        val targetFrameNanos = 1_000_000_000L / 60L // Увеличили до 60 FPS

        while (running) {
            val frameStart = System.nanoTime()
            val dt = ((frameStart - lastTime) / 1_000_000_000f).coerceAtMost(0.03f)
            lastTime = frameStart

            if (state.gameOver) {
                if (wantShoot) state.restart()
            } else if (!state.isSelectingPerk) {
                state.update(dt, joystick.direction(), wantShoot)
            }

            drawFrame()

            val frameTime = System.nanoTime() - frameStart
            val sleepNanos = targetFrameNanos - frameTime
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    private fun drawFrame() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            render(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun render(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, screenH, paintBg)

        for (p in state.particles) {
            paintParticle.color = p.color
            paintParticle.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, 3f, paintParticle)
        }

        for (b in state.bullets) {
            canvas.drawCircle(b.x, b.y, b.radius, if (b.fromPlayer) paintBulletPlayer else paintBulletEnemy)
        }
        for (e in state.enemies) {
            val p = when (e.type) {
                EnemyType.KAMIKAZE -> paintKamikaze
                EnemyType.BOSS -> paintBoss
                else -> paintEnemy
            }
            canvas.drawCircle(e.x, e.y, e.radius, p)

            if (e.type == EnemyType.BOSS) {
                // Полоска HP босса
                val bossHpW = 100f
                val bossHpH = 8f
                val hpL = e.x - bossHpW / 2f
                val hpT = e.y - e.radius - 20f
                canvas.drawRect(hpL, hpT, hpL + bossHpW, hpT + bossHpH, paintHealthBg)
                val bossHpFrac = e.health.toFloat() / (500 + state.wave * 20)
                canvas.drawRect(hpL, hpT, hpL + bossHpW * bossHpFrac, hpT + bossHpH, paintHealthFg)
            }
        }
        canvas.drawCircle(state.player.x, state.player.y, state.player.radius, paintPlayer)

        canvas.drawCircle(joystick.centerX, joystick.centerY, joystick.radius, paintJoystickBase)
        val (kx, ky) = joystick.knobPosition()
        canvas.drawCircle(kx, ky, 30f, paintJoystickKnob)

        canvas.drawCircle(shootBtnX, shootBtnY, shootBtnRadius, paintShootBtn)

        val hpWidth = 150f
        val hpLeft = screenW * 0.5f - hpWidth / 2f
        val hpTop = 24f
        canvas.drawRect(hpLeft, hpTop, hpLeft + hpWidth, hpTop + 20f, paintHealthBg)
        val hpFrac = (state.player.health.toFloat() / state.player.maxHealth).coerceIn(0f, 1f)
        canvas.drawRect(hpLeft, hpTop, hpLeft + hpWidth * hpFrac, hpTop + 20f, paintHealthFg)

        paintHud.textAlign = Paint.Align.CENTER
        canvas.drawText("Волна ${state.wave}  Очки ${state.score}", screenW * 0.5f, hpTop + 52f, paintHud)

        if (state.gameOver) {
            canvas.drawText("ИГРА ОКОНЧЕНА", screenW / 2f, screenH / 2f - 16f, paintGameOver)
            canvas.drawText("Коснись, чтобы начать заново", screenW / 2f, screenH / 2f + 24f, paintGameOver)
        }

        if (state.isSelectingPerk) {
            renderPerkSelection(canvas)
        }
    }

    private fun renderPerkSelection(canvas: Canvas) {
        canvas.drawARGB(200, 0, 0, 0)
        paintHud.textAlign = Paint.Align.CENTER
        paintHud.textSize = 28f
        canvas.drawText("ВЫБЕРИ УЛУЧШЕНИЕ", screenW * 0.5f, screenH * 0.22f, paintHud)

        val perks = state.availablePerks
        if (perks.size >= 2) {
            drawPerkButton(canvas, perks[0], screenH * 0.45f)
            drawPerkButton(canvas, perks[1], screenH * 0.72f)
        }
    }

    private fun drawPerkButton(canvas: Canvas, perk: Perk, y: Float) {
        val rectW = screenW * 0.75f
        val rectH = 90f
        val left = (screenW - rectW) / 2f
        val rect = RectF(left, y - rectH / 2f, left + rectW, y + rectH / 2f)

        paintJoystickBase.color = Color.argb(140, 60, 60, 180)
        canvas.drawRoundRect(rect, 20f, 20f, paintJoystickBase)

        paintHud.textSize = 26f
        paintHud.color = Color.WHITE
        canvas.drawText(perk.title, screenW * 0.5f, y - 6f, paintHud)
        paintHud.textSize = 20f
        paintHud.color = Color.LTGRAY
        canvas.drawText(perk.desc, screenW * 0.5f, y + 24f, paintHud)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val pointerId = event.getPointerId(idx)

                // Игнорируем касания слишком близко к левому краю (там системная зона свайпа)
                if (x < screenW * 0.08f && joystickPointerId == -1) return true

                if (state.isSelectingPerk) {
                    checkPerkClick(x, y)
                    return true
                }

                if (state.gameOver) {
                    wantShoot = true
                    return true
                }

                if (dist(x, y, shootBtnX, shootBtnY) < shootBtnRadius + 40f) {
                    if (shootPointerId == -1) {
                        shootPointerId = pointerId
                        wantShoot = true
                    }
                } else {
                    if (joystickPointerId == -1) {
                        joystickPointerId = pointerId
                        joystick.start(x, y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pid == joystickPointerId) {
                        joystick.move(event.getX(i), event.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                if (pid == joystickPointerId) {
                    joystick.stop()
                    joystickPointerId = -1
                }
                if (pid == shootPointerId) {
                    wantShoot = false
                    shootPointerId = -1
                }
                if (state.gameOver) {
                    wantShoot = false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                joystick.stop()
                joystickPointerId = -1
                shootPointerId = -1
                wantShoot = false
            }
        }
        return true
    }

    private fun checkPerkClick(x: Float, y: Float) {
        val perks = state.availablePerks
        if (perks.size < 2) return

        val rectW = screenW * 0.75f
        val rectH = 90f
        val left = (screenW - rectW) / 2f

        // Кнопка 1
        if (x in left..(left + rectW) && y in (screenH * 0.45f - rectH / 2f)..(screenH * 0.45f + rectH / 2f)) {
            state.applyPerk(perks[0])
        }
        // Кнопка 2
        else if (x in left..(left + rectW) && y in (screenH * 0.72f - rectH / 2f)..(screenH * 0.72f + rectH / 2f)) {
            state.applyPerk(perks[1])
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
