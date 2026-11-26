package com.bspsms.android

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.google.mlkit.vision.text.Text as MlText

// ------------------------------------------------------------------ portal side (JavaScript)

/**
 * JavaScript that remembers which student row the teacher touched in the portal table,
 * reads that row's boxes, and types values into them.
 * Row and box numbering is identical in the read and the write script.
 */
object HprcIndividualJs {

    /** Remembers (and outlines) the last student row the teacher tapped. Safe to run repeatedly. */
    const val TRACKER: String = """
(function() {
    var W = window;
    var docs = [document];
    Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
        try { if (fr.contentDocument && fr.contentDocument.body) docs.push(fr.contentDocument); } catch (e) {}
    });
    docs.forEach(function(d) {
        if (d.__bspIndTracker) return;
        d.__bspIndTracker = true;
        var handler = function(ev) {
            var el = ev.target;
            if (!el || !el.closest) return;
            var tr = el.closest('tr');
            if (!tr) return;
            if (!tr.querySelector('input, select, textarea')) return;
            if (W.__bspSelTr && W.__bspSelTr !== tr) {
                try { W.__bspSelTr.style.outline = ''; } catch (e) {}
            }
            W.__bspSelTr = tr;
            tr.style.outline = '3px solid #2563eb';
        };
        d.addEventListener('focusin', handler, true);
        d.addEventListener('click', handler, true);
        d.addEventListener('touchstart', handler, true);
    });
    return 'ok';
})();
"""

    private const val COMMON: String = """
var W = window;
var docs = [document];
Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
    try { if (fr.contentDocument && fr.contentDocument.body) docs.push(fr.contentDocument); } catch (e) {}
});
function isW(el) {
    if (!el || el.disabled || el.readOnly) return false;
    if (el.offsetParent === null) return false;
    if (el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') return true;
    if (el.tagName === 'INPUT') {
        var t = (el.type || 'text').toLowerCase();
        if (t !== 'text' && t !== 'number' && t !== 'tel' && t !== '') return false;
        var meta = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
        if (/search|filter|captcha|password|login/.test(meta)) return false;
        return true;
    }
    return false;
}
var best = null, bestCount = 0, bestDoc = document;
docs.forEach(function(d) {
    Array.from(d.querySelectorAll('table')).forEach(function(tbl) {
        var c = Array.from(tbl.querySelectorAll('tr input, tr select, tr textarea')).filter(isW).length;
        if (c > bestCount) { bestCount = c; best = tbl; bestDoc = d; }
    });
});
var dataTrs = [];
if (best) {
    var bodyRows = Array.from(best.querySelectorAll('tbody tr'));
    if (bodyRows.length === 0) bodyRows = Array.from(best.querySelectorAll('tr')).slice(1);
    dataTrs = bodyRows.filter(function(tr) {
        return Array.from(tr.querySelectorAll('input, select, textarea')).some(isW);
    });
}
var selIdx = W.__bspSelTr ? dataTrs.indexOf(W.__bspSelTr) : -1;
if (selIdx < 0) {
    for (var di = 0; di < docs.length; di++) {
        var ae = docs[di].activeElement;
        if (ae && ae.closest) {
            var atr = ae.closest('tr');
            var ai = dataTrs.indexOf(atr);
            if (ai >= 0) { selIdx = ai; W.__bspSelTr = atr; break; }
        }
    }
}
"""

