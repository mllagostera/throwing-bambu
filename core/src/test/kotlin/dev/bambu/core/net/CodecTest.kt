package dev.bambu.core.net

import dev.bambu.core.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §15.10: encoding and decoding every message returns the original object. */
class CodecTest {
    @Test
    fun everyMessageSurvivesARoundTrip() {
        for (msg in samples()) {
            val decoded = Codec.decode(Codec.encode(msg))
            assertTrue("$msg was rejected: $decoded", decoded is Decoded.Ok)
            assertEquals(msg, (decoded as Decoded.Ok).msg)
        }
    }

    @Test
    fun edgeValuesSurviveToo() {
        val edges =
            listOf(
                Msg.Hello(nick = "", width = 320, caps = 0),
                Msg.Hello(nick = "á".repeat(120), width = 460, caps = 255),
                Msg.ShotMsg(turn = 65535, angle = 0, power = 1),
                Msg.ShotMsg(turn = 0, angle = 90, power = 100),
                Msg.MatchStart(seed = Long.MIN_VALUE, width = 320, startingPlayer = 1, roundsToWin = 255),
                Msg.MatchStart(seed = Long.MAX_VALUE, width = 460, startingPlayer = 0, roundsToWin = 1),
                Msg.Ping(nonce = Long.MIN_VALUE),
                Msg.Resume(seed = -1L, nextTurn = 65535),
                Msg.History(emptyList()),
            )
        for (msg in edges) {
            val decoded = Codec.decode(Codec.encode(msg))
            assertEquals("edge case lost in transit: $msg", Decoded.Ok(msg), decoded)
        }
    }

    @Test
    fun everyOutcomeCodeSurvives() {
        for (code in OutcomeCode.entries) {
            val msg = Msg.Result(turn = 7, outcome = code, impactX = 123, impactY = 45)
            assertEquals(Decoded.Ok(msg), Codec.decode(Codec.encode(msg)))
        }
    }

    /** A7: a malformed frame is a rejected value, never an exception. */
    @Test
    fun aWrongVersionIsRejected() {
        val frame = Codec.encode(Msg.Rematch)
        frame[0] = 0x02
        val decoded = Codec.decode(frame)
        assertTrue(decoded is Decoded.Rejected)
        assertTrue("$decoded", (decoded as Decoded.Rejected).reason.contains("version"))
    }

    @Test
    fun truncatedFramesAreRejected() {
        for (msg in samples()) {
            val full = Codec.encode(msg)
            for (cut in 0 until full.size) {
                val decoded = Codec.decode(full.copyOf(cut))
                assertTrue(
                    "a $cut-byte prefix of $msg decoded as if it were whole",
                    decoded is Decoded.Rejected,
                )
            }
        }
    }

    @Test
    fun trailingGarbageIsRejected() {
        val frame = Codec.encode(Msg.ShotMsg(3, 45, 50)) + byteArrayOf(0x00)
        assertTrue(Codec.decode(frame) is Decoded.Rejected)
    }

    @Test
    fun unknownTypesAreRejected() {
        val frame = byteArrayOf(PROTOCOL_VERSION, 0x7F)
        assertTrue(Codec.decode(frame) is Decoded.Rejected)
    }

    @Test
    fun emptyFramesAreRejected() {
        assertTrue(Codec.decode(ByteArray(0)) is Decoded.Rejected)
    }

    @Test
    fun framesAreBigEndian() {
        // Byte order is contract: the other device may not share this one's architecture.
        val frame = Codec.encode(Msg.ShotMsg(turn = 0x0102, angle = 45, power = 50))
        assertEquals(PROTOCOL_VERSION, frame[0])
        assertEquals(0x03, frame[1].toInt())
        assertEquals(0x01, frame[2].toInt())
        assertEquals(0x02, frame[3].toInt())
    }

    private fun samples(): List<Msg> =
        listOf(
            Msg.Hello(nick = "panda", width = 400, caps = 1),
            Msg.MatchStart(seed = 20260913L, width = 383, startingPlayer = 1, roundsToWin = 3),
            Msg.ShotMsg(turn = 12, angle = 45, power = 68),
            Msg.Result(turn = 12, outcome = OutcomeCode.HIT_TERRAIN, impactX = 350, impactY = 128),
            Msg.Rematch,
            Msg.Bye(ByeReason.TURN_TIMEOUT),
            Msg.Ping(nonce = 0x0123456789ABCDEFL),
            Msg.Resume(seed = 42L, nextTurn = 9),
            Msg.History(listOf(Shot(0, 45, 50), Shot(1, 30, 80), Shot(2, 90, 20))),
        )
}
