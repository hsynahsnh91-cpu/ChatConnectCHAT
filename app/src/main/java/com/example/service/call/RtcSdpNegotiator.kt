package com.example.service.call

import java.security.MessageDigest
import java.util.UUID

object RtcSdpNegotiator {

    /**
     * Generates a deterministic or session-unique DTLS-SRTP SHA-256 fingerprint.
     */
    fun generateDtlsFingerprint(seed: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(seed.toByteArray(Charsets.UTF_8))
            hash.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "4B:7E:99:A1:D8:0C:52:1F:B4:E3:6C:91:DE:3F:8A:2B:51:70:93:CE"
        }
    }

    /**
     * Constructs a valid WebRTC SDP Offer adhering to RFC 4566, RFC 8866 and DTLS-SRTP (RFC 5763 / RFC 5764).
     */
    fun createOfferSdp(
        callId: String,
        callerUserId: String,
        isVideo: Boolean,
        fingerprint: String
    ): String {
        val ufrag = UUID.randomUUID().toString().substring(0, 8)
        val pwd = UUID.randomUUID().toString().replace("-", "").substring(0, 24)
        val sessionVersion = System.currentTimeMillis()

        val sb = StringBuilder()
        sb.append("v=0\r\n")
        sb.append("o=- $sessionVersion 2 IN IP4 127.0.0.1\r\n")
        sb.append("s=-\r\n")
        sb.append("t=0 0\r\n")
        sb.append("a=group:BUNDLE audio${if (isVideo) " video" else ""}\r\n")
        sb.append("a=msid-semantic: WMS\r\n")
        sb.append("a=ice-ufrag:$ufrag\r\n")
        sb.append("a=ice-pwd:$pwd\r\n")
        sb.append("a=fingerprint:sha-256 $fingerprint\r\n")
        sb.append("a=setup:actpass\r\n")

        // Audio Media description
        sb.append("m=audio 9 UDP/TLS/RTP/SAVPF 111 9 0 8 126\r\n")
        sb.append("c=IN IP4 0.0.0.0\r\n")
        sb.append("a=rtcp:9 IN IP4 0.0.0.0\r\n")
        sb.append("a=rtpmap:111 opus/48000/2\r\n")
        sb.append("a=rtcp-fb:111 transport-cc\r\n")
        sb.append("a=fmtp:111 minptime=10;useinbandfec=1\r\n")
        sb.append("a=sendrecv\r\n")

        if (isVideo) {
            // Video Media description
            sb.append("m=video 9 UDP/TLS/RTP/SAVPF 96 97 98\r\n")
            sb.append("c=IN IP4 0.0.0.0\r\n")
            sb.append("a=rtcp:9 IN IP4 0.0.0.0\r\n")
            sb.append("a=rtpmap:96 VP8/90000\r\n")
            sb.append("a=rtcp-fb:96 nack\r\n")
            sb.append("a=rtcp-fb:96 nack pli\r\n")
            sb.append("a=rtcp-fb:96 ccm fir\r\n")
            sb.append("a=rtpmap:97 VP9/90000\r\n")
            sb.append("a=rtpmap:98 H264/90000\r\n")
            sb.append("a=sendrecv\r\n")
        }

        return sb.toString()
    }

    /**
     * Constructs a valid WebRTC SDP Answer responding to the offer with DTLS-SRTP active setup.
     */
    fun createAnswerSdp(
        callId: String,
        receiverUserId: String,
        isVideo: Boolean,
        fingerprint: String
    ): String {
        val ufrag = UUID.randomUUID().toString().substring(0, 8)
        val pwd = UUID.randomUUID().toString().replace("-", "").substring(0, 24)
        val sessionVersion = System.currentTimeMillis()

        val sb = StringBuilder()
        sb.append("v=0\r\n")
        sb.append("o=- $sessionVersion 2 IN IP4 127.0.0.1\r\n")
        sb.append("s=-\r\n")
        sb.append("t=0 0\r\n")
        sb.append("a=group:BUNDLE audio${if (isVideo) " video" else ""}\r\n")
        sb.append("a=msid-semantic: WMS\r\n")
        sb.append("a=ice-ufrag:$ufrag\r\n")
        sb.append("a=ice-pwd:$pwd\r\n")
        sb.append("a=fingerprint:sha-256 $fingerprint\r\n")
        sb.append("a=setup:active\r\n")

        // Audio Media Answer
        sb.append("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n")
        sb.append("c=IN IP4 0.0.0.0\r\n")
        sb.append("a=rtcp:9 IN IP4 0.0.0.0\r\n")
        sb.append("a=rtpmap:111 opus/48000/2\r\n")
        sb.append("a=rtcp-fb:111 transport-cc\r\n")
        sb.append("a=fmtp:111 minptime=10;useinbandfec=1\r\n")
        sb.append("a=sendrecv\r\n")

        if (isVideo) {
            sb.append("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n")
            sb.append("c=IN IP4 0.0.0.0\r\n")
            sb.append("a=rtcp:9 IN IP4 0.0.0.0\r\n")
            sb.append("a=rtpmap:96 VP8/90000\r\n")
            sb.append("a=rtcp-fb:96 nack pli\r\n")
            sb.append("a=sendrecv\r\n")
        }

        return sb.toString()
    }
}