    /** Returns JSON about the selected row: student, page category/subject and its writable boxes. */
    fun readSelectionJs(): String = "(function() {\n" + COMMON + """
function selectedLabel(patterns) {
    var result = '';
    docs.forEach(function(d) {
        Array.from(d.querySelectorAll('select')).forEach(function(sel) {
            var meta = ((sel.name || '') + ' ' + (sel.id || '')).toLowerCase();
            var lab = '';
            if (sel.id) {
                var l = d.querySelector('label[for="' + sel.id + '"]');
                if (l) lab = l.innerText || '';
            }
            var all = (meta + ' ' + lab).toLowerCase();
            if (patterns.some(function(p) { return p.test(all); })) {
                var opt = sel.options[sel.selectedIndex];
                if (opt && !result) result = (opt.text || '').trim();
            }
        });
    });
    return result;
}
var scopeParts = [];
docs.forEach(function(d) {
    Array.from(d.querySelectorAll('h1, h2, h3, h4, h5, .box-title, .page-title, .panel-title, caption, legend')).forEach(function(h) {
        scopeParts.push((h.innerText || '').replace(/\s+/g, ' ').trim());
    });
});
var headerLabels = [];
if (best) {
    headerLabels = Array.from(best.querySelectorAll('thead tr:last-child th, thead th, tr:first-child th'))
        .map(function(th) { return (th.innerText || '').replace(/\s+/g, ' ').trim(); });
    scopeParts.push(headerLabels.join(' '));
}
scopeParts.push(selectedLabel([/evaluation|assessment|category|exam/]));
var pageText = scopeParts.join('\n');
var urlLower = (location.href || '').toLowerCase();
var cat = 'Summative';
if (/formative/.test(urlLower) || /formative/i.test(pageText) && !/summative/i.test(pageText)) cat = 'Formative';
else if (/lpcd/.test(urlLower) || /\blpcd\b|learning\s+perspective/i.test(pageText)) cat = 'LPCD';
else if (/bco/.test(urlLower) || /\bbco\b|co-?curricular|behavio/i.test(pageText)) cat = 'BCO';
var out = {
    ok: true, hasTable: bestCount > 0, selected: selIdx >= 0, rowIndex: selIdx,
    studentName: '', roll: '', studentCode: '', category: cat,
    subject: selectedLabel([/subject|language/]), fields: []
};
if (selIdx >= 0) {
    var tr = dataTrs[selIdx];
    var tds = Array.from(tr.children);
    tds.forEach(function(td, ci) {
        if (td.querySelector('input, select, textarea')) return;
        var t = (td.innerText || '').replace(/\s+/g, ' ').trim();
        if (!t) return;
        if (!/\D/.test(t) && t.length >= 10 && t.length <= 18) { if (!out.studentCode) out.studentCode = t; return; }
        if (!/\D/.test(t) && t.length <= 4 && ci <= 2) { if (!out.roll) out.roll = t; return; }
        if (/[A-Za-z\u0980-\u09FF]{2,}/.test(t) && !out.studentName && !/^(male|female|other)/i.test(t)) out.studentName = t;
    });
    var ctrls = Array.from(tr.querySelectorAll('input, select, textarea')).filter(isW);
    ctrls.forEach(function(el, fi) {
        var cell = el.closest('td');
        var ci = cell ? tds.indexOf(cell) : -1;
        var label = (ci >= 0 && headerLabels[ci]) ? headerLabels[ci] : (el.getAttribute('placeholder') || el.getAttribute('aria-label') || el.name || '');
        out.fields.push({ idx: fi, label: String(label), max: String(el.getAttribute('max') || el.getAttribute('maxlength') || '') });
    });
}
return JSON.stringify(out);
})();
"""

    /** Types the values into the SELECTED row only; refuses if the selection moved since the scan. */
    fun applyJs(planJson: String): String = "(function() {\n" + COMMON + """
var plan = JSON.parse(""" + JSONObject.quote(planJson) + """);
if (selIdx < 0 || selIdx !== plan.expectedRow) {
    return JSON.stringify({ ok: false, error: 'selection changed' });
}
function setVal(el, value) {
    try {
        var v = String(value);
        if (el.tagName === 'SELECT') {
            var matched = false;
            Array.from(el.options).forEach(function(o) {
                if (!matched && ((o.value || '').trim().toLowerCase() === v.toLowerCase() || (o.text || '').trim().toLowerCase() === v.toLowerCase())) {
                    el.value = o.value; matched = true;
                }
            });
            if (!matched) return false;
        } else {
            var maxAttr = parseFloat(el.getAttribute('max'));
            var num = parseFloat(v);
            if (el.type === 'number' || el.getAttribute('max')) {
                if (!isNaN(maxAttr) && !isNaN(num) && num > maxAttr) return false;
            }
            var proto = Object.getPrototypeOf(el);
            var desc = Object.getOwnPropertyDescriptor(proto, 'value');
            if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
        }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
        return true;
    } catch (e) { return false; }
}
var trSel = dataTrs[selIdx];
var ctrls = Array.from(trSel.querySelectorAll('input, select, textarea')).filter(isW);
var filled = 0, skipped = 0;
(plan.writes || []).forEach(function(w) {
    var el = ctrls[w.fieldIdx];
    if (el && setVal(el, w.value)) filled++; else skipped++;
});
trSel.style.outline = '3px solid #16a34a';
return JSON.stringify({ ok: true, filled: filled, skipped: skipped });
})();
"""
}

