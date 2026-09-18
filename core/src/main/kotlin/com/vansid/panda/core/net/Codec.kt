package com.vansid.panda.core.net

import com.vansid.panda.core.Shot

/** What came out of the wire. */
sealed interface Decoded {
    data class Ok(
        val msg: Msg,
    ) : Decoded

    /**
     * The frame was not usable. It is a value, not an exception: a peer on the far end
     * of a radio link can send anything at all, and a malformed frame must cost a log
     * line, not a crashed match.
     */
    data class Rejected(
        val reason: String,
    ) : Decoded
}

private const val TYPE_HELLO = 0x01
private const val TYPE_MATCH_START = 0x02
private const val TYPE_SHOT = 0x03
private const val TYPE_RESULT = 0x04
private const val TYPE_REMATCH = 0x05
private const val TYPE_BYE = 0x06
private const val TYPE_PING = 0x07
private const val TYPE_RESUME = 0x08
private const val TYPE_HISTORY = 0x09

private const val U8_MAX = 0xFF
private const val U16_MAX = 0xFFFF

/**
 * Binary protocol (§12). Big-endian throughout; byte 0 is the version, byte 1 the type.
 *
 * Fixed-width and hand-rolled on purpose: the frames are a handful of bytes, the format
 * is part of the contract, and a serialisation library would make the wire depend on a
 * dependency's idea of compatibility.
 */
object Codec {
    fun encode(msg: Msg): ByteArray {
        val out = Writer()
        out.u8(PROTOCOL_VERSION.toInt())
        when (msg) {
            is Msg.Hello -> encodeHello(out, msg)

            is Msg.MatchStart -> {
                out.u8(TYPE_MATCH_START)
                out.i64(msg.seed)
                out.u16(msg.width)
                out.u8(msg.startingPlayer)
                out.u8(msg.roundsToWin)
            }

            is Msg.ShotMsg -> {
                out.u8(TYPE_SHOT)
                out.u16(msg.turn)
                out.u8(msg.angle)
                out.u8(msg.power)
            }

            is Msg.Result -> {
                out.u8(TYPE_RESULT)
                out.u16(msg.turn)
                out.u8(msg.outcome.code)
                // Clamped: coordinates are only meaningful where the flight ended inside
                // the canvas, which is exactly the case the receiver verifies.
                out.u16(msg.impactX.coerceIn(0, U16_MAX))
                out.u16(msg.impactY.coerceIn(0, U16_MAX))
            }

            is Msg.Rematch -> out.u8(TYPE_REMATCH)

            is Msg.Bye -> {
                out.u8(TYPE_BYE)
                out.u8(msg.reason.code)
            }

            is Msg.Ping -> {
                out.u8(TYPE_PING)
                out.i64(msg.nonce)
            }

            is Msg.Resume -> {
                out.u8(TYPE_RESUME)
                out.i64(msg.seed)
                out.u16(msg.nextTurn)
            }

            is Msg.History -> encodeHistory(out, msg)
        }
        return out.toByteArray()
    }

    private fun encodeHello(
        out: Writer,
        msg: Msg.Hello,
    ) {
        // A nick longer than 255 bytes does not fit the length field; it is cut, not refused.
        val nick =
            msg.nick
                .toByteArray(Charsets.UTF_8)
                .take(U8_MAX)
                .toByteArray()
        out.u8(TYPE_HELLO)
        out.u8(nick.size)
        out.bytes(nick)
        out.u16(msg.width)
        out.u8(msg.caps)
    }

    private fun encodeHistory(
        out: Writer,
        msg: Msg.History,
    ) {
        out.u8(TYPE_HISTORY)
        out.u16(msg.shots.size)
        for (shot in msg.shots) {
            out.u16(shot.turn)
            out.u8(shot.angle)
            out.u8(shot.power)
        }
    }

    fun decode(bytes: ByteArray): Decoded {
        val r = Reader(bytes)
        if (!r.has(HEADER_BYTES)) return Decoded.Rejected("frame shorter than a header")

        val version = r.u8()
        val type = r.u8()
        return when {
            version != PROTOCOL_VERSION.toInt() -> Decoded.Rejected("unsupported protocol version $version")
            else -> finish(type, decodeBody(type, r), r)
        }
    }

    /** A body is only accepted whole: nothing missing, nothing left over. */
    private fun finish(
        type: Int,
        msg: Msg?,
        r: Reader,
    ): Decoded =
        when {
            msg == null -> Decoded.Rejected("truncated or unknown type $type")
            r.remaining() > 0 -> Decoded.Rejected("${r.remaining()} trailing bytes on type $type")
            else -> Decoded.Ok(msg)
        }

