package com.xiaohan.xhsnotegen.ui.publish

import com.google.gson.Gson
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Pure Kotlin port of the x-s signing algorithm from the ReaJason/xhs Python library.
 * No backend dependency — all signing happens on-device.
 */
object XhsSigner {

    // ---- Constants from xhs/help.py ----

    private val ALPHABET = "A4NjFqYu5wPHsO0XTdDgMa2r1ZQocVte9UJBvk6/7=yRnhISGKblCWi+LpfE8xzm3"

    private val LOOKUP = listOf(
        "Z", "m", "s", "e", "r", "b", "B", "o", "H", "Q",
        "t", "N", "P", "+", "w", "O", "c", "z", "a", "/",
        "L", "p", "n", "g", "G", "8", "y", "J", "q", "4",
        "2", "K", "W", "Y", "j", "0", "D", "S", "f", "d",
        "i", "k", "x", "3", "V", "T", "1", "6", "I", "l",
        "U", "A", "F", "M", "9", "7", "h", "E", "C", "v",
        "u", "R", "X", "5",
    )

    private val CRC_TABLE = intArrayOf(
        0, 1996959894, -301047508, -1727442502, 124634137, 1886057615, -379345611,
        -1637575261, 249268274, 2044508324, -522852066, -1747789432, 162941995,
        2125561021, -407360249, -1866523247, 498536548, 1789927666, -205950648,
        -2067906082, 450548861, 1843258603, -187386543, -2083282665, 325883990,
        1684777152, -43845254, -1973040660, 335633487, 1661365465, -99664541,
        -1928851979, 997073096, 1281953886, -715111964, -1570279054, 1006888145,
        1258607687, -770865667, -1526024853, 901097722, 1119000684, -608450090,
        -1396901568, 853044451, 1172266101, -589951537, -1412350631, 651767980,
        1373503546, -925412992, -1076862698, 565507253, 1454621731, -809855591,
        -1195530993, 671266974, 1594198024, -972236366, -1324619484, 795835527,
        1483230225, -1050600021, -1234817731, 1994146192, 31158534, -1731059524,
        -271249366, 1907459465, 112637215, -1614814043, -390540237, 2013776290,
        251722036, -1777751922, -519137256, 2137656763, 141376813, -1855689577,
        -429695999, 1802195444, 476864866, -2056965928, -228458418, 1812370925,
        453092731, -2113342271, -183516073, 1706088902, 314042704, -1950435094,
        -54949764, 1658658271, 366619977, -1932296973, -69972891, 1303535960,
        984961486, -1547960204, -725929758, 1256170817, 1037604311, -1529756563,
        -740887301, 1131014506, 879679996, -1385723834, -847195664, 1141124467,
        855842277, -1442165665, -586318647, 1342533948, 654459306, -1106571248,
        -921952122, 1466479909, 544179635, -1184443383, -832445281, 1591671054,
        702138776, -1328506846, -942167884, 1504918807, 783551873, -1212326853,
        -1061524307, -306674912, -1698712650, 62317068, 1957810842, -355121351,
        -1647151185, 81470997, 1943803523, -480048366, -1805370492, 225274430,
        2053790376, -468791541, -1828061283, 167816743, 2097651377, -267414716,
        -2029476910, 503444072, 1762050814, -144550051, -2140837941, 426522225,
        1852507879, -19653770, -1982649376, 282753626, 1742555852, -105259153,
        -1900089353, 397917763, 1622183637, -690576408, -1580100738, 953729732,
        1340076626, -776247311, -1497606297, 1068828381, 1219638859, -670225446,
        -1358292148, 906185462, 1090812512, -547295293, -1469587628, 829329135,
        1181335161, -882789492, -1134132454, 628085408, 1382605366, -871598187,
        -1156888829, 570562233, 1426400815, -977650754, -1296233688, 733239954,
        1555261956, -1026031705, -1244606671, 752459403, 1541320221, -1687895376,
        -328994266, 1969922972, 40735498, -1677130071, -351390145, 1913087877,
        83908371, -1782625662, -491226604, 2075208622, 213261112, -1831694693,
        -438977731, 2094854071, 198958881, -2032938284, -237706686, 1759359992,
        534414190, -2118246475, -201638509, 1873836001, 414664567, -2012718362,
        -15766928, 1711684554, 285281116, -1889165569, -127750551, 1634467795,
        376229701, -1609899400, -686959890, 1308918612, 956543938, -1486412191,
        -799009033, 1231636301, 1047427035, -1362007478, -640614460, 1088359270,
        936918000, -1447252397, -558129907, 1202900863, 817233897, -1111625188,
        -893730166, 1404277552, 615818150, -1160759803, -841546093, 1423857449,
        601450431, -1285129682, -1000256840, 1567103746, 711928724, -1274298825,
        -1022587233, 1510334235, 755167117,
    )

    // ---- Core signing functions (exact ports of xhs/help.py) ----