// ------------------------------------------------------------------ mapping scanned values -> portal boxes

private data class SelField(val idx: Int, val label: String, val max: String)

private data class SelInfo(
    val hasTable: Boolean = false,
    val selected: Boolean = false,
    val rowIndex: Int = -1,
    val name: String = "",
    val roll: String = "",
    val category: String = "Summative",
    val subject: String = "",
    val fields: List<SelField> = emptyList()
)

private data class MappedWrites(val writes: List<Pair<Int, String>>, val note: String)

private fun mapReadingToFields(category: String, term: Int, reading: HprcIndividualReading, fields: List<SelField>): MappedWrites {
    val writes = ArrayList<Pair<Int, String>>()
    val n = fields.size
    when (category.lowercase()) {
        "formative" -> {
            val labelRx = Regex("F\\s*([1-3])\\s*([ABC])", RegexOption.IGNORE_CASE)
            var labelled = 0
            for (f in fields) {
                val m = labelRx.find(f.label) ?: continue
                labelled++
                val phase = m.groupValues[1].toInt()
                val pos = (phase - 1) * 3 + (m.groupValues[2].uppercase()[0] - 'A')
                if (phase == term) reading.allValues.getOrNull(pos)?.let { writes.add(f.idx to it) }
            }
            if (labelled == 0) {
                when {
                    n == 3 -> reading.values.forEachIndexed { i, v -> writes.add(fields[i].idx to v) }
                    n == 9 -> reading.values.forEachIndexed { i, v -> writes.add(fields[(term - 1) * 3 + i].idx to v) }
                    else -> return MappedWrites(emptyList(), "This portal row has $n box(es); cannot tell which are F-A / F-B / F-C.")
                }
            }
        }
        "summative" -> {
            val v = reading.values.firstOrNull() ?: ""
            val termRx = listOf(
                Regex("1st|first|\\bt1\\b|term\\s*[-:]?\\s*(?:1|i)\\b", RegexOption.IGNORE_CASE),
                Regex("2nd|second|\\bt2\\b|term\\s*[-:]?\\s*(?:2|ii)\\b", RegexOption.IGNORE_CASE),
                Regex("3rd|third|\\bt3\\b|term\\s*[-:]?\\s*(?:3|iii)\\b", RegexOption.IGNORE_CASE)
            )
            val byLabel = fields.filter { termRx[term - 1].containsMatchIn(it.label) }
            when {
                byLabel.size == 1 -> writes.add(byLabel[0].idx to v)
                n == 1 -> writes.add(fields[0].idx to v)
                n >= 3 && byLabel.isEmpty() -> writes.add(fields[term - 1].idx to v)
                else -> return MappedWrites(emptyList(), "Cannot tell which of the $n box(es) is for this term.")
            }
        }
        else -> {
            val items = reading.values.size
            if (n != items && n != items - 1) {
                return MappedWrites(emptyList(), "This portal row has $n box(es) but the card page has $items items; not filled to avoid wrong data.")
            }
            for (i in 0 until minOf(n, items)) {
                if (reading.values[i].isNotBlank()) writes.add(fields[i].idx to reading.values[i])
            }
        }
    }
    return MappedWrites(writes, "")
}

