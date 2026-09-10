package com.asnidev.trailkeeperoffgrid.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * A tiny GPX 1.1 reader/writer. GPX is the lingua franca for handing a route
 * or a trail to another app or another phone — the interoperability layer an
 * app with no server needs. No dependency: `<trk>`/`<trkseg>`/`<trkpt>` and
 * `<rte>`/`<rtept>` only, which is all Trailkeeper Offgrid produces or needs.
 */
object Gpx {
    data class Pt(val lat: Double, val lon: Double, val ele: Double?, val time: String?)
    data class Segment(val name: String, val kind: Kind, val points: List<Pt>)

    enum class Kind { TRACK, ROUTE }

    // ---- write --------------------------------------------------------

    fun document(segments: List<Segment>, author: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append(
            """<gpx version="1.1" creator="Trailkeeper Offgrid" """ +
                """xmlns="http://www.topografix.com/GPX/1/1">""",
        ).append('\n')
        append("  <metadata><author><name>").append(esc(author)).append("</name></author></metadata>\n")
        for (s in segments) {
            when (s.kind) {
                Kind.TRACK -> {
                    append("  <trk><name>").append(esc(s.name)).append("</name><trkseg>\n")
                    s.points.forEach { append(pt("trkpt", it)) }
                    append("  </trkseg></trk>\n")
                }
                Kind.ROUTE -> {
                    append("  <rte><name>").append(esc(s.name)).append("</name>\n")
                    s.points.forEach { append(pt("rtept", it)) }
                    append("  </rte>\n")
                }
            }
        }
        append("</gpx>\n")
    }

    private fun pt(tag: String, p: Pt): String = buildString {
        append("    <").append(tag).append(" lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
        p.ele?.let { append("<ele>").append(it).append("</ele>") }
        p.time?.let { append("<time>").append(esc(it)).append("</time>") }
        append("</").append(tag).append(">\n")
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---- read --------------------------------------------------------

    fun parse(xml: String): List<Segment> {
        val out = ArrayList<Segment>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var event = parser.eventType
        var curName = ""
        var curKind: Kind? = null
        var pts = ArrayList<Pt>()
        var text = StringBuilder()
        var pendingLat = 0.0
        var pendingLon = 0.0
        var pendingEle: Double? = null
        var pendingTime: String? = null
        var inPt = false

        fun flush() {
            curKind?.let { k ->
                if (pts.isNotEmpty()) out.add(Segment(curName.ifBlank { k.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, k, pts.toList()))
            }
            curName = ""; curKind = null; pts = ArrayList()
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name.lowercase()) {
                        "trk" -> { flush(); curKind = Kind.TRACK }
                        "rte" -> { flush(); curKind = Kind.ROUTE }
                        "trkpt", "rtept" -> {
                            inPt = true
                            pendingLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            pendingLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            pendingEle = null; pendingTime = null
                        }
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "name" -> if (curKind != null && !inPt && curName.isBlank()) curName = text.toString().trim()
                        "ele" -> if (inPt) pendingEle = text.toString().trim().toDoubleOrNull()
                        "time" -> if (inPt) pendingTime = text.toString().trim().ifBlank { null }
                        "trkpt", "rtept" -> {
                            pts.add(Pt(pendingLat, pendingLon, pendingEle, pendingTime))
                            inPt = false
                        }
                        "trk", "rte" -> flush()
                    }
                    text = StringBuilder()
                }
            }
            event = parser.next()
        }
        flush()
        return out
    }
}
