package de.sonora.app.core

import java.security.SecureRandom
import java.util.Collections
import java.util.Random

enum class BlindOrder { SONORA_FIRST, ORIGINAL_FIRST }

class BlindTest(
    private val rounds: Int = 10,
    orderSeed: Long? = null
) {
    init { require(rounds > 0) }

    private val random = orderSeed?.let(::Random) ?: SecureRandom()
    private val orders = mutableListOf<BlindOrder>().apply {
        repeat(rounds / 2) {
            add(BlindOrder.SONORA_FIRST)
            add(BlindOrder.ORIGINAL_FIRST)
        }
        if (rounds % 2 == 1) add(if (random.nextBoolean()) BlindOrder.SONORA_FIRST else BlindOrder.ORIGINAL_FIRST)
        Collections.shuffle(this, random)
    }.toList()
    private val sonoraRatings = mutableListOf<Int>()
    private val originalRatings = mutableListOf<Int>()

    fun nextOrder(roundIndex: Int): BlindOrder {
        require(roundIndex in 0 until rounds)
        return orders[roundIndex]
    }

    /** A is the first segment, B the second; the mapping to the sound condition stays hidden. */
    fun recordPair(ratingA: Int, ratingB: Int) {
        require(ratingA in 1..10 && ratingB in 1..10)
        check(sonoraRatings.size < rounds) { "Alle Runden sind bereits bewertet." }
        when (orders[sonoraRatings.size]) {
            BlindOrder.SONORA_FIRST -> {
                sonoraRatings += ratingA
                originalRatings += ratingB
            }
            BlindOrder.ORIGINAL_FIRST -> {
                originalRatings += ratingA
                sonoraRatings += ratingB
            }
        }
    }

    /** Sonora's mean is available only after all randomized pairs have been rated. */
    fun results(): List<Int> = sonoraRatings.toList()
    fun originalResults(): List<Int> = originalRatings.toList()
    fun average(): Double? = sonoraRatings.takeIf { it.size == rounds }?.average()
    fun originalAverage(): Double? = originalRatings.takeIf { it.size == rounds }?.average()
    fun isComplete(completed: Int = sonoraRatings.size): Boolean = completed >= rounds
    fun totalRounds(): Int = rounds
    fun ratedPairs(): Int = sonoraRatings.size
}
