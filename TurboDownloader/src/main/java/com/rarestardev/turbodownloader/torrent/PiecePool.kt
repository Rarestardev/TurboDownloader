package com.rarestardev.turbodownloader.torrent

/**
 * Thread-safe pool of torrent pieces shared by all peer sessions.
 */
internal class PiecePool(
    private val pieceCount: Int,
    private val maxAttemptsPerPiece: Int = MAX_ATTEMPTS_PER_PIECE
) {
    companion object {
        const val MAX_ATTEMPTS_PER_PIECE = 6
    }

    /** Result of [claim]. */
    sealed class Claim {
        /** The piece index is reserved for the caller. */
        data class Piece(val index: Int) : Claim()

        /** All remaining pieces are in flight by other peers; retry shortly. */
        object Wait : Claim()

        /** Every piece is downloaded and verified. */
        object Done : Claim()

        /** Every remaining piece exceeded its attempt budget; download must fail. */
        object Exhausted : Claim()
    }

    private val lock = Any()
    private val done = BooleanArray(pieceCount)
    private val inFlight = BooleanArray(pieceCount)
    private val attempts = IntArray(pieceCount)

    @Volatile
    var exhausted: Boolean = false
        private set

    fun claim(): Claim = synchronized(lock) {
        var retriedCandidate = -1
        for (index in 0 until pieceCount) {
            if (done[index] || inFlight[index]) continue
            if (attempts[index] >= maxAttemptsPerPiece) continue
            if (attempts[index] == 0) {
                attempts[index]++
                inFlight[index] = true
                return Claim.Piece(index)
            }
            if (retriedCandidate == -1) retriedCandidate = index
        }

        if (retriedCandidate != -1) {
            attempts[retriedCandidate]++
            inFlight[retriedCandidate] = true
            return Claim.Piece(retriedCandidate)
        }

        val anyUndone = (0 until pieceCount).any { !done[it] }
        if (!anyUndone) return Claim.Done

        val allRemainingExhausted =
            (0 until pieceCount).all { done[it] || attempts[it] >= maxAttemptsPerPiece }
        if (allRemainingExhausted) {
            exhausted = true
            return Claim.Exhausted
        }
        Claim.Wait
    }

    /** Marks a piece as permanently completed (already verified). */
    fun markDone(index: Int) = synchronized(lock) {
        if (index in 0 until pieceCount) {
            done[index] = true
            inFlight[index] = false
        }
    }

    fun complete(index: Int) = markDone(index)

    /** Returns a claimed piece to the pool (peer failed / hash mismatch). */
    fun release(index: Int) = synchronized(lock) {
        if (index in 0 until pieceCount) {
            inFlight[index] = false
        }
    }

    fun completedCount(): Int = synchronized(lock) { (0 until pieceCount).count { done[it] } }

    fun isComplete(): Boolean = synchronized(lock) { (0 until pieceCount).all { done[it] } }
}
