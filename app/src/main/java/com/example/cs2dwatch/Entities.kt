package com.example.cs2dwatch

/** Игрок */
class Player(var x: Float, var y: Float) {
    var angle: Float = 0f
    var health: Int = 100
    var maxHealth: Int = 100
    val radius: Float = 22f
    var speed: Float = 260f
    var shootCooldown: Float = 0f
    var shootDelay: Float = 0.25f
    var damage: Int = 12
    var hasRicochet: Boolean = false
    var lifeSteal: Int = 0

    val isAlive: Boolean get() = health > 0
}

enum class Perk(val title: String, val desc: String) {
    RAPID_FIRE("Скорострел", "-20% задержка"),
    HEALTH("Аптечка", "Full HP +20 Max"),
    DAMAGE("Убойность", "+25% урон"),
    RICOCHET("Рикошет", "Пули отскакивают"),
    VAMPIRE("Вампир", "HP за убийство")
}

/** Враг-бот */
class Enemy(var x: Float, var y: Float, val type: EnemyType = EnemyType.NORMAL) {
    var health: Int = 30
    var radius: Float = 20f
    var speed: Float = 110f
    var shootCooldown: Float = (0.5f..1.5f).random()
    var shootDelay: Float = 1.4f
    val visionRange: Float = 620f
    val attackRange: Float = 420f

    val isAlive: Boolean get() = health > 0

    init {
        when (type) {
            EnemyType.KAMIKAZE -> {
                health = 15
                speed = 220f
                radius = 18f
            }
            EnemyType.BOSS -> {
                health = 4000
                speed = 95f
                radius = 60f
                shootDelay = 0.3f
            }
            else -> {}
        }
    }
}

enum class EnemyType { NORMAL, KAMIKAZE, BOSS }

/** Пуля */
class Bullet(
    var x: Float,
    var y: Float,
    val dx: Float,
    val dy: Float,
    val fromPlayer: Boolean,
    val damage: Int = 10
) {
    val radius: Float = 5f
    var alive: Boolean = true
}

/** Частица для эффектов */
class Particle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Int,
    var life: Float // время жизни в секундах
) {
    val maxLife = life
    var alive = true
}

// Случайное число с плавающей точкой в диапазоне (используется для разброса задержки стрельбы ботов)
private fun ClosedFloatingPointRange<Float>.random(): Float =
    start + (endInclusive - start) * kotlin.random.Random.nextFloat()