private fun parseSelection(raw: String?): SelInfo? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return try {
        val json = JSONObject(JSONArray("[$raw]").getString(0))
        val arr = json.optJSONArray("fields") ?: JSONArray()
        val fields = ArrayList<SelField>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            fields.add(SelField(o.optInt("idx"), o.optString("label"), o.optString("max")))
        }
        SelInfo(
            hasTable = json.optBoolean("hasTable"),
            selected = json.optBoolean("selected"),
            rowIndex = json.optInt("rowIndex", -1),
            name = json.optString("studentName"),
            roll = json.optString("roll"),
            category = json.optString("category", "Summative"),
            subject = json.optString("subject"),
            fields = fields
        )
    } catch (e: Exception) {
        null
    }
}

private val ROMAN = listOf("I", "II", "III")

// ------------------------------------------------------------------ the popup

@Composable
fun HprcIndividualScanPopup(
    locale: AppLocale,
    portalWebViewProvider: () -> WebView?,
    onRequestCameraPermission: ((Boolean) -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    fun t(en: String, bn: String) = if (locale == AppLocale.BN) bn else en

    var expanded by remember { mutableStateOf(true) }
    var term by remember { mutableIntStateOf(1) }
    var subjectChoice by remember { mutableStateOf("AUTO") }
    var sel by remember { mutableStateOf(SelInfo()) }
    var message by remember {
        mutableStateOf(t("Tap any box beside a student's name, then press SCAN.", "ছাত্রের নামের পাশের যেকোনো বক্সে ট্যাপ করুন, তারপর SCAN চাপুন।"))
    }
    var captured by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val captureFlag = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        if (!hasCamera) onRequestCameraPermission { granted -> hasCamera = granted }
    }

    // Keep watching which student row the teacher touched in the portal
    LaunchedEffect(Unit) {
        while (isActive) {
            val wv = portalWebViewProvider()
            if (wv != null) {
                wv.evaluateJavascript(HprcIndividualJs.TRACKER) { }
                wv.evaluateJavascript(HprcIndividualJs.readSelectionJs()) { raw ->
                    val parsed = parseSelection(raw)
                    if (parsed != null) sel = parsed
                }
            }
            delay(900)
        }
    }

    fun finishWithText(text: MlText?) {
        val s = sel
        if (text == null) {
            captured = false
            message = t("Could not read the photo. Press SCAN again.", "ছবি পড়া যায়নি। আবার SCAN চাপুন।")
            return
        }
        if (!s.selected) {
            captured = false
            message = t("Select a student row in the portal first.", "আগে পোর্টালে একটি ছাত্রের সারি সিলেক্ট করুন।")
            return
        }
        val subjectId = if (subjectChoice != "AUTO") subjectChoice else HprcIndividualParser.canonicalSubject(s.subject)
        val reading = HprcIndividualParser.read(text, s.category, term, subjectId)
        if (!reading.ok) {
            captured = false
            message = reading.message
            return
        }
        val mapped = mapReadingToFields(s.category, term, reading, s.fields)
        if (mapped.writes.isEmpty()) {
            captured = false
            message = mapped.note.ifBlank { t("Nothing to fill for this row.", "এই সারির জন্য ভরার মতো কিছু নেই।") }
            return
        }
        val writesJson = JSONArray()
        for ((idx, value) in mapped.writes) {
            writesJson.put(JSONObject().put("fieldIdx", idx).put("value", value))
        }
        val plan = JSONObject().put("expectedRow", s.rowIndex).put("writes", writesJson)
        val wv = portalWebViewProvider()
        if (wv == null) {
            captured = false
            message = t("Portal is not available.", "পোর্টাল পাওয়া যায়নি।")
            return
        }
        val shown = mapped.writes.joinToString(", ") { it.second }
        wv.evaluateJavascript(HprcIndividualJs.applyJs(plan.toString())) { raw ->
            val ok = try {
                val o = JSONObject(JSONArray("[$raw]").getString(0))
                if (o.optBoolean("ok")) o.optInt("filled") else -1
            } catch (e: Exception) {
                -1
            }
            if (ok > 0) {
                captured = true
                message = t("Captured ✓ Filled: $shown", "ক্যাপচার হয়েছে ✓ ভরা হয়েছে: $shown") +
                    t(" — wrong? press SCAN again.", " — ভুল হলে আবার SCAN চাপুন।")
            } else {
                captured = false
                message = t(
                    "Row selection changed or boxes could not be filled. Select the row again and press SCAN.",
                    "সারি বদলে গেছে বা বক্সে লেখা যায়নি। আবার সারি সিলেক্ট করে SCAN চাপুন।"
                )
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    t("Individual Scan", "ইন্ডিভিজুয়াল স্ক্যান"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1766A7),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "−" else "+", fontSize = 18.sp) }
                TextButton(onClick = onClose) { Text("✕", fontSize = 16.sp, color = Color(0xFFDC2626)) }
            }

            val who = if (sel.selected) {
                listOf(sel.roll.takeIf { it.isNotBlank() }?.let { "Roll $it" }, sel.name.takeIf { it.isNotBlank() })
                    .filterNotNull().joinToString(" · ").ifBlank { t("Row ${sel.rowIndex + 1}", "সারি ${sel.rowIndex + 1}") }
            } else {
                t("No student row selected", "কোনো ছাত্র সিলেক্ট করা নেই")
            }
            Text(
                t("Student: ", "ছাত্র: ") + who + "  ·  " + sel.category + (if (sel.subject.isNotBlank()) " · ${sel.subject}" else ""),
                fontSize = 12.sp,
                color = if (sel.selected) Color(0xFF166534) else Color(0xFFB45309),
                fontWeight = FontWeight.SemiBold
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in 1..3) {
                        val active = term == k
                        Button(
                            onClick = { term = k },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) Color(0xFF1766A7) else Color(0xFFE2E8F0),
                                contentColor = if (active) Color.White else Color(0xFF334155)
                            )
                        ) { Text(t("Term ", "পর্যায় ") + ROMAN[k - 1], fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                if (sel.category.equals("Formative", true) || sel.category.equals("Summative", true)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val choices = listOf(
                            "AUTO" to t("Auto subject", "অটো বিষয়"),
                            "FIRST" to "1st Lang",
                            "SECOND" to "2nd Lang",
                            "MATH" to "Maths",
                            "ART" to "Art & Work",
                            "HEALTH" to "Health & PE"
                        )
                        for ((id, label) in choices) {
                            val on = subjectChoice == id
                            OutlinedButton(
                                onClick = { subjectChoice = id },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (on) Color(0xFFDBEAFE) else Color.White
                                )
                            ) { Text(label, fontSize = 11.sp) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (hasCamera) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .padding(0.dp)
                    ) {
                        HprcCaptureCameraView(captureFlag = captureFlag, onText = { finishWithText(it) })
                    }
                } else {
                    Text(
                        t("Camera permission is needed to scan.", "স্ক্যান করতে ক্যামেরার অনুমতি দরকার।"),
                        fontSize = 12.sp,
                        color = Color(0xFFB45309)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!sel.selected) {
                            captured = false
                            message = t("Select a student row in the portal first.", "আগে পোর্টালে একটি ছাত্রের সারি সিলেক্ট করুন।")
                        } else {
                            captured = false
                            message = t("Reading…", "পড়া হচ্ছে…")
                            captureFlag.set(true)
                        }
                    },
                    enabled = hasCamera,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1766A7))
                ) { Text("SCAN", fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                message,
                fontSize = 12.sp,
                fontWeight = if (captured) FontWeight.Bold else FontWeight.Normal,
                color = if (captured) Color(0xFF166534) else Color(0xFF334155)
            )
        }
    }
}

/** Small camera preview that reads ONE frame each time captureFlag is set; nothing is saved as a photo. */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun HprcCaptureCameraView(
    captureFlag: AtomicBoolean,
    onText: (MlText?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnText by rememberUpdatedState(onText)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val highRes = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(2560, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(highRes)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    // Only a deliberate tap on SCAN reads a frame; every other frame is dropped
                    if (!captureFlag.compareAndSet(true, false)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        mainExecutor.execute { latestOnText(null) }
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    recognizer.process(input)
                        .addOnSuccessListener(mainExecutor) { visionText -> latestOnText(visionText) }
                        .addOnFailureListener(mainExecutor) { latestOnText(null) }
                        .addOnCompleteListener { imageProxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}