    fun sign(uri: String, data: Map<String, Any?>? = null, a1: String = "", b1: String = ""): Map<String, String> {
        val v = System.currentTimeMillis()
        val gson = Gson()
        val dataStr = if (data != null) {
            gson.toJson(data) // Gson produces compact JSON by default
        } else ""
        val rawStr = "${v}test${uri}$dataStr"
        val md5Str = md5(rawStr)
        val xS = h(md5Str)
        val xT = v.toString()

        val common = mapOf(
            "s0" to 5, "s1" to "",
            "x0" to "1", "x1" to "3.2.0", "x2" to "Windows",
            "x3" to "xhs-pc-web", "x4" to "2.3.1",
            "x5" to a1, "x6" to xT, "x7" to xS, "x8" to b1,
            "x9" to mrc(xT + xS).toString(), "x10" to 1,
        )
        val commonJson = gson.toJson(common)
        val encoded = encodeUtf8(commonJson)
        val xsCommon = b64Encode(encoded)

        return mapOf("x-s" to xS, "x-t" to xT, "x-s-common" to xsCommon)
    }

    // ---- h(n) — custom hash encoding ----

    private fun h(n: String): String {
        val d = ALPHABET
        val sb = StringBuilder()
        var i = 0
        while (i < 32) {
            val o = n[i].code
            val g = if (i + 1 < 32) n[i + 1].code else 0
            val h = if (i + 2 < 32) n[i + 2].code else 0
            val x = ((o and 3) shl 4) or (g shr 4)
            val p = ((15 and g) shl 2) or (h shr 6)
            val v = o shr 2
            val b = if (h != 0) h and 63 else 64
            val pp: Int
            val bb: Int
            if (g == 0) {
                pp = 64
                bb = 64
            } else {
                pp = p
                bb = b
            }
            sb.append(d[v])
            sb.append(d[x])
            sb.append(d[pp])
            sb.append(d[bb])
            i += 3
        }
        return sb.toString()
    }

    // ---- mrc(e) — CRC32-like checksum ----

    private fun mrc(e: String): Int {
        var o = -1
        for (n in 0 until 57) {
            o = CRC_TABLE[(o and 255) xor e[n].code] xor rightWithoutSign(o, 8)
        }
        return o xor -1 xor 3988292384.toInt()
    }

    private fun rightWithoutSign(num: Int, bit: Int = 0): Int {
        val unsigned = num.toLong() and 0xFFFFFFFFL
        val shifted = (unsigned shr bit).toInt()
        return shifted
    }

    // ---- b64Encode — custom base64 ----

    private fun b64Encode(e: List<Int>): String {
        val P = e.size
        val W = P % 3
        val U = mutableListOf<String>()
        val z = 16383
        var H = 0
        val Z = P - W
        while (H < Z) {
            val end = if (H + z > Z) Z else H + z
            U.add(encodeChunk(e, H, end))
            H += z
        }
        if (W == 1) {
            val F = e[P - 1]
            U.add(LOOKUP[F shr 2] + LOOKUP[(F shl 4) and 63] + "==")
        } else if (W == 2) {
            val F = (e[P - 2] shl 8) + e[P - 1]
            U.add(LOOKUP[F shr 10] + LOOKUP[63 and (F shr 4)] + LOOKUP[(F shl 2) and 63] + "=")
        }
        return U.joinToString("")
    }

    private fun encodeChunk(e: List<Int>, t: Int, r: Int): String {
        val m = mutableListOf<String>()
        var b = t
        while (b < r) {
            val n = ((16711680 and (e[b] shl 16)) +
                    ((e[b + 1] shl 8) and 65280) +
                    (e[b + 2] and 255))
            m.add(tripletToBase64(n))
            b += 3
        }
        return m.joinToString("")
    }

    private fun tripletToBase64(e: Int): String {
        return (LOOKUP[63 and (e shr 18)] +
                LOOKUP[63 and (e shr 12)] +
                LOOKUP[(e shr 6) and 63] +
                LOOKUP[e and 63])
    }

    // ---- encodeUtf8 — URL-encode to byte list ----

    private fun encodeUtf8(e: String): List<Int> {
        val b = mutableListOf<Int>()
        val m = urlEncode(e)
        var w = 0
        while (w < m.length) {
            val T = m[w]
            if (T == '%') {
                val E = "${m[w + 1]}${m[w + 2]}"
                val S = E.toInt(16)
                b.add(S)
                w += 2
            } else {
                b.add(T.code)
            }
            w += 1
        }
        return b
    }

    private fun urlEncode(s: String): String {
        // Python's urllib.parse.quote with safe='~()*!.\''
        val safe = "~()*!.'"
        val sb = StringBuilder()
        for (ch in s) {
            if (ch.isLetterOrDigit() || ch in safe || ch == '-' || ch == '_') {
                sb.append(ch)
            } else if (ch == ' ') {
                sb.append("%20")
            } else {
                val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                for (byte in bytes) {
                    sb.append('%')
                    sb.append(String.format("%02X", byte))
                }
            }
        }
        return sb.toString()
    }

    // ---- MD5 helper ----

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