    /**
     * Every branch checks the bytes it needs **before** reading them, so a truncated
     * frame falls out as `null` instead of as a pile of null-checks per field.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun decodeBody(
        type: Int,
        r: Reader,
    ): Msg? =
        when (type) {
            TYPE_HELLO -> decodeHello(r)

            TYPE_MATCH_START ->
                if (!r.has(MATCH_START_BYTES)) null else Msg.MatchStart(r.i64(), r.u16(), r.u8(), r.u8())

            TYPE_SHOT ->
                if (!r.has(SHOT_BYTES)) null else Msg.ShotMsg(r.u16(), r.u8(), r.u8())

            TYPE_RESULT -> decodeResult(r)

            TYPE_REMATCH -> Msg.Rematch

            TYPE_BYE ->
                if (!r.has(BYE_BYTES)) null else Msg.Bye(ByeReason.fromCode(r.u8()))

            TYPE_PING ->
                if (!r.has(PING_BYTES)) null else Msg.Ping(r.i64())

            TYPE_RESUME ->
                if (!r.has(RESUME_BYTES)) null else Msg.Resume(r.i64(), r.u16())

            TYPE_HISTORY -> decodeHistory(r)

            else -> null
        }

    private fun decodeHello(r: Reader): Msg? {
        if (!r.has(1)) return null
        val nickLen = r.u8()
        if (!r.has(nickLen + HELLO_TAIL_BYTES)) return null
        return Msg.Hello(r.utf8(nickLen), r.u16(), r.u8())
    }

    private fun decodeResult(r: Reader): Msg? {
        if (!r.has(RESULT_BYTES)) return null
        val turn = r.u16()
        val outcome = OutcomeCode.fromCode(r.u8()) ?: return null
        return Msg.Result(turn, outcome, r.u16(), r.u16())
    }

    private fun decodeHistory(r: Reader): Msg? {
        if (!r.has(COUNT_BYTES)) return null
        val count = r.u16()
        if (!r.has(count * SHOT_BYTES)) return null
        return Msg.History(List(count) { Shot(r.u16(), r.u8(), r.u8()) })
    }
}

// Body sizes, which are the format itself: each one spells out the fields of §12.
private const val SHOT_BYTES = 4 // u16 turn + u8 angle + u8 power
private const val HELLO_TAIL_BYTES = 3 // u16 width + u8 caps, after the nick
private const val MATCH_START_BYTES = 12 // i64 seed + u16 width + u8 player + u8 rounds
private const val RESULT_BYTES = 7 // u16 turn + u8 outcome + u16 x + u16 y
private const val BYE_BYTES = 1 // u8 reason
private const val PING_BYTES = 8 // i64 nonce
private const val RESUME_BYTES = 10 // i64 seed + u16 turn
private const val COUNT_BYTES = 2 // u16 length prefix
private const val HEADER_BYTES = 2 // u8 version + u8 type

/** Enough for any frame the protocol defines except a long nick or history. */
private const val TYPICAL_FRAME_BYTES = 32

private const val BITS_PER_BYTE = 8
private const val LONG_BYTES = 8

private class Writer {
    private val buffer = ArrayList<Byte>(TYPICAL_FRAME_BYTES)

    fun u8(v: Int) {
        buffer.add((v and U8_MAX).toByte())
    }

    fun u16(v: Int) {
        buffer.add((v ushr BITS_PER_BYTE and U8_MAX).toByte())
        buffer.add((v and U8_MAX).toByte())
    }

    fun i64(v: Long) {
        for (byte in (LONG_BYTES - 1) downTo 0) {
            buffer.add((v ushr (byte * BITS_PER_BYTE) and U8_MAX.toLong()).toByte())
        }
    }

    fun bytes(b: ByteArray) {
        for (x in b) buffer.add(x)
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}

/**
 * Reads big-endian fields. Callers check [has] first; past the end the values are
 * meaningless rather than exceptional, which keeps a malformed frame from throwing.
 */
private class Reader(
    private val bytes: ByteArray,
) {
    private var pos = 0

    fun remaining(): Int = bytes.size - pos

    fun has(n: Int): Boolean = n >= 0 && remaining() >= n

    fun u8(): Int = if (remaining() < 1) 0 else bytes[pos++].toInt() and U8_MAX

    fun u16(): Int = (u8() shl BITS_PER_BYTE) or u8()

    fun i64(): Long {
        var v = 0L
        repeat(LONG_BYTES) { v = (v shl BITS_PER_BYTE) or u8().toLong() }
        return v
    }

    fun utf8(len: Int): String {
        val s = String(bytes, pos, len, Charsets.UTF_8)
        pos += len
        return s
    }
}
