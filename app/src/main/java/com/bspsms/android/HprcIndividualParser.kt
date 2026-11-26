package com.bspsms.android

import com.google.mlkit.vision.text.Text
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Result of reading ONE report-card page for ONE selected term.
 *  - Formative : values = the 3 marks (F?A, F?B, F?C) of the chosen phase, allValues = all 9 marks
 *  - Summative : values = the single mark of the chosen term, allValues = marks of the 3 terms
 *  - BCO / LPCD: values = one entry per printed item for the chosen phase ("" when blank / unclear)
 */
data class HprcIndividualReading(
    val ok: Boolean,
    val message: String,
    val values: List<String> = emptyList(),
    val allValues: List<String> = emptyList()
)

/**
 * Reads the handwritten values of one HPRC page using the position of the printed labels
 * (subject names / item names / phase headings) so every value is tied to the right row and column.
 * When anything is unclear the reading FAILS (the teacher rescans) instead of guessing.
 */
object HprcIndividualParser {

    private data class Tok(val text: String, val cx: Float, val cy: Float, val w: Float, val h: Float)

    private val IC = RegexOption.IGNORE_CASE

    private val SUBJECT_LABELS: List<Pair<String, Regex>> = listOf(
        "FIRST" to Regex("1st\\s*lang|first\\s*lang", IC),
        "SECOND" to Regex("2nd\\s*lang|second\\s*lang", IC),
        "MATH" to Regex("mathematic|\\bmaths?\\b", IC),
        "ART" to Regex("art\\s*&|art\\s*and\\s*work|work\\s*edu", IC),
        "HEALTH" to Regex("health", IC)
    )

    private val BCO_ITEMS: List<Regex> = listOf(
        Regex("self\\s*aware", IC),
        Regex("communicat", IC),
        Regex("collaborat", IC),
        Regex("experiential", IC),
        Regex("critical\\s*think", IC),
        Regex("computational|analytical", IC),
        Regex("problem\\s*solving", IC),
        Regex("decision\\s*making", IC),
        Regex("creative\\s*present", IC),
        Regex("aesthetic", IC),
        Regex("teacher.{0,3}s\\s*percep", IC)
    )

    private val LPCD_ITEMS: List<Regex> = listOf(
        Regex("pattern\\s*of\\s*intell", IC),
        Regex("area\\s*of\\s*interest", IC),
        Regex("positive\\s*attitude", IC),
        Regex("exceptional\\s*abil", IC),
        Regex("features\\s*of\\s*anxi", IC),
        Regex("learning\\s*gaps", IC),
        Regex("specific\\s*learning", IC)
    )

    /** Maps a portal / card subject name (English or Bengali) to a stable id, or null when unknown. */
    fun canonicalSubject(text: String): String? {
        val t = text.lowercase(Locale.US)
        return when {
            Regex("1st\\s*lang|first\\s*lang|প্রথম\\s*ভাষা|\\bbengali\\b|বাংলা|\\bbangla\\b").containsMatchIn(t) -> "FIRST"
            Regex("2nd\\s*lang|second\\s*lang|দ্বিতীয়\\s*ভাষা|\\benglish\\b|ইংরেজি").containsMatchIn(t) -> "SECOND"
            Regex("math|গণিত").containsMatchIn(t) -> "MATH"
            Regex("\\bart\\b|work\\s*ed|কলা").containsMatchIn(t) -> "ART"
            Regex("health|physical\\s*ed|স্বাস্থ্য").containsMatchIn(t) -> "HEALTH"
            else -> null
        }
    }

    fun read(visionText: Text, category: String, term: Int, subjectId: String?): HprcIndividualReading {
        val theta = estimateTilt(visionText)
        val lines = ArrayList<Tok>()
        val elems = ArrayList<Tok>()
        collect(visionText, theta, lines, elems)
        if (elems.isEmpty()) {
            return fail("No text found in this photo. Hold the page steady and scan again.")
        }
        val cat = category.lowercase(Locale.US)
        return when (cat) {
            "formative" -> readMarks(lines, elems, true, term, subjectId)
            "summative" -> readMarks(lines, elems, false, term, subjectId)
            "bco" -> readQualitative(lines, elems, term, true)
            "lpcd" -> readQualitative(lines, elems, term, false)
            else -> fail("Open a Formative, Summative, LPCD or BCO marks page in the portal first.")
        }
    }

