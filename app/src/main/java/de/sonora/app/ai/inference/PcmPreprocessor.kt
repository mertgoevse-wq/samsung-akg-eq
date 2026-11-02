package de.sonora.app.ai.inference

/**
 * Korrektes PCM-Preprocessing für On-Device-Audio-ML.
 *
 * Pipeline (in dieser Reihenfolge):
 * 1. [toMono] — Stereo/Multichannel wird auf Mono gemischt (einfache Mittelung).
 * 2. [resample] — Auf Zielsamplerate (YAMNet: 16 kHz).
 *    Achtung: Die aktuelle Implementierung nutzt lineare Interpolation.
 *    Das ist Prototyp-Qualität und ausreichend für erste Evaluationsläufe,
 *    aber nicht anti-aliasiert. Für Produktion sollte ein bandbegrenzter
 *    Resampler (z. B. Sinc / Fenstered-Sinc) nachgeliefert werden.
 * 3. [clampToUnitRange] — Clamp auf [-1, 1] ohne Peak-Normalisierung.
 *    Für YAMNet ist Clamping korrekt, da das Modell auf Float32-PCM in
 *    [-1.0, 1.0] trainiert wurde und die Signal-Dynamik erhalten bleiben soll.
 *    [normalizeToUnitRange] (Peak-Normalisierung) darf NICHT für YAMNet-Input
 *    verwendet werden, da sie leise Signale künstlich aufpegt und damit die
 *    Dynamik verzerrt.
 *
 * Keine der Funktionen verändert die Semantik von NaN/Infinity-Werten der
 * Eingabe; Aufrufer müssen die Eingabe vorab validieren (siehe
 * LiteRtInferenceEngine.Companion.validateInput).
 */
object PcmPreprocessor {

    /**
     * Wandelt mehrkanalige PCM-Samples in Mono um (einfache Mittelung über Kanäle).
     */
    fun toMono(samples: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return samples.copyOf()
        val out = FloatArray(samples.size / channels)
        for (i in out.indices) {
            var acc = 0f
            for (c in 0 until channels) acc += samples[i * channels + c]
            out[i] = acc / channels
        }
        return out
    }

    /**
     * Resamplert von [fromRate] auf [toRate] mittels linearer Interpolation.
     *
     * @return kopierte Samples bei gleicher Rate, ansonsten neu berechnete Samples.
     * @see <prototype-quality> Lineare Interpolation ist kein anti-aliasierter Resampler
     *      und sollte für Produktionsaudio durch einen bandbegrenzten Resampler ersetzt werden.
     */
    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input.copyOf()
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in out.indices) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceIn(0, input.size - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return out
    }

    /**
     * Normalisiert Audio auf den Bereich [-1, 1].
     *
     * Hinweis: Für YAMNet-Input darf hier keine Peak-Normalisierung angewendet werden,
     * da diese die Signal-Dynamik verzerrt. Verwenden Sie stattdessen [clampToUnitRange].
     * Diese Methode bleibt aus Abwärtskompatibilität erhalten und delegiert an
     * [normalizeToUnitRange].
     */
    fun normalize(input: FloatArray): FloatArray {
        return normalizeToUnitRange(input)
    }

    /**
     * Peak-Normalisierung auf [-1, 1].
     *
     * Achtung: Diese Operation kann Signale, die bewusst einen niedrigen Peak
     * gegenüber ihrer Gesamtenergie haben, künstlich aufpegen und damit die
     * Dynamik verzerren. Für YAMNet-Input bevorzugen Sie [clampToUnitRange].
     *
     * @return Kopie der Samples, skaliert sodass der Peak genau 1.0 ist.
     *         Bei einem Peak von 0 (Stille) wird eine unveränderte Kopie zurückgegeben.
     */
    fun normalizeToUnitRange(input: FloatArray): FloatArray {
        var peak = 0f
        for (v in input) peak = maxOf(peak, kotlin.math.abs(v))
        if (peak == 0f) return input.copyOf()
        val scale = 1f / peak
        return FloatArray(input.size) { input[it] * scale }
    }

    /**
     * Clamp auf den Bereich [-1, 1] ohne Peak-Normalisierung.
     *
     * Dies erhält die ursprüngliche Signal-Dynamik und stellt gleichzeitig die
     * Kompatibilität mit Modellen sicher, die begrenzten Float-Input erwarten
     * (z. B. YAMNet Float32). Außerhalb von [-1, 1] liegende Werte werden
     * auf die Grenze geklemmt; Werte innerhalb des Bereichs bleiben unverändert.
     * Dies ist die empfohlene Normalisierung für YAMNet-Modell-Input.
     */
    fun clampToUnitRange(input: FloatArray): FloatArray {
        return FloatArray(input.size) { input[it].coerceIn(-1f, 1f) }
    }

    /**
     * Führt die empfohlene YAMNet-Vorverarbeitung in einem Schritt aus:
     * Mono-Konvertierung → Resampling auf 16 kHz → Clamping auf [-1, 1].
     *
     * Dies ist die bevorzugte Pipeline für YAMNet-Input, da sie
     * [clampToUnitRange] statt Peak-Normalisierung verwendet.
     */
    fun prepareForYamnet(
        samples: FloatArray,
        channels: Int,
        fromRate: Int,
        targetRate: Int = LiteRtInferenceEngine.Companion.YAMNET_SAMPLE_RATE
    ): FloatArray {
        val mono = toMono(samples, channels)
        val resampled = resample(mono, fromRate, targetRate)
        return clampToUnitRange(resampled)
    }
}