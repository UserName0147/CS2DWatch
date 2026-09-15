package com.example.cs2dwatch

import android.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class GameState(private val screenWidth: Float, private val screenHeight: Float) {

    val player = Player(screenWidth / 2f, screenHeight / 2f)
    val enemies = mutableListOf<Enemy>()
    val bullets = mutableListOf<Bullet>()
    val particles = mutableListOf<Particle>()

    var onImpact: (() -> Unit)? = null // Колбэк для вибрации

    var score: Int = 0
    var wave: Int = 1
    var waveEnemiesRemaining: Int = 0
    var gameOver: Boolean = false
    var isSelectingPerk: Boolean = false
    var availablePerks = mutableListOf<Perk>()
    private var spawnTimer: Float = 0f

    private val arenaMargin = 60f

    init {
        startWave()
    }

    private fun startWave() {
        waveEnemiesRemaining = 3 + wave
        spawnTimer = 0f
    }

    fun update(dt: Float, moveDir: Pair<Float, Float>, wantShoot: Boolean) {
        if (gameOver || isSelectingPerk) return

        updatePlayer(dt, moveDir, wantShoot)
        updateEnemies(dt)
        updateBullets(dt)
        updateParticles(dt)
        handleSpawning(dt)
        handleCollisions()

        if (!player.isAlive) {
            gameOver = true
        }
    }

    private fun updatePlayer(dt: Float, moveDir: Pair<Float, Float>, wantShoot: Boolean) {
        val (mx, my) = moveDir
        val len = hypot(mx.toDouble(), my.toDouble()).toFloat()
        
        // Игрок всегда в центре
        player.x = screenWidth / 2f
        player.y = screenHeight / 2f

        if (player.shootCooldown > 0f) player.shootCooldown -= dt

        // Если джойстик нажат, стреляем туда. Иначе — автоприцел (если wantShoot)
        val isJoystickActive = len > 0.1f
        val shouldShoot = isJoystickActive || wantShoot

        if (shouldShoot && player.shootCooldown <= 0f) {
            player.shootCooldown = player.shootDelay
            
            val angle = if (isJoystickActive) {
                // Стреляем по джойстику
                atan2(my, mx)
            } else {
                // Автоприцел (кнопка)
                val target = nearestEnemy()
                if (target != null) {
                    atan2(target.y - player.y, target.x - player.x)
                } else {
                    player.angle
                }
            }
            player.angle = angle
            spawnBullet(player.x, player.y, angle.toFloat(), fromPlayer = true)
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.isAlive) continue

            val dx = player.x - e.x
            val dy = player.y - e.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (e.type == EnemyType.KAMIKAZE) {
                // Камикадзе просто бежит на игрока
                val nx = dx / dist
                val ny = dy / dist
                e.x += nx * e.speed * dt
                e.y += ny * e.speed * dt
                
                if (dist < player.radius + e.radius) {
                    player.health -= 30
                    e.health = 0 // Умирает при взрыве
                    onImpact?.invoke()
                    spawnExplosion(e.x, e.y, Color.rgb(255, 69, 0), 20)
                }
            } else if (e.type == EnemyType.BOSS) {
                // БОСС медленно движется и стреляет веером
                val nx = dx / dist
                val ny = dy / dist
                e.x += nx * e.speed * dt
                e.y += ny * e.speed * dt

                e.shootCooldown -= dt
                if (e.shootCooldown <= 0f) {
                    e.shootCooldown = e.shootDelay
                    
                    // 1. Прицельный дробовик (3 быстрые пули, большой урон)
                    val aimAngle = atan2(dy, dx)
                    spawnBullet(e.x, e.y, aimAngle, fromPlayer = false, customSpeed = 800f, customDamage = 20)
                    spawnBullet(e.x, e.y, aimAngle + 0.2f, fromPlayer = false, customSpeed = 800f, customDamage = 20)
                    spawnBullet(e.x, e.y, aimAngle - 0.2f, fromPlayer = false, customSpeed = 800f, customDamage = 20)

                    // 2. Случайное кольцо пуль вокруг (медленные, чтобы создавать ловушки)
                    val offset = Random.nextFloat() * 0.5f
                    for (i in 0 until 12) {
                        val angle = (i * Math.PI / 6).toFloat() + offset
                        spawnBullet(e.x, e.y, angle, fromPlayer = false, customSpeed = 350f, customDamage = 15)
                    }
                }
            } else {
                // Обычный враг (стрелок)
                if (dist < e.attackRange) {
                    if (dist > e.attackRange * 0.5f) {
                        val nx = dx / dist
                        val ny = dy / dist
                        e.x += nx * e.speed * dt
                        e.y += ny * e.speed * dt
                    }
                    e.shootCooldown -= dt
                    if (e.shootCooldown <= 0f) {
                        e.shootCooldown = e.shootDelay
                        val angle = atan2(dy, dx)
                        spawnBullet(e.x, e.y, angle.toFloat(), fromPlayer = false)
                    }
                } else if (dist < e.visionRange) {
                    val nx = dx / dist
                    val ny = dy / dist
                    e.x += nx * e.speed * dt
                    e.y += ny * e.speed * dt
                }
            }
        }
        enemies.removeAll { !it.isAlive }
    }

    private fun updateBullets(dt: Float) {
        val newBullets = mutableListOf<Bullet>()
        for (b in bullets) {
            b.x += b.dx * dt
            b.y += b.dy * dt
            
            if (player.hasRicochet && b.fromPlayer) {
                if (b.x < 0 || b.x > screenWidth) {
                    newBullets.add(Bullet(b.x, b.y, -b.dx, b.dy, true, b.damage).apply { alive = true })
                    b.alive = false
                } else if (b.y < 0 || b.y > screenHeight) {
                    newBullets.add(Bullet(b.x, b.y, b.dx, -b.dy, true, b.damage).apply { alive = true })
                    b.alive = false
                }
            } else {
                if (b.x < 0 || b.x > screenWidth || b.y < 0 || b.y > screenHeight) {
                    b.alive = false
                }
            }
        }
        bullets.addAll(newBullets)
        bullets.removeAll { !it.alive }
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) p.alive = false
        }
        particles.removeAll { !it.alive }
    }

    private fun spawnExplosion(x: Float, y: Float, color: Int, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 150f + 50f
            particles.add(
                Particle(
                    x, y,
                    cos(angle) * speed,
                    sin(angle) * speed,
                    color,
                    Random.nextFloat() * 0.5f + 0.2f
                )
            )
        }
    }

    private fun handleSpawning(dt: Float) {
        if (waveEnemiesRemaining <= 0 && enemies.isEmpty()) {
            wave += 1
            preparePerks()
            return
        }
        if (waveEnemiesRemaining <= 0) return

        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = 1.2f
            if (wave == 10 && waveEnemiesRemaining == (3 + 10)) {
                spawnBoss()
            } else if (wave != 10) {
                spawnEnemyAtEdge()
            }
            waveEnemiesRemaining -= 1
        }
    }

    private fun spawnBoss() {
        val enemy = Enemy(screenWidth / 2f, -100f, EnemyType.BOSS)
        enemy.health = 500 + wave * 20
        enemies.add(enemy)
        waveEnemiesRemaining = 1 // Босс один на волне
    }

    private fun spawnEnemyAtEdge() {
        val edge = Random.nextInt(4)
        val (x, y) = when (edge) {
            0 -> Random.nextFloat() * screenWidth to 0f
            1 -> Random.nextFloat() * screenWidth to screenHeight
            2 -> 0f to Random.nextFloat() * screenHeight
            else -> screenWidth to Random.nextFloat() * screenHeight
        }
        
        // Каждая третья волна увеличивает шанс появления камикадзе
        val isKamikaze = Random.nextFloat() < (0.15f + wave * 0.05f).coerceAtMost(0.5f)
        val type = if (isKamikaze) EnemyType.KAMIKAZE else EnemyType.NORMAL
        
        val enemy = Enemy(x, y, type)
        enemy.health = (20 + wave * 4) / (if (isKamikaze) 2 else 1)
        enemies.add(enemy)
    }

    private fun spawnBullet(x: Float, y: Float, angle: Float, fromPlayer: Boolean, customSpeed: Float = 640f, customDamage: Int? = null) {
        val speed = customSpeed
        bullets.add(
            Bullet(
                x = x,
                y = y,
                dx = cos(angle) * speed,
                dy = sin(angle) * speed,
                fromPlayer = fromPlayer,
                damage = customDamage ?: if (fromPlayer) player.damage else 8
            )
        )
    }

    private fun handleCollisions() {
        for (b in bullets) {
            if (!b.alive) continue

            if (b.fromPlayer) {
                for (e in enemies) {
                    if (!e.isAlive) continue
                    if (hits(b.x, b.y, b.radius, e.x, e.y, e.radius)) {
                        e.health -= b.damage
                        b.alive = false
                        spawnExplosion(b.x, b.y, Color.YELLOW, 5)
                        if (!e.isAlive) {
                            score += 10
                            player.health = (player.health + player.lifeSteal).coerceAtMost(player.maxHealth)
                            spawnExplosion(e.x, e.y, Color.RED, 15)
                        }
                        break
                    }
                }
            } else {
                if (hits(b.x, b.y, b.radius, player.x, player.y, player.radius)) {
                    player.health -= b.damage
                    b.alive = false
                    onImpact?.invoke() // Вибрация при попадании в игрока
                    spawnExplosion(b.x, b.y, Color.rgb(255, 100, 0), 8)
                }
            }
        }
    }

    private fun hits(x1: Float, y1: Float, r1: Float, x2: Float, y2: Float, r2: Float): Boolean {
        val d = hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()
        return d < r1 + r2
    }

    private fun nearestEnemy(): Enemy? =
        enemies.filter { it.isAlive }
            .minByOrNull { hypot((it.x - player.x).toDouble(), (it.y - player.y).toDouble()) }

    private fun preparePerks() {
        availablePerks.clear()
        
        // Фильтруем перки: Рикошет и Вампиризм убираем, если они уже есть
        val allPerks = Perk.values().toMutableList()
        if (player.hasRicochet) allPerks.remove(Perk.RICOCHET)
        if (player.lifeSteal > 0) allPerks.remove(Perk.VAMPIRE)
        
        allPerks.shuffle()
        availablePerks.addAll(allPerks.take(2))
        isSelectingPerk = true
    }

    fun applyPerk(perk: Perk) {
        when (perk) {
            Perk.RAPID_FIRE -> player.shootDelay *= 0.8f
            Perk.HEALTH -> {
                player.maxHealth += 20
                player.health = player.maxHealth
            }
            Perk.DAMAGE -> player.damage = (player.damage * 1.25f).toInt()
            Perk.RICOCHET -> player.hasRicochet = true
            Perk.VAMPIRE -> player.lifeSteal += 5
        }
        isSelectingPerk = false
        startWave()
    }

    fun restart() {
        player.health = 100
        player.maxHealth = 100
        player.speed = 260f
        player.shootDelay = 0.25f
        player.damage = 12
        player.hasRicochet = false
        player.lifeSteal = 0
        player.x = screenWidth / 2f
        player.y = screenHeight / 2f
        enemies.clear()
        bullets.clear()
        score = 0
        wave = 1
        gameOver = false
        isSelectingPerk = false
        startWave()
    }
}