    private fun fail(msg: String) = HprcIndividualReading(ok = false, message = msg)

    // ---------------------------------------------------------------- geometry helpers

    /** Median angle of the printed text lines = how much the page is tilted in the photo. */
    private fun estimateTilt(visionText: Text): Double {
        val angles = ArrayList<Double>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val cp = line.cornerPoints ?: continue
                if (cp.size < 2) continue
                val dx = (cp[1].x - cp[0].x).toDouble()
                val dy = (cp[1].y - cp[0].y).toDouble()
                if (hypot(dx, dy) < 80.0) continue
                angles.add(atan2(dy, dx))
            }
        }
        if (angles.size < 3) return 0.0
        angles.sort()
        return angles[angles.size / 2]
    }

    private fun collect(visionText: Text, theta: Double, lines: MutableList<Tok>, elems: MutableList<Tok>) {
        val c = cos(theta)
        val s = sin(theta)
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lb = line.boundingBox ?: continue
                val lx = lb.exactCenterX()
                val ly = lb.exactCenterY()
                lines.add(Tok(line.text, (lx * c + ly * s).toFloat(), (-lx * s + ly * c).toFloat(), lb.width().toFloat(), lb.height().toFloat()))
                for (el in line.elements) {
                    val eb = el.boundingBox ?: continue
                    val ex = eb.exactCenterX()
                    val ey = eb.exactCenterY()
                    elems.add(Tok(el.text, (ex * c + ey * s).toFloat(), (-ex * s + ey * c).toFloat(), eb.width().toFloat(), eb.height().toFloat()))
                }
            }
        }
    }

    private fun clusterByY(items: List<Tok>, tol: Float): List<List<Tok>> {
        val sorted = items.sortedBy { it.cy }
        val groups = ArrayList<MutableList<Tok>>()
        for (t in sorted) {
            val g = groups.lastOrNull()
            if (g != null && abs(t.cy - meanY(g)) <= tol) g.add(t) else groups.add(mutableListOf(t))
        }
        return groups
    }

    private fun meanY(g: List<Tok>): Float = g.map { it.cy }.average().toFloat()

    private fun median(values: List<Float>, fallback: Float): Float {
        if (values.isEmpty()) return fallback
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    // ---------------------------------------------------------------- number helpers

    private fun isFullMarksToken(raw: String): Boolean =
        raw.contains('(') || raw.contains(')') || raw.any { it in '\u09E6'..'\u09EF' }

    private fun cleanNumber(raw: String): String? {
        val t = raw.trim().trim { !it.isLetterOrDigit() }
        if (t.length !in 1..4) return null
        if (t.none { it.isDigit() }) return null
        val sb = StringBuilder()
        for (ch in t) {
            sb.append(
                when (ch) {
                    'O', 'o' -> '0'
                    'l', 'I' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    else -> ch
                }
            )
        }
        val mapped = sb.toString()
        return if (Regex("^\\d{1,3}(?:\\.\\d)?$").matches(mapped)) mapped else null
    }

    // ---------------------------------------------------------------- Formative / Summative

    private fun readMarks(lines: List<Tok>, elems: List<Tok>, formative: Boolean, term: Int, subjectId: String?): HprcIndividualReading {
        if (subjectId.isNullOrBlank()) {
            return fail("Choose the subject in the popup (the portal subject was not recognised).")
        }
        val candidates = elems.filter { isFullMarksToken(it.text) || cleanNumber(it.text) != null }
        if (candidates.isEmpty()) return fail("No marks found. Keep the marks table fully in view and scan again.")

        val tol = maxOf(14f, median(candidates.map { it.h }, 30f) * 0.6f)
        // Printed full-marks rows such as "10(১০)" are never student marks
        val markGroups = clusterByY(candidates, tol)
            .filter { g -> g.none { isFullMarksToken(it.text) } }
            .map { g -> g.sortedBy { it.cx } }
        if (markGroups.isEmpty()) return fail("No handwritten marks found. Scan again.")

        val anchors = ArrayList<Pair<String, Tok>>()
        for (ln in lines) {
            for ((id, rx) in SUBJECT_LABELS) {
                if (rx.containsMatchIn(ln.text)) {
                    anchors.add(id to ln)
                    break
                }
            }
        }
        if (anchors.isEmpty()) return fail("Subject names are not visible. Keep the subject column in view and scan again.")

        // Pair every printed subject label with the nearest handwritten row (each used once)
        val pairs = ArrayList<Triple<Float, Int, Int>>()
        for (ai in anchors.indices) {
            for (gi in markGroups.indices) {
                pairs.add(Triple(abs(anchors[ai].second.cy - meanY(markGroups[gi])), ai, gi))
            }
        }
        pairs.sortBy { it.first }
        val maxDist = maxOf(40f, median(anchors.map { it.second.h }, 30f) * 2.5f)
        val usedA = HashSet<Int>()
        val usedG = HashSet<Int>()
        val assigned = ArrayList<Pair<String, List<Tok>>>()
        for ((dist, ai, gi) in pairs) {
            if (dist > maxDist) break
            if (ai in usedA || gi in usedG) continue
            usedA.add(ai)
            usedG.add(gi)
            assigned.add(anchors[ai].first to markGroups[gi])
        }

        val headS = lines.firstOrNull { Regex("summative\\s*eval", IC).containsMatchIn(it.text) }
        fun isFormativeRow(g: List<Tok>): Boolean =
            if (headS != null) meanY(g) < headS.cy else g.size >= 5

        val row = assigned.firstOrNull { it.first == subjectId && isFormativeRow(it.second) == formative }?.second
            ?: return fail("This subject's row was not found. Keep the whole table in view and scan again.")

        val nums = row.mapNotNull { cleanNumber(it.text) }
        val small = subjectId == "ART" || subjectId == "HEALTH"

        if (formative) {
            if (nums.size != 9) {
                return fail("Read ${nums.size} of 9 marks for this subject. Keep the page flat and steady, then scan again.")
            }
            val maxF = if (small) 10.0 else 20.0
            for (n in nums) {
                if ((n.toDoubleOrNull() ?: 999.0) > maxF) {
                    return fail("The value \"$n\" looks wrong (maximum is ${maxF.toInt()}). Scan again.")
                }
            }
            val start = (term - 1) * 3
            return HprcIndividualReading(true, "OK", nums.subList(start, start + 3), nums)
        }

        if (nums.size < 3) {
            return fail("Read ${nums.size} of 3 marks for this subject. Keep the page flat and steady, then scan again.")
        }
        val three = nums.take(3)
        val maxS = if (small) listOf(10.0, 15.0, 25.0) else listOf(20.0, 30.0, 50.0)
        for (i in 0 until 3) {
            if ((three[i].toDoubleOrNull() ?: 999.0) > maxS[i]) {
                return fail("The value \"${three[i]}\" looks wrong (maximum is ${maxS[i].toInt()}). Scan again.")
            }
        }
        return HprcIndividualReading(true, "OK", listOf(three[term - 1]), three)
    }

    // ---------------------------------------------------------------- BCO (grades) and LPCD (handwritten words)

    private fun readQualitative(lines: List<Tok>, elems: List<Tok>, term: Int, gradesOnly: Boolean): HprcIndividualReading {
        val items = if (gradesOnly) BCO_ITEMS else LPCD_ITEMS
        val anchorOf = arrayOfNulls<Tok>(items.size)
        for (i in items.indices) {
            anchorOf[i] = lines.filter { items[i].containsMatchIn(it.text) }.minByOrNull { it.cy }
        }
        val found = items.indices.filter { anchorOf[it] != null }
        if (found.size < 3) {
            return fail("Item names are not visible. Keep the whole table in view and scan again.")
        }
        val first = found.first()
        val last = found.last()
        val pitch = if (last > first) (anchorOf[last]!!.cy - anchorOf[first]!!.cy) / (last - first) else 100f
        if (pitch <= 10f) return fail("Could not read the table rows. Scan again.")

        // Column positions from the printed "1st Phase / 2nd Phase / 3rd Phase" headings
        val colCx = arrayOfNulls<Float>(3)
        val phaseRx = listOf(Regex("1st\\s*phase", IC), Regex("2nd\\s*phase", IC), Regex("3rd\\s*phase", IC))
        for (k in 0 until 3) {
            colCx[k] = lines.firstOrNull { phaseRx[k].containsMatchIn(it.text) }?.cx
        }
        val haveCols = colCx.all { it != null }
        fun colOf(cx: Float): Int {
            var best = 0
            var bestD = Float.MAX_VALUE
            for (k in 0 until 3) {
                val d = abs(cx - (colCx[k] ?: 0f))
                if (d < bestD) { bestD = d; best = k }
            }
            return best
        }
        fun rowOf(cy: Float, tolerance: Float): Int {
            var best = -1
            var bestD = Float.MAX_VALUE
            for (i in found) {
                val d = abs(cy - anchorOf[i]!!.cy)
                if (d < bestD) { bestD = d; best = i }
            }
            return if (bestD <= tolerance) best else -1
        }

        val values = MutableList(items.size) { "" }

        if (gradesOnly) {
            val grades = elems.filter { Regex("^[A-D]$").matches(it.text.trim()) }
            val perRow = HashMap<Int, MutableList<Tok>>()
            for (g in grades) {
                val r = rowOf(g.cy, pitch * 0.65f)
                if (r >= 0) perRow.getOrPut(r) { mutableListOf() }.add(g)
            }
            for ((r, toks) in perRow) {
                val sorted = toks.sortedBy { it.cx }
                if (haveCols) {
                    val mine = sorted.filter { colOf(it.cx) == term - 1 }
                    if (mine.size == 1) values[r] = mine[0].text.trim()
                } else if (sorted.size == 3) {
                    values[r] = sorted[term - 1].text.trim()
                }
            }
        } else {
            val labelRight = found.maxOf { anchorOf[it]!!.cx + anchorOf[it]!!.w / 2f }
            val topLimit = anchorOf[first]!!.cy - pitch * 0.6f
            val header = Regex("phase|formative|items|identified|condition|domain|perspective", IC)
            val written = lines.filter { ln ->
                ln.cx - ln.w / 2f >= labelRight - 15f &&
                    ln.cy >= topLimit &&
                    !header.containsMatchIn(ln.text) &&
                    ln.text.none { it in '\u0980'..'\u09FF' }
            }
            val maxRight = written.maxOfOrNull { it.cx + it.w / 2f } ?: (labelRight + 300f)
            val third = (maxRight - labelRight) / 3f
            val perRow = HashMap<Int, MutableList<Tok>>()
            for (ln in written) {
                val r = rowOf(ln.cy, pitch * 0.8f)
                if (r < 0) continue
                val col = if (haveCols) colOf(ln.cx) else ((ln.cx - labelRight) / third).toInt().coerceIn(0, 2)
                if (col == term - 1) perRow.getOrPut(r) { mutableListOf() }.add(ln)
            }
            for ((r, toks) in perRow) {
                values[r] = toks.sortedWith(compareBy({ it.cy }, { it.cx }))
                    .joinToString(" ") { it.text.trim() }
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(60)
            }
        }

        if (values.all { it.isBlank() }) {
            return fail("Nothing readable in the selected phase column. Make sure the page is flat and well lit, then scan again.")
        }
        return HprcIndividualReading(true, "OK", values, values)
    }
}
