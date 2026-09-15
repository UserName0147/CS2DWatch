package com.example.cs2dwatch

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Виртуальный джойстик. "Плавающий" — появляется там, где палец коснулся экрана,
 * это удобнее на маленьком экране часов, чем фиксированная позиция.
 */
class Joystick(var centerX: Float, var centerY: Float, val radius: Float) {

    private var knobX: Float = centerX
    private var knobY: Float = centerY
    var active: Boolean = false
        private set

    fun start(x: Float, y: Float) {
        active = true
        centerX = x
        centerY = y
        knobX = x
        knobY = y
    }

    fun move(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val dist = sqrt(dx * dx + dy * dy)
        
        if (dist > radius) {
            // Центр джойстика «едет» за пальцем, если мы вышли за радиус.
            // Это позволяет не терять контроль на маленьком экране.
            val angle = atan2(dy, dx)
            centerX = x - cos(angle) * radius
            centerY = y - sin(angle) * radius
            knobX = x
            knobY = y
        } else {
            knobX = x
            knobY = y
        }
    }

    fun stop() {
        active = false
        // Не сбрасываем knobX/Y к центру при остановке, 
        // чтобы не было резких рывков игрока при отпускании.
    }

    /** Направление движения: -1..1 по каждой оси с небольшой мертвой зоной */
    fun direction(): Pair<Float, Float> {
        if (!active) return 0f to 0f
        val dx = (knobX - centerX) / radius
        val dy = (knobY - centerY) / radius
        val mag = sqrt(dx * dx + dy * dy)
        if (mag < 0.15f) return 0f to 0f // Мертвая зона
        return dx to dy
    }

    fun knobPosition(): Pair<Float, Float> = knobX to knobY
}
