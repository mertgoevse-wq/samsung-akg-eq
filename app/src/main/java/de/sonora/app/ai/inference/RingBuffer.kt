package de.sonora.app.ai.inference

/**
 * Ringpuffer für überlappende Audioanalysefenster.
 *
 * Thread-Modell: **Single-Producer / Single-Consumer (SPSC)**.
 * - Ein einziger Produzent schreibt ([write]).
 * - Ein einziger Konsument liest ([readLatest], [available]).
 *
 * Alle öffentlichen Methoden sind synchronisiert, sodass der Puffer auch bei
 * versehentlicher Multi-Producer/Multi-Consumer-Nutzung keine inkonsistenten
 * Zustände erzeugt. [clear()] ist mit den [readLatest]-Invarianten
 * abgesprochen: nach [clear()] meldet [available()] 0 und [readLatest] liefert
 * ein leeres Array, sodass ein Konsument niemals "alte" Daten nach einem Reset
 * ausliest.
 */
class RingBuffer(capacity: Int) {
    init {
        require(capacity > 0) { "RingBuffer capacity must be positive, got $capacity" }
    }

    private val buf = FloatArray(capacity)
    private val cap = capacity
    private var writePos = 0
    private var count = 0

    /**
     * Schreibt [samples] in den Puffer. Bei Überlauf werden ältere Samples
     * überschrieben (ring-Verhalten).
     *
     * Thread-sicher: SPSC garantiert; bei mehreren Produzenten wird der Zugriff
     * serialisiert, allerdings ohne Garantie für atomare Chunk-Grenzen.
     */
    @Synchronized
    fun write(samples: FloatArray) {
        for (s in samples) {
            buf[writePos] = s
            writePos = (writePos + 1) % cap
            if (count < cap) count++
        }
    }

    /**
     * Liefert die [n] neuesten Samples als Kopie (ältestes zuerst).
     * Bei weniger als [n> verfügbaren Samples werden nur die vorhandenen
     * zurückgegeben.
     *
     * Thread-sicher: SPSC garantiert.
     */
    @Synchronized
    fun readLatest(n: Int): FloatArray {
        require(n >= 0) { "readLatest count must be non-negative, got $n" }
        val size = minOf(n, count)
        val out = FloatArray(size)
        // Start bei der ältesten der gewünschten n Positionen.
        var pos = (writePos - size + cap) % cap
        for (i in 0 until size) {
            out[i] = buf[pos]
            pos = (pos + 1) % cap
        }
        return out
    }

    /**
     * Anzahl aktuell im Puffer verfügbarer Samples (0 … cap).
     */
    @Synchronized
    fun available(): Int = count

    /**
     * Setzt den Pufer zurück. Nach [clear()] meldet [available()] 0 und
     * [readLatest] liefert ein leeres Array. Die Synchronisation stellt sicher,
     * dass ein laufender [readLatest]-Aufruf nicht mit einem zurückgesetzten
     * Zähler kollidiert (keine "alten" Daten nach dem Reset).
     */
    @Synchronized
    fun clear() {
        writePos = 0
        count = 0
    }

    /** Kapazität des Puffers (in Samples). */
    fun capacity(): Int = cap
}
