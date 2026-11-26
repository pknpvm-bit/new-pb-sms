package com.bspsms.android

import com.google.mlkit.vision.text.Text
import java.security.MessageDigest
import java.util.Locale

data class HprcParsedPageResult(
    val pageSignature: String,
    val detectedPageNumber: Int?,
    val detectedSections: Set<String>,
    val studentName: String,
    val rollNo: String,
    val studentCode: String,
    val className: String,
    val section: String,
    val academicYear: String,
    val medium: String,
    val formativeMarks: Map<String, String>,
    val summativeMarks: Map<String, String>,
    val lpcdResponses: Map<String, String>,
    val bcoResponses: Map<String, String>,
    val remarks: String,
    val uncertainFields: List<HprcUncertainField>
) {
    val hasUsefulData: Boolean
        get() = studentName.isNotBlank() ||
            rollNo.isNotBlank() ||
            studentCode.isNotBlank() ||
            formativeMarks.isNotEmpty() ||
            summativeMarks.isNotEmpty() ||
            lpcdResponses.isNotEmpty() ||
            bcoResponses.isNotEmpty() ||
            remarks.isNotBlank() ||
            uncertainFields.isNotEmpty()
}

object HprcOcrParser {

    private val SUBJECT_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("\\bbengali\\b|\\b1st\\s*lang(?:uage)?\\b|\\bfirst\\s*lang(?:uage)?\\b|\\bben\\b|বাংলা", RegexOption.IGNORE_CASE) to "Bengali",
        Regex("\\benglish\\b|\\b2nd\\s*lang(?:uage)?\\b|\\bsecond\\s*lang(?:uage)?\\b|\\beng\\b", RegexOption.IGNORE_CASE) to "English",
        Regex("environment\\s*(?:&|and)\\s*geo|\\bgeography\\b", RegexOption.IGNORE_CASE) to "Geography",
        Regex("environment\\s*(?:&|and)\\s*hist|\\bhistory\\b", RegexOption.IGNORE_CASE) to "History",
        Regex("\\bour\\s*env\\w*|\\benvironment\\b|\\bevs\\b|\\bparibesh\\b|পরিবেশ", RegexOption.IGNORE_CASE) to "Our Environment",
        Regex("\\bphysical\\s*sci\\w*|\\bphys\\.?\\s*sci\\w*", RegexOption.IGNORE_CASE) to "Physical Science",
        Regex("\\blife\\s*sci\\w*", RegexOption.IGNORE_CASE) to "Life Science",
        Regex("\\bscience\\b", RegexOption.IGNORE_CASE) to "Science",
        Regex("\\bmath(?:s|ematics)?\\b|\\bganit\\b|\\barithmetic\\b|গণিত", RegexOption.IGNORE_CASE) to "Mathematics",
        Regex("\\bhealth\\b|\\bphysical\\s*ed\\w*|\\bswasthya\\b", RegexOption.IGNORE_CASE) to "Health & Physical Ed.",
        Regex("\\bart\\b|\\bwork\\s*ed\\w*|\\bdrawing\\b|\\bcraft\\b|\\bmusic\\b", RegexOption.IGNORE_CASE) to "Art & Work Ed.",
        Regex("\\bsanskrit\\b", RegexOption.IGNORE_CASE) to "Sanskrit",
        Regex("\\bhindi\\b", RegexOption.IGNORE_CASE) to "Hindi",
        Regex("\\bcomputer\\b|\\bict\\b", RegexOption.IGNORE_CASE) to "Computer",
        Regex("\\bability\\s*to\\s*communicat\\w*", RegexOption.IGNORE_CASE) to "Ability to Communicate",
        Regex("\\bability\\s*to\\s*correlat\\w*", RegexOption.IGNORE_CASE) to "Ability to Correlate",
        Regex("\\bproblem\\s*solv\\w*", RegexOption.IGNORE_CASE) to "Problem Solving"
    )

    private val FORMATIVE_INDICATORS: List<Pair<Regex, String>> = listOf(
        Regex("participation|participat", RegexOption.IGNORE_CASE) to "Participation",
        Regex("question|experiment|inquiry|curiosity", RegexOption.IGNORE_CASE) to "Questioning & Experimentation",
        Regex("interpret|applicat|understand", RegexOption.IGNORE_CASE) to "Interpretation & Application",
        Regex("empathy|co[-\\s]?operat|teamwork", RegexOption.IGNORE_CASE) to "Empathy & Cooperation",
        Regex("aesthetic|creative|expression", RegexOption.IGNORE_CASE) to "Aesthetic & Creative Expression"
    )

    private val LPCD_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("literacy|reading|writing|listen", RegexOption.IGNORE_CASE) to "Literacy & Comprehension",
        Regex("problem|critical|reason", RegexOption.IGNORE_CASE) to "Problem Solving",
        Regex("co[-\\s]?operat|collaborat|team", RegexOption.IGNORE_CASE) to "Cooperation & Sharing",
        Regex("discipline|punctual|regular|clean|hygiene", RegexOption.IGNORE_CASE) to "Discipline & Cleanliness",
        Regex("creativ|curios|initiative", RegexOption.IGNORE_CASE) to "Creativity & Curiosity"
    )

    private val BCO_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("part\\s*[-:]?\\s*a|self\\s*awareness|emotional|social\\s*skill", RegexOption.IGNORE_CASE) to "Part A (Social & Emotional)",
        Regex("part\\s*[-:]?\\s*b|co[-\\s]?curricular|sports|cultural|library|club", RegexOption.IGNORE_CASE) to "Part B (Co-Curricular)",
        Regex("attendance|working\\s*days|present", RegexOption.IGNORE_CASE) to "Attendance Observation",
        Regex("conduct|behaviour|behavior|attitude", RegexOption.IGNORE_CASE) to "Behaviour & Conduct"
    )

    fun normalizeDigits(input: String): String {
        val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = bn.indexOf(ch)
            if (idx >= 0) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    fun parseMlKitText(
        visionText: Text,
        currentStudentLocalId: String,
        currentStudentLabel: String
    ): HprcParsedPageResult {
        val rawFull = visionText.text.orEmpty()
        val normalizedFull = normalizeDigits(rawFull)

        // Rebuild spatially ordered horizontal lines using bounding boxes so multi-column tables stay row-aligned
        val lineEntries = visionText.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val txt = normalizeDigits(line.text).replace(Regex("\\s+"), " ").trim()
                if (txt.isBlank()) null else Triple(line.boundingBox?.top ?: 0, line.boundingBox?.left ?: 0, txt)
            }
            .sortedWith(compareBy<Triple<Int, Int, String>> { it.first / 26 }.thenBy { it.second })

        val mergedRows = mutableListOf<String>()
        var currentBand = Int.MIN_VALUE
        val currentRowParts = mutableListOf<String>()
        for ((top, _, txt) in lineEntries) {
            if (currentBand == Int.MIN_VALUE || kotlin.math.abs(top - currentBand) <= 22) {
                if (currentBand == Int.MIN_VALUE) currentBand = top
                currentRowParts.add(txt)
            } else {
                mergedRows.add(currentRowParts.joinToString(" | "))
                currentRowParts.clear()
                currentRowParts.add(txt)
                currentBand = top
            }
        }
        if (currentRowParts.isNotEmpty()) mergedRows.add(currentRowParts.joinToString(" | "))

        val sections = linkedSetOf<String>()
        if (Regex("summative", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) sections.add("Summative")
        if (Regex("formative", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) sections.add("Formative")
        if (Regex("\\blpcd\\b|literacy.*problem|qualitative\\s+indicator", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) {
            sections.add("LPCD")
        }
        if (Regex("\\bbco\\b|co[-\\s]?curricular|part\\s*[-:]?\\s*a.*part\\s*[-:]?\\s*b", RegexOption.IGNORE_CASE).containsMatchIn(normalizedFull)) {
            sections.add("BCO")
        }

        // Student metadata extraction (father/mother/school names are never accepted as the student name)
        val studentName = extractStudentName(normalizedFull)

        val rollNo = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?<![A-Za-z])Roll\\s*(?:No\\.?|Number|#)?\\s*[:\\-]?\\s*([0-9]{1,3})(?![0-9])", RegexOption.IGNORE_CASE),
                Regex("(?<![A-Za-z])Roll(?![A-Za-z])\\s*[:\\-]\\s*([0-9A-Za-z\\-]{1,8})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val studentCode = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Student\\s*(?:Code|ID)|BSP\\s*ID|Unique\\s*ID)\\s*[:\\-]\\s*([0-9]{6,18})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val className = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("\\bClass\\b\\s*[:\\-]\\s*([IVX]{1,4}|PP|Pre[-\\s]?Primary|[0-9]{1,2})", RegexOption.IGNORE_CASE)
            )
        ).trim().uppercase(Locale.US)

        val section = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("\\bSection\\b\\s*[:\\-]\\s*([A-Za-z0-9]{1,4})\\b", RegexOption.IGNORE_CASE)
            )
        ).trim().uppercase(Locale.US)

        val academicYear = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Academic\\s*Year|Session)\\s*[:\\-]\\s*(20\\d{2})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val medium = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("\\bMedium\\b\\s*[:\\-]\\s*(Bengali|English|Hindi|Urdu|Nepali|Santhali)", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val detectedPageNumber = extractRegexGroup(
            normalizedFull,
            listOf(Regex("\\bPage\\s*[:\\-#]?\\s*([1-6])\\b", RegexOption.IGNORE_CASE))
        ).toIntOrNull()

        val summativeMarks = LinkedHashMap<String, String>()
        val formativeMarks = LinkedHashMap<String, String>()
        val lpcdResponses = LinkedHashMap<String, String>()
        val bcoResponses = LinkedHashMap<String, String>()
        val uncertainFields = mutableListOf<HprcUncertainField>()

        // Zone is tracked row by row (headings switch the zone) instead of once per page
        var zone: String? = if (sections.size == 1) sections.first() else null
        val metaLabelRow = Regex(
            "\\b(?:student(?:'s)?\\s*name|name|roll(?:\\s*(?:no|number))?|class|section|session|academic\\s*year|medium|father|mother|guardian|d\\.?o\\.?b|date\\s*of\\s*birth|school|udise|student\\s*(?:code|id))\\b\\s*[:\\-]",
            RegexOption.IGNORE_CASE
        )

        for (rowText in mergedRows) {
            val cleanRow = rowText.replace("|", " ").replace(Regex("\\s+"), " ").trim()
            if (cleanRow.length < 3) continue
            if (Regex("grand\\s*total|full\\s*marks|\\bf\\.?m\\.?\\b|signature|headmaster", RegexOption.IGNORE_CASE).containsMatchIn(cleanRow)) {
                continue
            }

            val lowerRow = cleanRow.lowercase(Locale.US)
            when {
                Regex("\\bsummative\\b").containsMatchIn(lowerRow) -> zone = "Summative"
                Regex("\\bformative\\b").containsMatchIn(lowerRow) -> zone = "Formative"
                Regex("\\blpcd\\b|qualitative\\s+indicator").containsMatchIn(lowerRow) -> zone = "LPCD"
                Regex("\\bbco\\b|co[-\\s]?curricular").containsMatchIn(lowerRow) -> zone = "BCO"
            }
            // Student detail rows (Name:, Roll:, Class:, Father:, ...) never contain marks
            if (metaLabelRow.containsMatchIn(cleanRow)) continue

            val summativeOk = zone == null || zone == "Summative"
            val formativeOk = zone == null || zone == "Formative"
            val lpcdOk = zone == null || zone == "LPCD"
            val bcoOk = zone == null || zone == "BCO"
            var recorded = false

            // 1. Subject row (Summative numeric marks or Formative grades)
            val matchedSubjPair = SUBJECT_PATTERNS.firstOrNull { it.first.containsMatchIn(cleanRow) }
            if (matchedSubjPair != null) {
                val matchedSubj = matchedSubjPair.second
                val tail = cleanRow.replace(matchedSubjPair.first, " ").trim()
                val rawTokens = tail.split(Regex("\\s+")).filter { it.isNotBlank() }

                val validNums = mutableListOf<String>()
                val uncertainTokens = mutableListOf<String>()
                val gradeTokens = mutableListOf<String>()

                for (tok in rawTokens) {
                    val cleanTok = tok.trim(',', ';', ':', '.', '(', ')', '[', ']')
                    if (cleanTok.isBlank()) continue
                    when {
                        Regex("^\\d{1,2}(?:\\.\\d)?$").matches(cleanTok) -> {
                            val v = cleanTok.toDoubleOrNull()
                            if (v != null && v <= 100.0) {
                                validNums.add(cleanTok)
                            } else {
                                uncertainTokens.add(cleanTok)
                            }
                        }
                        // Grades are printed in capitals; lowercase letters are ordinary words
                        Regex("^[A-D][+]?$").matches(cleanTok) -> {
                            gradeTokens.add(cleanTok)
                        }
                        // Typical OCR confusion like 1O, l8, O5, I2, 2O, ?
                        Regex("^(?:[0-9]+[OIlSBG]|[OIl]+[0-9]+|\\?+|--\\?|\\d{3,})$").matches(cleanTok) -> {
                            uncertainTokens.add(cleanTok)
                        }
                    }
                }

                // Do NOT guess when ambiguous tokens appear on a subject row
                if (uncertainTokens.isNotEmpty() && summativeOk) {
                    uncertainFields.add(
                        HprcUncertainField(
                            studentLocalId = currentStudentLocalId,
                            studentLabel = currentStudentLabel,
                            section = "Summative",
                            fieldKey = "$matchedSubj|T1",
                            rawOcrText = cleanRow,
                            candidateValue = validNums.firstOrNull() ?: "",
                            reason = "Unclear OCR token(s): ${uncertainTokens.joinToString(", ")}"
                        )
                    )
                }

                if (validNums.isNotEmpty() && summativeOk) {
                    // Filter out repeated F.M. pairs if 6+ numbers like [20, 17, 30, 26, 50, 44]
                    val obtNums = if (validNums.size >= 6 &&
                        validNums[0] in setOf("15", "20") &&
                        validNums[2] in setOf("25", "30") &&
                        validNums[4] in setOf("50", "70")
                    ) {
                        listOfNotNull(validNums.getOrNull(1), validNums.getOrNull(3), validNums.getOrNull(5), validNums.getOrNull(6))
                    } else {
                        validNums
                    }
                    obtNums.getOrNull(0)?.let { summativeMarks["$matchedSubj|T1"] = it }
                    obtNums.getOrNull(1)?.let { summativeMarks["$matchedSubj|T2"] = it }
                    obtNums.getOrNull(2)?.let { summativeMarks["$matchedSubj|T3"] = it }
                    obtNums.getOrNull(3)?.let { summativeMarks["$matchedSubj|TOTAL"] = it }
                    sections.add("Summative")
                    recorded = true
                } else if (gradeTokens.isNotEmpty() && formativeOk) {
                    gradeTokens.getOrNull(0)?.let { formativeMarks["$matchedSubj|T1"] = it }
                    gradeTokens.getOrNull(1)?.let { formativeMarks["$matchedSubj|T2"] = it }
                    gradeTokens.getOrNull(2)?.let { formativeMarks["$matchedSubj|T3"] = it }
                    sections.add("Formative")
                    recorded = true
                }
            }
            // A row that already produced a subject value must not also feed other sections
            if (recorded) continue

            // 2. Formative indicators
            val matchedFormPair = FORMATIVE_INDICATORS.firstOrNull { it.first.containsMatchIn(cleanRow) }
            if (matchedFormPair != null && formativeOk) {
                val matchedForm = matchedFormPair.second
                val tokens = extractGradesOrMarks(stripLabel(cleanRow, matchedFormPair.first))
                if (tokens.isNotEmpty()) {
                    tokens.getOrNull(0)?.let { formativeMarks["$matchedForm|T1"] = it }
                    tokens.getOrNull(1)?.let { formativeMarks["$matchedForm|T2"] = it }
                    tokens.getOrNull(2)?.let { formativeMarks["$matchedForm|T3"] = it }
                    sections.add("Formative")
                    continue
                } else if (zone == "Formative") {
                    uncertainFields.add(
                        HprcUncertainField(
                            studentLocalId = currentStudentLocalId,
                            studentLabel = currentStudentLabel,
                            section = "Formative",
                            fieldKey = "$matchedForm|T1",
                            rawOcrText = cleanRow,
                            reason = "Indicator found without clear A/B/C/D grade or numeric score"
                        )
                    )
                }
            }

            // 3. LPCD items
            val matchedLpcdPair = LPCD_PATTERNS.firstOrNull { it.first.containsMatchIn(cleanRow) }
            if (matchedLpcdPair != null && lpcdOk) {
                val matchedLpcd = matchedLpcdPair.second
                val labelFree = stripLabel(cleanRow, matchedLpcdPair.first)
                val tokens = extractGradesOrMarks(labelFree)
                if (tokens.isNotEmpty()) {
                    tokens.getOrNull(0)?.let { lpcdResponses["$matchedLpcd|T1"] = it }
                    tokens.getOrNull(1)?.let { lpcdResponses["$matchedLpcd|T2"] = it }
                    tokens.getOrNull(2)?.let { lpcdResponses["$matchedLpcd|T3"] = it }
                    sections.add("LPCD")
                    continue
                }
                val m = Regex("\\b(yes|no|good|satisfactory|needs\\s+improvement)\\b", RegexOption.IGNORE_CASE).find(labelFree)
                if (m != null) {
                    lpcdResponses["$matchedLpcd|T1"] = m.value.uppercase(Locale.US)
                    sections.add("LPCD")
                    continue
                }
            }

            // 4. BCO items
            val matchedBcoPair = BCO_PATTERNS.firstOrNull { it.first.containsMatchIn(cleanRow) }
            if (matchedBcoPair != null && bcoOk) {
                val matchedBco = matchedBcoPair.second
                val labelFree = stripLabel(cleanRow, matchedBcoPair.first)
                val tokens = extractGradesOrMarks(labelFree)
                if (tokens.isNotEmpty()) {
                    tokens.getOrNull(0)?.let { bcoResponses["$matchedBco|T1"] = it }
                    tokens.getOrNull(1)?.let { bcoResponses["$matchedBco|T2"] = it }
                    tokens.getOrNull(2)?.let { bcoResponses["$matchedBco|T3"] = it }
                    sections.add("BCO")
                    continue
                }
                val yn = Regex("(?<![A-Za-z0-9])(?:[Yy]es|[Nn]o|YES|NO)(?![A-Za-z0-9])").find(labelFree)?.value
                if (yn != null) {
                    bcoResponses["$matchedBco|T1"] = yn.uppercase(Locale.US)
                    sections.add("BCO")
                    continue
                }
            }
        }

        val remarks = extractRegexGroup(
            normalizedFull,
            listOf(
                Regex("(?:Teacher(?:'s)?\\s*Remarks?|General\\s*Remarks?|Observation)\\s*[:\\-]\\s*([^\n\r]{4,140})", RegexOption.IGNORE_CASE)
            )
        ).trim()

        val sigInput = "$studentName|$rollNo|$detectedPageNumber|${summativeMarks.entries}|${formativeMarks.entries}|${lpcdResponses.entries}|${bcoResponses.entries}"
        val signature = sha256Short(sigInput)

        return HprcParsedPageResult(
            pageSignature = signature,
            detectedPageNumber = detectedPageNumber,
            detectedSections = sections,
            studentName = studentName,
            rollNo = rollNo,
            studentCode = studentCode,
            className = className,
            section = section,
            academicYear = academicYear,
            medium = medium,
            formativeMarks = formativeMarks,
            summativeMarks = summativeMarks,
            lpcdResponses = lpcdResponses,
            bcoResponses = bcoResponses,
            remarks = remarks,
            uncertainFields = uncertainFields
        )
    }

    private fun extractRegexGroup(text: String, patterns: List<Regex>): String {
        for (rx in patterns) {
            val m = rx.find(text)
            if (m != null && m.groupValues.size > 1) {
                val valStr = m.groupValues[1].trim()
                if (valStr.isNotBlank()) return valStr
            }
        }
        return ""
    }

    private val NAME_EXCLUDE_LINE = Regex(
        "father|mother|guardian|parent|school|teacher|head\\s*master|headmistress|principal",
        RegexOption.IGNORE_CASE
    )
    private val NAME_CUT = Regex(
        "(?i)\\b(?:father|mother|guardian|roll|class|section|session|medium|dob|date|code)\\b"
    )

    private fun cutName(raw: String): String =
        raw.split(NAME_CUT)[0].trim().trimEnd('.', '-', ':', ' ')

    /** Student name only: lines about father/mother/guardian/school/teacher are ignored. */
    private fun extractStudentName(fullText: String): String {
        val lines = fullText.split('\n', '\r').map { it.trim() }.filter { it.isNotBlank() }
        val primary = Regex(
            "(?:Student(?:'s|\u2019s)?\\s*Name|Name\\s*of\\s*(?:the\\s*)?Student)\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .'\u2019]{2,48})",
            RegexOption.IGNORE_CASE
        )
        val secondary = Regex(
            "(?<![\\w'\u2019])Name\\s*[:\\-]\\s*([A-Za-z][A-Za-z .'\u2019]{2,48})",
            RegexOption.IGNORE_CASE
        )
        for (line in lines) {
            val m = primary.find(line) ?: continue
            val cleaned = cutName(m.groupValues[1])
            if (cleaned.length >= 3) return cleaned
        }
        for (line in lines) {
            if (NAME_EXCLUDE_LINE.containsMatchIn(line)) continue
            val m = secondary.find(line) ?: continue
            val cleaned = cutName(m.groupValues[1])
            if (cleaned.length >= 3) return cleaned
        }
        return ""
    }

    /** Removes the matched row label (and "Part A/B") so label letters are never read as grades. */
    private fun stripLabel(row: String, labelRegex: Regex): String =
        row.replace(labelRegex, " ")
            .replace(Regex("(?i)\\bpart\\s*[-:]?\\s*[ab]\\b"), " ")

    private fun extractGradesOrMarks(line: String): List<String> {
        val tokens = line.split(Regex("[\\s,;:|]+")).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        for (t in tokens) {
            val clean = t.trim('(', ')', '[', ']', '.')
            when {
                // Grades are capital letters; lowercase "a", "b", "c", "d" are ordinary words
                Regex("^[A-D][+]?$").matches(clean) -> result.add(clean)
                Regex("^\\d{1,2}(?:\\.\\d)?$").matches(clean) -> {
                    val v = clean.toDoubleOrNull()
                    if (v != null && v <= 100.0) result.add(clean)
                }
            }
        }
        return result
    }

    private fun sha256Short(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
