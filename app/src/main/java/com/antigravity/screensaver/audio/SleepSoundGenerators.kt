package com.antigravity.screensaver.audio

import com.antigravity.screensaver.model.SleepSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time, sample-by-sample procedural sound generators.
 *
 * Every sound is synthesized from random noise + simple filters, so no audio files are
 * bundled with the app (zero APK size cost) and there are never loop seams.
 * Each generator returns samples roughly in the range [-1, 1].
 */
interface SoundGenerator {
    fun next(): Float
}

object SoundGenerators {
    const val SAMPLE_RATE = 44100

    fun create(sound: SleepSound, random: Random = Random(System.nanoTime())): SoundGenerator =
        when (sound) {
            SleepSound.BROWN -> BrownNoiseGenerator(random)
            SleepSound.PINK -> PinkNoiseGenerator(random)
            SleepSound.WHITE -> WhiteNoiseGenerator(random)
            SleepSound.RAIN -> RainGenerator(random)
            SleepSound.OCEAN -> OceanWavesGenerator(random)
            SleepSound.FAN -> FanGenerator(random)
        }

    /** One-pole low-pass smoothing coefficient for a cutoff frequency in Hz. */
    fun onePoleCoefficient(cutoffHz: Float): Float =
        (1.0 - exp(-2.0 * PI * cutoffHz / SAMPLE_RATE)).toFloat()
}

private fun Random.white(): Float = nextFloat() * 2f - 1f

/** One-pole low-pass filter. */
private class LowPass(cutoffHz: Float) {
    var coefficient = SoundGenerators.onePoleCoefficient(cutoffHz)
    private var state = 0f
    fun process(x: Float): Float {
        state += coefficient * (x - state)
        return state
    }
}

/** One-pole high-pass filter (input minus its low-passed copy). */
private class HighPass(cutoffHz: Float) {
    private val lp = LowPass(cutoffHz)
    fun process(x: Float): Float = x - lp.process(x)
}

/** Deep, soft rumble (6 dB/octave roll-off). Identical character to the original brown noise. */
class BrownNoiseGenerator(private val random: Random) : SoundGenerator {
    private var last = 0f
    override fun next(): Float {
        last = last * 0.985f + random.white() * 0.08f
        return last
    }
}

/** Balanced "even" noise (3 dB/octave), using Paul Kellet's refined pink filter. */
class PinkNoiseGenerator(private val random: Random) : SoundGenerator {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f

    override fun next(): Float {
        val w = random.white()
        b0 = 0.99886f * b0 + w * 0.0555179f
        b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f
        b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f
        b5 = -0.7616f * b5 - w * 0.0168980f
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
        b6 = w * 0.115926f
        return pink * 0.11f
    }
}

/** Bright, flat-spectrum hiss. Kept quieter because it is perceived as much louder. */
class WhiteNoiseGenerator(private val random: Random) : SoundGenerator {
    override fun next(): Float = random.white() * 0.22f
}

/**
 * Steady rainfall: band-limited pink noise "wash" with slowly drifting intensity,
 * plus randomly scattered short droplet ticks.
 */
class RainGenerator(private val random: Random) : SoundGenerator {
    private val pink = PinkNoiseGenerator(random)
    private val washHighPass = HighPass(350f)
    private val washLowPass = LowPass(7000f)
    private val dropHighPass = HighPass(2500f)

    // Slow intensity drift (random walk, heavily smoothed)
    private var intensityTarget = 0.85f
    private var intensity = 0.85f
    private var samplesUntilNewTarget = 0

    // Droplet envelope
    private var dropEnv = 0f
    private var dropDecay = 0.999f
    private val dropsPerSecond = 45f

    override fun next(): Float {
        if (samplesUntilNewTarget <= 0) {
            intensityTarget = 0.65f + random.nextFloat() * 0.35f
            samplesUntilNewTarget = SoundGenerators.SAMPLE_RATE * (3 + random.nextInt(5))
        }
        samplesUntilNewTarget--
        intensity += (intensityTarget - intensity) * 0.00002f

        val wash = washLowPass.process(washHighPass.process(pink.next())) * 1.6f * intensity

        if (random.nextFloat() < dropsPerSecond / SoundGenerators.SAMPLE_RATE) {
            dropEnv = 0.08f + random.nextFloat() * 0.22f
            // 2–8 ms decay time constant
            val tauSamples = SoundGenerators.SAMPLE_RATE * (0.002f + random.nextFloat() * 0.006f)
            dropDecay = exp(-1.0 / tauSamples).toFloat()
        }
        val drop = dropHighPass.process(random.white()) * dropEnv
        dropEnv *= dropDecay

        return wash + drop
    }
}

/**
 * Ocean surf: low-passed brown/pink noise whose loudness and brightness swell and recede
 * like waves rolling in (randomized 7–13 s cycles, quick rise, long wash-out).
 */
class OceanWavesGenerator(private val random: Random) : SoundGenerator {
    private val brown = BrownNoiseGenerator(random)
    private val pink = PinkNoiseGenerator(random)
    private val surfLowPass = LowPass(400f)

    private var phase = 0f
    private var phaseStep = newPhaseStep()
    private var waveStrength = 0.8f

    private fun newPhaseStep(): Float {
        val periodSeconds = 7f + random.nextFloat() * 6f
        return 1f / (periodSeconds * SoundGenerators.SAMPLE_RATE)
    }

    private var sampleCounter = 0

    override fun next(): Float {
        phase += phaseStep
        if (phase >= 1f) {
            phase -= 1f
            phaseStep = newPhaseStep()
            waveStrength = 0.6f + random.nextFloat() * 0.4f
        }

        // Asymmetric swell: rise over first 35% of the cycle, slow decay afterward
        val swell = if (phase < 0.35f) {
            val t = phase / 0.35f
            t * t * (3f - 2f * t)
        } else {
            val t = (phase - 0.35f) / 0.65f
            val d = 1f - t
            d * d
        }
        val envelope = 0.18f + 0.82f * swell * waveStrength

        // Update filter brightness every 64 samples (cheap): darker in troughs, brighter at crest
        if (sampleCounter++ and 63 == 0) {
            surfLowPass.coefficient = SoundGenerators.onePoleCoefficient(250f + 1400f * swell * waveStrength)
        }

        val source = brown.next() * 0.9f + pink.next() * 0.9f
        return surfLowPass.process(source) * envelope * 1.5f
    }
}

/**
 * Bedroom fan: steady low-passed noise "whoosh" with a faint motor/blade hum
 * that wobbles very slightly.
 */
class FanGenerator(private val random: Random) : SoundGenerator {
    private val brown = BrownNoiseGenerator(random)
    private val pink = PinkNoiseGenerator(random)
    private val airLowPass = LowPass(900f)

    private var humPhase = 0.0
    private var wobblePhase = 0.0
    private val humFrequency = 92.0
    private val wobbleFrequency = 0.35

    override fun next(): Float {
        val air = airLowPass.process(brown.next() * 0.8f + pink.next() * 0.9f) * 1.4f

        humPhase += 2.0 * PI * humFrequency / SoundGenerators.SAMPLE_RATE
        if (humPhase > 2.0 * PI) humPhase -= 2.0 * PI
        wobblePhase += 2.0 * PI * wobbleFrequency / SoundGenerators.SAMPLE_RATE
        if (wobblePhase > 2.0 * PI) wobblePhase -= 2.0 * PI

        val wobble = 1.0 + 0.12 * sin(wobblePhase)
        val hum = (0.014 * sin(humPhase) + 0.007 * sin(2.0 * humPhase)) * wobble

        return (air + hum.toFloat())
    }
}
