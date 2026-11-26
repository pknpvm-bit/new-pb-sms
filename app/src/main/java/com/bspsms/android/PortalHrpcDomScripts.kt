package com.bspsms.android

import org.json.JSONObject

object PortalHrpcDomScripts {

    private fun sharedRowActionJs(): String = """
        function _bspNormalizeText(s) {
            return String(s || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
        }
        function _bspGetColMap(tbl) {
            var headers = Array.from(tbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td'));
            var map = { roll: -1, name: -1, view: -1, print: -1, action: -1, headers: headers.map(function(c) { return _bspNormalizeText(c.innerText).toLowerCase(); }) };
            map.headers.forEach(function(txt, idx) {
                if (/roll/.test(txt) && map.roll === -1) map.roll = idx;
                else if ((/student\s*name|\bname\b/.test(txt)) && map.name === -1) map.name = idx;
                else if (/\bview\b/.test(txt) && !/print/.test(txt) && map.view === -1) map.view = idx;
                else if (/\bprint\b/.test(txt) && !/view/.test(txt) && map.print === -1) map.print = idx;
                else if (/action|marksheet|report|view.*print|print.*view/.test(txt) && map.action === -1) map.action = idx;
            });
            return map;
        }
        function _bspElementOwnAndChildMeta(el) {
            var cls = typeof el.className === 'string' ? el.className : (el.getAttribute('class') || '');
            var parts = [
                el.innerText || '',
                el.textContent || '',
                el.value || '',
                el.title || '',
                el.id || '',
                el.name || '',
                cls,
                el.getAttribute('aria-label') || '',
                el.getAttribute('data-original-title') || '',
                el.getAttribute('data-bs-original-title') || '',
                el.getAttribute('data-title') || '',
                el.getAttribute('data-tooltip') || '',
                el.getAttribute('data-action') || '',
                el.getAttribute('href') || '',
                el.getAttribute('onclick') || ''
            ];
            var nodes = el.querySelectorAll('i, span, svg, img, path, use');
            for (var i = 0; i < nodes.length; i++) {
                var n = nodes[i];
                var nCls = typeof n.className === 'string' ? n.className : (n.getAttribute('class') || '');
                parts.push(nCls);
                parts.push(n.id || '');
                parts.push(n.alt || '');
                parts.push(n.title || '');
                parts.push(n.getAttribute('src') || '');
                parts.push(n.getAttribute('href') || '');
                parts.push(n.getAttribute('xlink:href') || '');
                parts.push(n.getAttribute('data-original-title') || '');
                parts.push(n.getAttribute('data-bs-original-title') || '');
                parts.push(n.getAttribute('aria-label') || '');
            }
            var nonHtmlMeta = parts.join(' ').toLowerCase();
            var htmlMeta = (el.innerHTML || '').toLowerCase();
            return { nonHtml: nonHtmlMeta, full: nonHtmlMeta + ' ' + htmlMeta };
        }
        function _bspCollectRowClickables(container) {
            if (!container) return [];
            var raw = Array.from(container.querySelectorAll('a, button, input[type="button"], input[type="submit"], [role="button"], span[onclick], i[onclick], img[onclick], td i.fa, td i[class*="icon"], td svg'));
            var filtered = raw.filter(function(el) {
                var parentCtrl = el.parentElement ? el.parentElement.closest('a, button, input[type="button"], input[type="submit"], [role="button"]') : null;
                if (parentCtrl && parentCtrl !== el && container.contains(parentCtrl)) return false;
                var st = null;
                try { st = window.getComputedStyle(el); } catch(e) {}
                if (st && (st.display === 'none' || st.visibility === 'hidden')) return false;
                if (el.tagName === 'I' || el.tagName === 'SVG') {
                    var clickableStyle = st && st.cursor === 'pointer';
                    if (!el.getAttribute('onclick') && !clickableStyle && !el.closest('td')) return false;
                }
                return true;
            });
            return filtered.length > 0 ? filtered : Array.from(container.querySelectorAll('a, button, input[type="button"], input[type="submit"], [role="button"], [onclick]'));
        }
        function _bspFindActionControl(tbl, row, actionWord, map) {
            if (!map) map = _bspGetColMap(tbl);
            var cells = Array.from(row.querySelectorAll('td'));
            var childRow = (row.nextElementSibling && row.nextElementSibling.classList && row.nextElementSibling.classList.contains('child')) ? row.nextElementSibling : null;

            function matchesPrint(el, tdIdx) {
                var m = _bspElementOwnAndChildMeta(el);
                if (/(?:^|[^a-z])(print|printer|pdf)(?:[^a-z]|${'$'})|fa-print|glyphicon-print|bi-printer|mdi-printer|la-print|print_hrpc|printcard|printmarksheet|printreport/i.test(m.nonHtml)) return true;
                if (/fa-print|glyphicon-print|bi-printer|mdi-printer|printer|print_icon|print-icon/i.test(m.full)) return true;
                if (tdIdx >= 0 && map.headers[tdIdx] && /print/.test(map.headers[tdIdx]) && !/view/.test(map.headers[tdIdx])) return true;
                return false;
            }
            function matchesView(el, tdIdx) {
                var m = _bspElementOwnAndChildMeta(el);
                if (/(?:^|[^a-z])(view|preview|eye|marksheet)(?:[^a-z]|${'$'})|fa-eye|glyphicon-eye|bi-eye|mdi-eye|view_hrpc|viewcard|viewmarksheet/i.test(m.nonHtml)) return true;
                if (/fa-eye|glyphicon-eye|bi-eye|mdi-eye/i.test(m.full)) return true;
                if (tdIdx >= 0 && map.headers[tdIdx] && /view/.test(map.headers[tdIdx]) && !/print/.test(map.headers[tdIdx])) return true;
                return false;
            }

            var allCtrls = _bspCollectRowClickables(row);
            if (childRow) {
                allCtrls = allCtrls.concat(_bspCollectRowClickables(childRow));
            }
            if (allCtrls.length === 0) return null;

            // 1. Direct explicit column match
            if (actionWord === 'print' && map.print >= 0 && cells[map.print]) {
                var pColCtrls = _bspCollectRowClickables(cells[map.print]);
                if (pColCtrls.length > 0) return pColCtrls[0];
            }
            if (actionWord === 'view' && map.view >= 0 && cells[map.view]) {
                var vColCtrls = _bspCollectRowClickables(cells[map.view]);
                if (vColCtrls.length > 0) return vColCtrls[0];
            }

            // 2. Attribute / class / icon / SVG / title / onclick / href match
            for (var i = 0; i < allCtrls.length; i++) {
                var ctrl = allCtrls[i];
                var td = ctrl.closest('td');
                var tdIdx = td ? cells.indexOf(td) : -1;
                if (actionWord === 'print' && matchesPrint(ctrl, tdIdx)) return ctrl;
                if (actionWord === 'view' && matchesView(ctrl, tdIdx)) return ctrl;
            }

            // 3. Actions cell & positional pair fallback (View = 1st control, Print = 2nd control)
            var actionCell = (map.action >= 0 && cells[map.action]) ? cells[map.action] : null;
            var actionCtrls = actionCell ? _bspCollectRowClickables(actionCell) : [];
            if (actionCtrls.length === 0) {
                actionCtrls = allCtrls.filter(function(el) {
                    var td = el.closest('td');
                    var idx = td ? cells.indexOf(td) : -1;
                    if (idx === -1) return true;
                    if (idx === map.roll || idx === map.name) return false;
                    return true;
                });
                for (var c = cells.length - 1; c >= 0; c--) {
                    var cCtrls = _bspCollectRowClickables(cells[c]);
                    if (cCtrls.length >= 2) {
                        actionCtrls = cCtrls;
                        break;
                    }
                }
            }

            var viewCtrl = null;
            for (var j = 0; j < actionCtrls.length; j++) {
                var vtd = actionCtrls[j].closest('td');
                if (matchesView(actionCtrls[j], vtd ? cells.indexOf(vtd) : -1)) {
                    viewCtrl = actionCtrls[j];
                    break;
                }
            }

            if (actionWord === 'view') {
                if (viewCtrl) return viewCtrl;
                if (actionCtrls.length >= 1) return actionCtrls[0];
                return allCtrls[0];
            }

            if (actionWord === 'print') {
                if (viewCtrl) {
                    var vPos = actionCtrls.indexOf(viewCtrl);
                    if (vPos >= 0 && vPos + 1 < actionCtrls.length) {
                        return actionCtrls[vPos + 1];
                    }
                    var other = actionCtrls.find(function(c) { return c !== viewCtrl; });
                    if (other) return other;
                }
                if (actionCtrls.length >= 2) {
                    return actionCtrls[1];
                }
                if (allCtrls.length >= 2 && map.roll !== 0) {
                    return allCtrls[1];
                }
            }
            return null;
        }
    """.trimIndent()

    private fun sameSessionInterceptorCoreJs(): String = """
        if (!window.__bspSameSessionHooked) {
            window.__bspSameSessionHooked = true;
            window.__bspOrigWindowOpen = window.open;
            window.__bspOrigFormSubmit = HTMLFormElement.prototype.submit;
            window.__bspOrigFormRequestSubmit = HTMLFormElement.prototype.requestSubmit;
            window.__bspBgCaptureActive = false;
            window.__bspAutomationMainTabLock = false;
            window.__bspCapturedNav = null;

            function _bspAbsUrl(u) {
                if (!u) return '';
                var s = String(u).trim();
                if (!s || s === '#' || /^#/i.test(s) || /^javascript:/i.test(s) || /^about:blank/i.test(s)) return '';
                try {
                    return new URL(s, window.location.href).href;
                } catch (e) {
                    return s;
                }
            }

            function _bspSerializeFormSafe(formEl, submitBtn) {
                if (!formEl) return null;
                var rawAction = (formEl.getAttribute ? (formEl.getAttribute('action') || '') : '').trim();
                if (/^javascript:/i.test(rawAction)) return null;
                var actionUrl = _bspAbsUrl(rawAction || window.location.href);
                if (!actionUrl) return null;
                var method = ((formEl.getAttribute ? (formEl.getAttribute('method') || 'GET') : 'GET').trim() || 'GET').toUpperCase();
                var params = [];
                var fields = formEl.querySelectorAll ? formEl.querySelectorAll('input, select, textarea') : [];
                for (var i = 0; i < fields.length; i++) {
                    var el = fields[i];
                    if (!el.name || el.disabled) continue;
                    var type = (el.type || '').toLowerCase();
                    if ((type === 'checkbox' || type === 'radio') && !el.checked) continue;
                    if (type === 'submit' || type === 'button' || type === 'reset' || type === 'image' || type === 'file') continue;
                    if (el.tagName === 'SELECT' && el.multiple) {
                        Array.from(el.options).forEach(function(opt) {
                            if (opt.selected) params.push({ name: el.name, value: opt.value });
                        });
                    } else {
                        params.push({ name: el.name, value: el.value || '' });
                    }
                }
                if (submitBtn && submitBtn.name) {
                    params.push({ name: submitBtn.name, value: submitBtn.value || '' });
                }
                return { mode: 'form', url: actionUrl, method: method, params: params, source: 'form_lock' };
            }

            function _bspPublishCapturedNav(navObj) {
                if (!navObj) return;
                window.__bspCapturedNav = navObj;
                if (!window.__bspBgCaptureActive && window.__bspAutomationMainTabLock &&
                    window.BspAndroidBridge && window.BspAndroidBridge.dispatchCapturedBackgroundTarget) {
                    try {
                        window.BspAndroidBridge.dispatchCapturedBackgroundTarget(JSON.stringify(navObj));
                    } catch (e) {}
                }
            }

            HTMLFormElement.prototype.submit = function() {
                if (window.__bspBgCaptureActive || window.__bspAutomationMainTabLock) {
                    var ser = _bspSerializeFormSafe(this, null);
                    if (ser && ser.url) {
                        _bspPublishCapturedNav(ser);
                        return;
                    }
                    return;
                }
                return window.__bspOrigFormSubmit.apply(this, arguments);
            };

            if (typeof window.__bspOrigFormRequestSubmit === 'function') {
                HTMLFormElement.prototype.requestSubmit = function(submitter) {
                    if (window.__bspBgCaptureActive || window.__bspAutomationMainTabLock) {
                        var ser = _bspSerializeFormSafe(this, submitter || null);
                        if (ser && ser.url) {
                            _bspPublishCapturedNav(ser);
                            return;
                        }
                        return;
                    }
                    return window.__bspOrigFormRequestSubmit.apply(this, arguments);
                };
            }

            document.addEventListener('click', function(ev) {
                if (!window.__bspBgCaptureActive && !window.__bspAutomationMainTabLock) return;
                var anchor = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
                if (anchor) {
                    var abs = _bspAbsUrl(anchor.getAttribute('href'));
                    if (abs) {
                        ev.preventDefault();
                        _bspPublishCapturedNav({ mode: 'url', url: abs, source: 'click_lock' });
                    }
                }
            }, true);

            document.addEventListener('submit', function(ev) {
                if (!window.__bspBgCaptureActive && !window.__bspAutomationMainTabLock) return;
                var ser = _bspSerializeFormSafe(ev.target, ev.submitter || null);
                ev.preventDefault();
                if (ser && ser.url) {
                    _bspPublishCapturedNav(ser);
                }
            }, true);

            window.open = function(url, name, specs) {
                var u = (url === undefined || url === null) ? '' : String(url).trim();
                var abs = (u && !/^about:blank/i.test(u) && !/^javascript:/i.test(u)) ? _bspAbsUrl(u) : '';
                if (window.__bspBgCaptureActive || window.__bspAutomationMainTabLock) {
                    if (abs) {
                        _bspPublishCapturedNav({ mode: 'url', url: abs, source: 'window_open' });
                    }
                    var capturedUrl = abs;
                    var dummyDocHtml = '';
                    return {
                        closed: false,
                        focus: function() {},
                        blur: function() {},
                        close: function() {},
                        print: function() { window.__bspPrintTriggered = true; },
                        document: {
                            open: function() {},
                            close: function() {
                                if (dummyDocHtml) {
                                    _bspPublishCapturedNav({ mode: 'html', html: dummyDocHtml, baseUrl: window.location.href, source: 'window_open_doc' });
                                }
                            },
                            write: function(s) {
                                dummyDocHtml += String(s || '');
                                _bspPublishCapturedNav({ mode: 'html', html: dummyDocHtml, baseUrl: window.location.href, source: 'window_open_doc' });
                            },
                            writeln: function(s) {
                                dummyDocHtml += String(s || '') + '\n';
                                _bspPublishCapturedNav({ mode: 'html', html: dummyDocHtml, baseUrl: window.location.href, source: 'window_open_doc' });
                            }
                        },
                        location: {
                            get href() { return capturedUrl || window.location.href; },
                            set href(v) {
                                var nextAbs = _bspAbsUrl(v);
                                if (nextAbs) {
                                    capturedUrl = nextAbs;
                                    _bspPublishCapturedNav({ mode: 'url', url: nextAbs, source: 'window_open_loc' });
                                }
                            },
                            assign: function(v) {
                                var nextAbs = _bspAbsUrl(v);
                                if (nextAbs) {
                                    capturedUrl = nextAbs;
                                    _bspPublishCapturedNav({ mode: 'url', url: nextAbs, source: 'window_open_assign' });
                                }
                            },
                            replace: function(v) {
                                var nextAbs = _bspAbsUrl(v);
                                if (nextAbs) {
                                    capturedUrl = nextAbs;
                                    _bspPublishCapturedNav({ mode: 'url', url: nextAbs, source: 'window_open_replace' });
                                }
                            }
                        }
                    };
                }
                return window.__bspOrigWindowOpen ? window.__bspOrigWindowOpen.apply(window, arguments) : null;
            };
        }
    """.trimIndent()

    fun setAutomationMainTabLockJs(locked: Boolean): String = """
        (function() {
            ${sameSessionInterceptorCoreJs()}
            window.__bspAutomationMainTabLock = ${if (locked) "true" else "false"};
            if (window.__bspAutomationMainTabLock) {
                window.__bspCapturedNav = null;
            }
            return 'ok';
        })();
    """.trimIndent()

    fun installSameSessionPopupInterceptorJs(): String = """
        (function() {
            ${sameSessionInterceptorCoreJs()}
            return 'ok';
        })();
    """.trimIndent()

    fun snapshotHrpcStateJs(): String = """
        (function() {
            function findSelect(patterns) {
                var selects = Array.from(document.querySelectorAll('select'));
                for (var p = 0; p < patterns.length; p++) {
                    var rx = patterns[p];
                    for (var i = 0; i < selects.length; i++) {
                        var s = selects[i];
                        var lbl = '';
                        if (s.id) {
                            var l = document.querySelector('label[for="' + s.id + '"]');
                            if (l) lbl = l.innerText || '';
                        }
                        if (!lbl && s.closest('.form-group, td, div')) {
                            lbl = (s.closest('.form-group, td, div').innerText || '').split('\n')[0] || '';
                        }
                        var meta = ((s.name || '') + ' ' + (s.id || '') + ' ' + lbl).toLowerCase();
                        if (rx.test(meta)) {
                            var selText = s.options && s.selectedIndex >= 0 ? (s.options[s.selectedIndex].text || '').trim() : '';
                            return { value: s.value || '', text: selText };
                        }
                    }
                }
                return { value: '', text: '' };
            }

            var pageInfo = { startEntry: 0, endEntry: 0, totalEntries: 0, currentPage: 1, totalPages: 1 };
            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
            var infoEl = document.querySelector('.dataTables_info, [id*="_info"]');
            var infoTxt = infoEl ? infoEl.innerText : bodyTxt;
            var m = infoTxt.match(/Showing\s+(\d+)\s+to\s+(\d+)\s+of\s+(\d+)\s+entries/i);
            if (m) {
                pageInfo.startEntry = parseInt(m[1], 10) || 0;
                pageInfo.endEntry = parseInt(m[2], 10) || 0;
                pageInfo.totalEntries = parseInt(m[3], 10) || 0;
            }
            var activePageEl = document.querySelector('.dataTables_paginate .paginate_button.active, .pagination li.active a, .pagination li.active span, .paginate_button.current');
            if (activePageEl) {
                var pn = parseInt((activePageEl.innerText || '').trim(), 10);
                if (pn > 0) pageInfo.currentPage = pn;
            }
            var pageNums = Array.from(document.querySelectorAll('.dataTables_paginate .paginate_button, .pagination li a, .pagination li span'))
                .map(function(el) { return parseInt((el.innerText || '').trim(), 10); })
                .filter(function(n) { return !isNaN(n) && n > 0; });
            if (pageNums.length > 0) {
                pageInfo.totalPages = Math.max.apply(null, pageNums);
            } else if (pageInfo.totalEntries > 0) {
                var perPage = (pageInfo.endEntry - pageInfo.startEntry + 1);
                if (perPage > 0) pageInfo.totalPages = Math.max(1, Math.ceil(pageInfo.totalEntries / perPage));
            }

            var hrpcMenuHref = '';
            var navLinks = Array.from(document.querySelectorAll('a[href]'));
            for (var n = 0; n < navLinks.length; n++) {
                var a = navLinks[n];
                var h = (a.href || '').trim();
                var t = (a.innerText || a.title || '').toLowerCase();
                if (!h || /^javascript:/i.test(h) || h.indexOf('#') === h.length - 1) continue;
                if (/hrpc|holistic.*progress|progress.*report.*card|marksheet/i.test(h + ' ' + t) &&
                    !/print|pdf|download/i.test(h)) {
                    hrpcMenuHref = h;
                    break;
                }
            }

            return JSON.stringify({
                url: location.href,
                referrer: document.referrer || '',
                hrpcMenuHref: hrpcMenuHref,
                academicYear: findSelect([/academic|session|year/]),
                medium: findSelect([/medium/]),
                className: findSelect([/\bclass\b|grade/]),
                section: findSelect([/section/]),
                currentPage: pageInfo.currentPage,
                totalPages: pageInfo.totalPages,
                totalEntries: pageInfo.totalEntries,
                startEntry: pageInfo.startEntry,
                endEntry: pageInfo.endEntry
            });
        })();
    """.trimIndent()

    fun openHrpcMenuFromDashboardJs(): String = """
        (function() {
            var links = Array.from(document.querySelectorAll('a[href]'));
            var candidates = links.filter(function(a) {
                var h = (a.href || '').trim();
                var t = (a.innerText || a.title || '').toLowerCase();
                if (!h || /^javascript:/i.test(h) || h.indexOf('#') === h.length - 1) return false;
                return (/hrpc|holistic|progress.*report|marksheet/i.test(h + ' ' + t) && !/print|pdf|download/i.test(h));
            });
            if (candidates.length > 0) {
                candidates[0].setAttribute('target', '_self');
                candidates[0].click();
                return JSON.stringify({ clicked: true, href: candidates[0].href });
            }
            return JSON.stringify({ clicked: false });
        })();
    """.trimIndent()

    fun scanHrpcTableJs(): String = """
        (function() {
            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
            var hasAuthForm = !!document.querySelector('input[type="password"], input[name*="captcha" i], img[src*="captcha" i]');
            if (hasAuthForm) {
                return JSON.stringify({ ok: false, sessionExpired: true, reason: 'Portal sign-in page is displayed. Please sign in first.' });
            }
            if (/An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt)) {
                return JSON.stringify({
                    ok: false,
                    sessionExpired: false,
                    dbError: true,
                    url: location.href,
                    reason: 'Portal database error on current page. Recovering cleanly...'
                });
            }

            ${sharedRowActionJs()}

            var tables = Array.from(document.querySelectorAll('table'));
            var bestTable = null;
            var bestColMap = null;
            var bestScore = -1;

            for (var t = 0; t < tables.length; t++) {
                var tbl = tables[t];
                var headers = Array.from(tbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td'));
                if (headers.length === 0) continue;
                var map = _bspGetColMap(tbl);

                var dataRows = Array.from(tbl.querySelectorAll('tbody tr'));
                if (dataRows.length === 0) {
                    dataRows = Array.from(tbl.querySelectorAll('tr')).filter(function(r) { return !r.querySelector('th'); });
                }

                var hasViewOrPrintButtons = false;
                for (var r = 0; r < Math.min(dataRows.length, 5); r++) {
                    var rowTxt = (dataRows[r].innerText || '').toLowerCase();
                    if (/\bview\b|\bprint\b/i.test(rowTxt) ||
                        _bspCollectRowClickables(dataRows[r]).length > 0) {
                        hasViewOrPrintButtons = true;
                        break;
                    }
                }

                var score = 0;
                if (map.roll >= 0) score += 3;
                if (map.name >= 0) score += 3;
                if (map.view >= 0 || map.print >= 0 || map.action >= 0) score += 2;
                if (hasViewOrPrintButtons) score += 3;
                if (dataRows.length > 0) score += 1;

                if (score > bestScore && dataRows.length > 0 && (map.roll >= 0 || map.name >= 0 || hasViewOrPrintButtons)) {
                    bestScore = score;
                    bestTable = tbl;
                    bestColMap = map;
                }
            }

            if (!bestTable) {
                return JSON.stringify({
                    ok: false,
                    sessionExpired: false,
                    dbError: false,
                    reason: 'Student Information table not found. Select Academic Year, Medium, Class, Section and click Search first.'
                });
            }

            bestTable.setAttribute('data-bsp-student-table', 'true');

            var isTableProcessing = false;
            var procEls = document.querySelectorAll('.dataTables_processing');
            for (var p = 0; p < procEls.length; p++) {
                try {
                    var pst = window.getComputedStyle(procEls[p]);
                    var prect = procEls[p].getBoundingClientRect();
                    if (pst.display !== 'none' && pst.visibility !== 'hidden' && parseFloat(pst.opacity || '1') > 0.05 && prect.width > 2 && prect.height > 2) {
                        isTableProcessing = true;
                        break;
                    }
                } catch (e) {}
            }

            var rows = Array.from(bestTable.querySelectorAll('tbody tr'));
            if (rows.length === 0) {
                rows = Array.from(bestTable.querySelectorAll('tr')).filter(function(r) { return !r.querySelector('th'); });
            }

            var students = [];
            rows.forEach(function(row, idx) {
                if (row.classList && row.classList.contains('child')) return;
                var cells = Array.from(row.querySelectorAll('td'));
                if (cells.length === 0) return;
                var rowFullTxt = (row.innerText || '').trim();
                if (/no\s*(?:data|matching\s*records)\s*(?:available|found)/i.test(rowFullTxt)) return;
                if (/^(?:loading|processing)\.{0,3}$/i.test(rowFullTxt)) {
                    isTableProcessing = true;
                    return;
                }

                var rollIdx = bestColMap.roll >= 0 ? bestColMap.roll : 0;
                var nameIdx = bestColMap.name >= 0 ? bestColMap.name : (cells.length > 1 ? 1 : 0);

                var rollNo = cells[rollIdx] ? (cells[rollIdx].innerText || '').trim() : '';
                var name = cells[nameIdx] ? (cells[nameIdx].innerText || '').trim() : '';

                if (!name && rollNo) {
                    name = 'STUDENT_' + rollNo;
                }
                if (!rollNo && !name) return;

                var viewEl = _bspFindActionControl(bestTable, row, 'view', bestColMap);
                var printEl = _bspFindActionControl(bestTable, row, 'print', bestColMap);

                row.setAttribute('data-bsp-row-idx', String(idx));
                students.push({
                    rowIndex: idx,
                    rollNo: rollNo.replace(/\s+/g, ' ').trim(),
                    name: name.replace(/\s+/g, ' ').trim(),
                    hasView: !!viewEl,
                    hasPrint: !!printEl,
                    viewHref: viewEl && viewEl.tagName === 'A' ? (viewEl.getAttribute('href') || '') : '',
                    printHref: printEl && printEl.tagName === 'A' ? (printEl.getAttribute('href') || '') : ''
                });
            });

            var infoEl = document.querySelector('.dataTables_info, [id*="_info"]');
            var infoTxt = infoEl ? infoEl.innerText : bodyTxt;
            var startEntry = 0, endEntry = 0, totalEntries = students.length, currentPage = 1, totalPages = 1;
            var m = infoTxt.match(/Showing\s+(\d+)\s+to\s+(\d+)\s+of\s+(\d+)\s+entries/i);
            if (m) {
                startEntry = parseInt(m[1], 10) || 0;
                endEntry = parseInt(m[2], 10) || 0;
                totalEntries = parseInt(m[3], 10) || students.length;
            }
            var activePageEl = document.querySelector('.dataTables_paginate .paginate_button.active, .pagination li.active a, .pagination li.active span, .paginate_button.current');
            if (activePageEl) {
                var pn = parseInt((activePageEl.innerText || '').trim(), 10);
                if (pn > 0) currentPage = pn;
            }
            var pageNums = Array.from(document.querySelectorAll('.dataTables_paginate .paginate_button, .pagination li a, .pagination li span'))
                .map(function(el) { return parseInt((el.innerText || '').trim(), 10); })
                .filter(function(n) { return !isNaN(n) && n > 0; });
            if (pageNums.length > 0) {
                totalPages = Math.max.apply(null, pageNums);
            } else if (totalEntries > 0 && endEntry >= startEntry && startEntry > 0) {
                var perPage = endEntry - startEntry + 1;
                totalPages = Math.max(1, Math.ceil(totalEntries / perPage));
            }

            var nextBtn = document.querySelector('.dataTables_paginate .paginate_button.next:not(.disabled), li.next:not(.disabled) a, a.next:not(.disabled), button.next:not([disabled])');
            var hasNext = false;
            if (nextBtn) {
                var cls = (nextBtn.className || '') + ' ' + ((nextBtn.parentElement && nextBtn.parentElement.className) || '');
                hasNext = !/disabled/i.test(cls);
            }
            if (!hasNext && currentPage < totalPages) hasNext = true;

            return JSON.stringify({
                ok: true,
                sessionExpired: false,
                dbError: false,
                isProcessing: isTableProcessing,
                url: location.href,
                currentPage: currentPage,
                totalPages: totalPages,
                startEntry: startEntry,
                endEntry: endEntry,
                totalEntries: totalEntries,
                hasNextPage: hasNext,
                students: students
            });
        })();
    """.trimIndent()

    fun triggerStudentActionJs(
        rowIndex: Int,
        rollNo: String,
        studentName: String,
        actionType: String
    ): String {
        val qRoll = JSONObject.quote(rollNo)
        val qName = JSONObject.quote(studentName)
        val qAction = JSONObject.quote(actionType)
        return """
            (function() {
                ${sameSessionInterceptorCoreJs()}
                window.__bspPrintTriggered = false;
                if (!window.__bspOrigPrint) {
                    window.__bspOrigPrint = window.print;
                }
                window.print = function() {
                    window.__bspPrintTriggered = true;
                };

                ${sharedRowActionJs()}

                function _bspResolveUrl(u) {
                    if (!u) return '';
                    var s = String(u).trim();
                    if (!s || s === '#' || /^#/i.test(s) || /^javascript:/i.test(s) || /^about:blank/i.test(s)) return '';
                    try { return new URL(s, window.location.href).href; } catch (e) { return s; }
                }

                function _bspSerializeForm(formEl, submitBtn) {
                    if (!formEl) return null;
                    var rawAction = (formEl.getAttribute('action') || '').trim();
                    if (/^javascript:/i.test(rawAction)) return null;
                    var actionUrl = _bspResolveUrl(rawAction || window.location.href);
                    var method = ((formEl.getAttribute('method') || 'GET').trim() || 'GET').toUpperCase();
                    var params = [];
                    var fields = formEl.querySelectorAll('input, select, textarea');
                    for (var i = 0; i < fields.length; i++) {
                        var el = fields[i];
                        if (!el.name || el.disabled) continue;
                        var type = (el.type || '').toLowerCase();
                        if ((type === 'checkbox' || type === 'radio') && !el.checked) continue;
                        if (type === 'submit' || type === 'button' || type === 'reset' || type === 'image' || type === 'file') continue;
                        if (el.tagName === 'SELECT' && el.multiple) {
                            Array.from(el.options).forEach(function(opt) {
                                if (opt.selected) params.push({ name: el.name, value: opt.value });
                            });
                        } else {
                            params.push({ name: el.name, value: el.value || '' });
                        }
                    }
                    if (submitBtn && submitBtn.name) {
                        params.push({ name: submitBtn.name, value: submitBtn.value || '' });
                    }
                    return { mode: 'form', url: actionUrl, method: method, params: params, source: 'form_inspect' };
                }

                function _bspExtractStaticTarget(ctrl) {
                    if (!ctrl) return null;
                    var onclickRaw = (ctrl.getAttribute('onclick') || '').trim();
                    if (ctrl.tagName === 'A') {
                        var rawHref = (ctrl.getAttribute('href') || '').trim();
                        var absHref = _bspResolveUrl(rawHref);
                        if (absHref && (!onclickRaw || /window\.open\s*\(|location\./i.test(onclickRaw))) {
                            return { mode: 'url', url: absHref, source: 'anchor_href' };
                        }
                    }
                    var dataAttrs = ['data-url', 'data-href', 'data-link', 'data-print-url', 'data-view-url', 'data-target-url', 'data-src'];
                    for (var d = 0; d < dataAttrs.length; d++) {
                        var dv = _bspResolveUrl(ctrl.getAttribute(dataAttrs[d]));
                        if (dv) return { mode: 'url', url: dv, source: dataAttrs[d] };
                    }
                    if (onclickRaw) {
                        var mOpen = onclickRaw.match(/(?:window\.open|location\.assign|location\.replace)\s*\(\s*['"]([^'"]+)['"]/i) ||
                            onclickRaw.match(/location(?:\.href)?\s*=\s*['"]([^'"]+)['"]/i) ||
                            onclickRaw.match(/['"](https?:\/\/[^'"]+)['"]/i) ||
                            onclickRaw.match(/['"]([^'"]*\/(?:print|view|marksheet|report|hrpc)[^'"]*)['"]/i);
                        if (mOpen && mOpen[1]) {
                            var resolvedOnclick = _bspResolveUrl(mOpen[1]);
                            if (resolvedOnclick) {
                                return { mode: 'url', url: resolvedOnclick, source: 'onclick_url' };
                            }
                        }
                    }
                    var formEl = ctrl.closest ? ctrl.closest('form') : null;
                    if (formEl) {
                        var isSubmit = (ctrl.tagName === 'BUTTON' && (!ctrl.type || ctrl.type.toLowerCase() === 'submit')) ||
                            (ctrl.tagName === 'INPUT' && ctrl.type && ctrl.type.toLowerCase() === 'submit') ||
                            !!formEl.getAttribute('action');
                        if (isSubmit) {
                            var serialized = _bspSerializeForm(formEl, ctrl);
                            if (serialized && serialized.url) return serialized;
                        }
                    }
                    return null;
                }

                var tbl = document.querySelector('table[data-bsp-student-table="true"]') ||
                    Array.from(document.querySelectorAll('table')).find(function(t) {
                        return t.querySelector('tbody tr');
                    });
                if (!tbl) return JSON.stringify({ ok: false, error: 'Student table missing on page' });

                var rows = Array.from(tbl.querySelectorAll('tbody tr')).filter(function(r) {
                    return !(r.classList && r.classList.contains('child'));
                });
                if (rows.length === 0) rows = Array.from(tbl.querySelectorAll('tr')).filter(function(r) { return !r.querySelector('th'); });

                var targetRow = tbl.querySelector('tr[data-bsp-row-idx="' + ${rowIndex} + '"]');
                var wantRoll = ($qRoll || '').replace(/\s+/g, ' ').trim().toLowerCase();
                var wantName = ($qName || '').replace(/\s+/g, ' ').trim().toLowerCase();
                if (!targetRow || (wantName && _bspNormalizeText(targetRow.innerText).toLowerCase().indexOf(wantName) === -1)) {
                    targetRow = rows.find(function(r) {
                        var txt = _bspNormalizeText(r.innerText).toLowerCase();
                        return (wantName && txt.indexOf(wantName) !== -1) && (!wantRoll || txt.indexOf(wantRoll) !== -1);
                    }) || rows.find(function(r) {
                        var txt = _bspNormalizeText(r.innerText).toLowerCase();
                        return wantRoll && txt.indexOf(wantRoll) !== -1;
                    }) || rows[${rowIndex}];
                }
                if (!targetRow) return JSON.stringify({ ok: false, error: 'Student row not found: ' + $qName });

                var colMap = _bspGetColMap(tbl);
                var primaryAction = ($qAction || 'print').toLowerCase();
                var secondaryAction = (primaryAction === 'print') ? 'view' : 'print';

                var chosen = _bspFindActionControl(tbl, targetRow, primaryAction, colMap);
                var usedAction = primaryAction;
                var altCtrl = _bspFindActionControl(tbl, targetRow, secondaryAction, colMap);
                if (!chosen && altCtrl) {
                    chosen = altCtrl;
                    usedAction = secondaryAction;
                    altCtrl = null;
                }

                if (!chosen) {
                    return JSON.stringify({ ok: false, error: 'Action button "' + primaryAction + '" not found for ' + $qName });
                }

                var fallbackTarget = altCtrl ? _bspExtractStaticTarget(altCtrl) : null;
                var staticTarget = _bspExtractStaticTarget(chosen);
                if (staticTarget) {
                    return JSON.stringify({
                        ok: true,
                        mode: staticTarget.mode,
                        url: staticTarget.url || '',
                        method: staticTarget.method || 'GET',
                        params: staticTarget.params || [],
                        source: staticTarget.source || '',
                        usedAction: usedAction,
                        fallback: fallbackTarget
                    });
                }

                // Dynamic JS handler fallback: keep main tab locked and intercept window.open / form.submit / anchor navigation WITHOUT navigating portalWebView
                window.__bspAutomationMainTabLock = true;
                window.__bspBgCaptureActive = true;
                window.__bspCapturedNav = null;

                try {
                    if (typeof chosen.click === 'function') {
                        chosen.click();
                    } else {
                        chosen.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    }
                } finally {
                    window.__bspBgCaptureActive = false;
                }

                if (window.__bspCapturedNav) {
                    var cap = window.__bspCapturedNav;
                    return JSON.stringify({
                        ok: true,
                        mode: cap.mode || 'url',
                        url: cap.url || '',
                        method: cap.method || 'GET',
                        params: cap.params || [],
                        html: cap.html || '',
                        baseUrl: cap.baseUrl || '',
                        source: cap.source || 'sync_capture',
                        usedAction: usedAction,
                        fallback: fallbackTarget
                    });
                }

                // Check if click opened an in-page Bootstrap dialog / modal
                var modalEl = document.querySelector('.modal.show, .modal.in, [role="dialog"]:not([hidden])');
                if (modalEl) {
                    var fr = modalEl.querySelector('iframe[src]');
                    var frSrc = fr ? _bspResolveUrl(fr.getAttribute('src')) : '';
                    if (frSrc) {
                        return JSON.stringify({
                            ok: true,
                            mode: 'url',
                            url: frSrc,
                            source: 'modal_iframe',
                            closeModalFirst: true,
                            usedAction: usedAction,
                            fallback: fallbackTarget
                        });
                    }
                    var modalBody = modalEl.querySelector('.modal-body') || modalEl;
                    var modalText = (modalBody.innerText || '');
                    if (/Summative|Progress\s+Report|Marksheet|Roll/i.test(modalText)) {
                        var headHtml = Array.from(document.querySelectorAll('head link[rel="stylesheet"], head style'))
                            .map(function(n) { return n.outerHTML; }).join('\n');
                        var docHtml = '<!DOCTYPE html><html><head><meta charset="utf-8">' + headHtml + '</head><body>' + modalBody.innerHTML + '</body></html>';
                        return JSON.stringify({
                            ok: true,
                            mode: 'html',
                            html: docHtml,
                            baseUrl: window.location.href,
                            closeModalFirst: true,
                            source: 'modal_html_clone',
                            usedAction: usedAction,
                            fallback: fallbackTarget
                        });
                    }
                }

                // Async handler (e.g. jQuery AJAX -> window.open or form.submit or redirect caught while __bspAutomationMainTabLock is active)
                return JSON.stringify({
                    ok: true,
                    mode: 'async_pending',
                    source: 'async_hook',
                    usedAction: usedAction,
                    fallback: fallbackTarget
                });
            })();
        """.trimIndent()
    }

    fun suppressAutoPrintJs(): String = """
        (function() {
            if (!window.__bspOrigPrint) {
                window.__bspOrigPrint = window.print;
            }
            window.print = function() {
                window.__bspPrintTriggered = true;
            };
            return 'ok';
        })();
    """.trimIndent()

    fun checkMarksheetRenderJs(): String = """
        (function() {
            ${sameSessionInterceptorCoreJs()}
            if (!window.__bspOrigPrint) {
                window.__bspOrigPrint = window.print;
            }
            window.print = function() {
                window.__bspPrintTriggered = true;
            };

            function collectBodyTextAndDoc() {
                var txt = (document.body && document.body.innerText) ? document.body.innerText : '';
                var docToCheck = document;
                var frames = Array.from(document.querySelectorAll('iframe'));
                for (var f = 0; f < frames.length; f++) {
                    try {
                        var fDoc = frames[f].contentDocument;
                        if (fDoc && fDoc.body && (fDoc.body.innerText || '').length > 100) {
                            txt += '\n' + fDoc.body.innerText;
                            if (/Summative\s+Evaluation/i.test(fDoc.body.innerText)) {
                                docToCheck = fDoc;
                            }
                        }
                    } catch (e) {}
                }
                return { bodyTxt: txt, doc: docToCheck };
            }

            var ctx = collectBodyTextAndDoc();
            var bodyTxt = ctx.bodyTxt;
            var doc = ctx.doc;

            var hasAuthForm = !!doc.querySelector('input[type="password"], input[name*="captcha" i], img[src*="captcha" i]');
            var hasPortalError = /An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered|404\s+Page\s+Not\s+Found/i.test(bodyTxt);

            var hasStudentListTable = !!doc.querySelector('table[data-bsp-student-table="true"]') ||
                (/Showing\s+\d+\s+to\s+\d+\s+of\s+\d+\s+entries/i.test(bodyTxt) &&
                 !/Summative\s+Evaluation/i.test(bodyTxt) &&
                 !/Progress\s+Report\s+Card/i.test(bodyTxt));

            var hasSummative = /Summative\s+Evaluation/i.test(bodyTxt);
            var hasReportHeader = /Progress\s+Report|Holistic\s+Report|Mark\s*sheet|Banglarshiksha|Academic\s+Year|Evaluation/i.test(bodyTxt);
            var hasSheetContainer = !!doc.querySelector('section.sheet, .sheet, .print-page, .marksheet-page, #printableArea, #printArea');
            var hasModalOpen = !!document.querySelector('.modal.show, .modal.in, [role="dialog"]:not([hidden])');

            // Accurate computed visibility check so CSS-hidden .loading/.spinner elements never block rendering
            var spinnerVisible = false;
            var spinnerEls = doc.querySelectorAll('.loading, .spinner, .spinner-border, #loader, .dataTables_processing');
            for (var s = 0; s < spinnerEls.length; s++) {
                var el = spinnerEls[s];
                try {
                    var st = window.getComputedStyle(el);
                    var rect = el.getBoundingClientRect();
                    if (st.display !== 'none' && st.visibility !== 'hidden' && parseFloat(st.opacity || '1') > 0.05 &&
                        el.offsetParent !== null && rect.width > 4 && rect.height > 4) {
                        spinnerVisible = true;
                        break;
                    }
                } catch (e) {}
            }

            var imgs = Array.from(doc.querySelectorAll('img'));
            var pendingImgs = imgs.filter(function(img) {
                var src = (img.getAttribute('src') || '').trim();
                if (!src || /^data:image\/svg/i.test(src)) return false;
                return !img.complete && img.offsetParent !== null;
            }).length;

            var looksLikeMarksheet = !hasPortalError && (!hasStudentListTable || hasModalOpen) &&
                (hasSummative || hasSheetContainer || (hasReportHeader && bodyTxt.length > 250) || window.__bspPrintTriggered === true);

            return JSON.stringify({
                readyState: document.readyState || '',
                hasAuthForm: hasAuthForm,
                hasPortalError: hasPortalError,
                hasStudentListTable: hasStudentListTable,
                hasModalOpen: hasModalOpen,
                hasSummative: hasSummative,
                looksLikeMarksheet: looksLikeMarksheet,
                spinnerVisible: spinnerVisible,
                pendingImgs: pendingImgs,
                url: location.href,
                textLen: bodyTxt.length
            });
        })();
    """.trimIndent()

    fun extractSummativeMarksJs(fallbackRoll: String, fallbackName: String): String {
        val qRoll = JSONObject.quote(fallbackRoll)
        val qName = JSONObject.quote(fallbackName)
        return """
(function() {
    var searchDocs = [document];
    Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
        try {
            if (fr.contentDocument && fr.contentDocument.body) searchDocs.push(fr.contentDocument);
        } catch (e) {}
    });

    var bodyTxt = searchDocs.map(function(d) {
        return (d.body && d.body.innerText) ? d.body.innerText : '';
    }).join('\n');

    var hasAuth = !!document.querySelector('input[type="password"], input[name*="captcha" i]');
    if (hasAuth) {
        return JSON.stringify({ ok: false, sessionExpired: true, error: 'Portal session expired' });
    }
    if (/An\s+Error\s+Was\s+Encountered|selected\s+a\s+database\s+type|A\s+PHP\s+Error\s+was\s+encountered/i.test(bodyTxt)) {
        return JSON.stringify({ ok: false, dbError: true, error: 'Portal database error displayed on page' });
    }

    function normalizeDigits(s) {
        var map = { '০':'0','১':'1','২':'2','৩':'3','৪':'4','৫':'5','৬':'6','৭':'7','৮':'8','৯':'9' };
        return String(s === undefined || s === null ? '' : s).replace(/[০-৯]/g, function(ch) { return map[ch] || ch; });
    }
    function cleanText(v) {
        return normalizeDigits(v).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }
    function trimNum(n) {
        return String(n).replace(/\.0+${'$'}/, '');
    }

    // A MARK is only: a plain number, or AB. Dashes, labels like SE(10) / (50), grades are NOT marks.
    function markCell(raw) {
        var s = cleanText(raw);
        if (!s) return '';
        if (/^[-–—*•]+${'$'}/.test(s)) return '';
        if (/^(?:ab|abs|absent)${'$'}/i.test(s)) return 'AB';
        if (/^\d+(?:\.\d+)?${'$'}/.test(s)) return trimNum(parseFloat(s));
        return '';
    }
    // "SE(10)" or "(50)" -> 10 / 50 (full marks label printed above the real marks)
    function labelNumber(raw) {
        var s = cleanText(raw);
        var m = s.match(/^(?:[A-Za-z]{1,3}\s*)?\(\s*(\d+(?:\.\d+)?)\s*\)${'$'}/);
        return m ? parseFloat(m[1]) : null;
    }
    // "99 / 100" -> "99"
    function obtainedOf(raw) {
        var s = cleanText(raw);
        var m = s.match(/^(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/);
        if (m) return trimNum(parseFloat(m[1]));
        return markCell(s);
    }

    function isNonSubjectLabel(text) {
        var s = cleanText(text).toLowerCase();
        if (!s || s.length < 2) return true;
        if (/^\d+(?:\.\d+)?${'$'}/.test(s)) return true;
        if (/\b(?:grand\s*total|sub\s*total|aggregate|total|percentage|position|rank|result|grade|division|remarks?|attendance|working\s*days|present|teacher|headmaster|guardian|signature|date\s*of|summative|formative|lpcd|bco|rubric|indicator|full\s*marks|obtained|marks?\s*obtained)\b/i.test(s)) {
            return true;
        }
        if (/^(?:subject|learning\s*area|subjects?|विषय|বিষয়)/i.test(s)) return true;
        return false;
    }

    function resolveSubjectDef(subjText) {
        var raw = cleanText(subjText);
        if (!raw || isNonSubjectLabel(raw)) return null;
        var s = raw.toLowerCase();
        if (/bengali|1st\s*lang|first\s*lang|\bbn\b|\bben\b|বাংলা/.test(s)) return { key: 'ben', code: 'BEN', displayName: raw };
        if (/english|2nd\s*lang|second\s*lang|\ben\b|\beng\b/.test(s)) return { key: 'eng', code: 'ENG', displayName: raw };
        if (/environment\s*(?:&|and)\s*geo|geography|ভূগোল/.test(s)) return { key: 'geo', code: 'GEO', displayName: raw };
        if (/environment\s*(?:&|and)\s*hist|history|ইতিহাস/.test(s)) return { key: 'hist', code: 'HIST', displayName: raw };
        if (/environment|our\s*env|\bevs\b|paribesh|poribesh|পরিবেশ/.test(s)) return { key: 'evs', code: 'EVS', displayName: raw };
        if (/physical\s*science|phys\.?\s*sci|ভৌত/.test(s)) return { key: 'psci', code: 'P.SCI', displayName: raw };
        if (/life\s*science|life\s*sci|জীবন\s*বিজ্ঞান/.test(s)) return { key: 'lsci', code: 'L.SCI', displayName: raw };
        if (/general\s*science|\bscience\b|বিজ্ঞান/.test(s)) return { key: 'sci', code: 'SCI', displayName: raw };
        if (/math|ganit|arithmetic|গণিত/.test(s)) return { key: 'math', code: 'MATH', displayName: raw };
        if (/health|physical\s*ed|hygiene|swasthya|স্বাস্থ্য/.test(s)) return { key: 'health', code: 'HEALTH', displayName: raw };
        if (/\bart\b|art\s*(?:&|and)|work\s*ed|drawing|craft|music|কর্ম|কলা/.test(s)) return { key: 'art', code: 'ART', displayName: raw };
        if (/sanskrit|সংস্কৃত/.test(s)) return { key: 'sans', code: 'SANS', displayName: raw };
        if (/hindi|হিন্দি/.test(s)) return { key: 'hin', code: 'HIN', displayName: raw };
        if (/urdu|উর্দু/.test(s)) return { key: 'urd', code: 'URD', displayName: raw };
        if (/3rd\s*lang|third\s*lang/.test(s)) return { key: 'lang3', code: '3RD.L', displayName: raw };
        if (/computer|ict|কম্পিউটার/.test(s)) return { key: 'comp', code: 'COMP', displayName: raw };
        if (/ability\s*to\s*communicate|communicate/.test(s)) return { key: 'comm', code: 'COMM', displayName: raw };
        if (/ability\s*to\s*correlate|correlate/.test(s)) return { key: 'corr', code: 'CORR', displayName: raw };
        if (/problem\s*solv/.test(s)) return { key: 'prob', code: 'PROB', displayName: raw };
        if (/coordination/.test(s)) return { key: 'coord', code: 'COORD', displayName: raw };

        var asciiWords = raw.replace(/[^A-Za-z0-9\s]/g, ' ').trim().split(/\s+/).filter(Boolean);
        var key = asciiWords.length > 0
            ? asciiWords.join('_').toLowerCase().slice(0, 18)
            : ('sub_' + Math.abs(raw.split('').reduce(function(a, b) { return ((a << 5) - a + b.charCodeAt(0)) | 0; }, 0)));
        var code = '';
        if (asciiWords.length === 1) code = asciiWords[0].toUpperCase().slice(0, 6);
        else if (asciiWords.length >= 2) code = asciiWords.map(function(w) { return w.slice(0, 3).toUpperCase(); }).join('').slice(0, 7);
        else code = raw.slice(0, 6).toUpperCase();
        return { key: key, code: code, displayName: raw };
    }

    // ---- Student identity ----
    var detectedName = $qName;
    var detectedRoll = $qRoll;
    var nameM = bodyTxt.match(/(?:Name\s*of\s*(?:the\s*)?Student|Student(?:'s)?\s*Name)\s*[:\-]\s*([^\n\r\t]+)/i);
    if (nameM && cleanText(nameM[1])) detectedName = cleanText(nameM[1]);
    else {
        var nameM2 = bodyTxt.match(/(?:^|[\n\t ])NAME\s*:\s*([^\n\r\t]+)/);
        if (nameM2 && cleanText(nameM2[1])) detectedName = cleanText(nameM2[1]);
    }
    var rollM = bodyTxt.match(/Roll\s*(?:No\.?|Number)?\s*[:\-]\s*([0-9A-Za-z\/\-]+)/i);
    if (rollM && cleanText(rollM[1])) detectedRoll = cleanText(rollM[1]);
    var detectedClass = '';
    var classM = bodyTxt.match(/\bClass\s*[:\-]\s*(?:CLASS\s*)?([IVXLC]+|\d+)\b/i);
    if (classM) detectedClass = cleanText(classM[1]).toUpperCase();
    var detectedSection = '';
    var secM = bodyTxt.match(/\bSection\s*[:\-]\s*([A-Za-z0-9]+)/i);
    if (secM) detectedSection = cleanText(secM[1]).toUpperCase();

    // ---- Grid builder (rowspan + colspan aware) ----
    function buildGrid(tbl) {
        var trs = Array.from(tbl.rows);
        var grid = [];
        var pend = {};
        trs.forEach(function(tr) {
            var row = [];
            var c = 0;
            function fill() {
                while (pend[c] && pend[c].left > 0) {
                    row[c] = { text: '', spanned: true };
                    pend[c].left--;
                    c++;
                }
            }
            Array.from(tr.cells).forEach(function(cell) {
                fill();
                var cs = Math.min(parseInt(cell.getAttribute('colspan') || '1', 10) || 1, 12);
                var rs = Math.min(parseInt(cell.getAttribute('rowspan') || '1', 10) || 1, 12);
                row[c] = { text: cleanText(cell.innerText || cell.textContent || ''), spanned: false };
                for (var k = 1; k < cs; k++) row[c + k] = { text: '', spanned: true };
                if (rs > 1) {
                    for (var k2 = 0; k2 < cs; k2++) pend[c + k2] = { left: rs - 1 };
                }
                c += cs;
            });
            fill();
            grid.push(row);
        });
        return grid;
    }

    // ---- Locate the Summative Evaluation table ----
    var allTables = [];
    searchDocs.forEach(function(d) { allTables = allTables.concat(Array.from(d.querySelectorAll('table'))); });

    var target = null;
    var bestScore = -1;
    allTables.forEach(function(tbl) {
        var grid;
        try { grid = buildGrid(tbl); } catch (e) { return; }
        if (!grid.length) return;
        var titleHit = false, subjRow = -1;
        for (var r = 0; r < Math.min(grid.length, 6); r++) {
            for (var c = 0; c < grid[r].length; c++) {
                var t = grid[r][c] ? grid[r][c].text : '';
                if (/^summative\s*evaluation${'$'}/i.test(t)) titleHit = true;
                if (subjRow < 0 && /^(?:name\s*of\s*the\s*)?subjects?${'$'}/i.test(t)) subjRow = r;
            }
        }
        if (subjRow < 0) return;
        var headerText = grid[subjRow].map(function(x) { return x ? x.text : ''; }).join(' ');
        if (/\bF[1-3][A-C]\b/.test(headerText)) return;            // Formative table
        if (!/total/i.test(headerText)) return;                    // Summative has Total column
        var score = (titleHit ? 20 : 0) + 10;
        if (tbl.querySelector('table')) score -= 12;               // nested containers are not leaf tables
        if (score > bestScore) { bestScore = score; target = { tbl: tbl, grid: grid, subjRow: subjRow }; }
    });

    if (!target) {
        return JSON.stringify({ ok: false, sessionExpired: false, error: 'Summative Evaluation table not found on marksheet view.' });
    }

    var grid = target.grid;
    var hr = target.subjRow;
    var width = 0;
    grid.forEach(function(r) { width = Math.max(width, r.length); });

    // header text per column (supports a second header row containing only "(20)" style labels)
    var headerRowCount = 1;
    var colHeader = [];
    for (var c0 = 0; c0 < width; c0++) {
        colHeader[c0] = (grid[hr][c0] ? grid[hr][c0].text : '');
    }
    if (grid[hr + 1]) {
        var next = grid[hr + 1];
        var onlyLabels = true, anyCell = false;
        for (var c1 = 0; c1 < width; c1++) {
            var tx = next[c1] ? next[c1].text : '';
            if (tx) { anyCell = true; if (!/^\(?\s*\d+(?:\.\d+)?\s*\)?${'$'}/.test(tx) && !/^\(?%\)?${'$'}/.test(tx)) onlyLabels = false; }
        }
        if (anyCell && onlyLabels) {
            headerRowCount = 2;
            for (var c2 = 0; c2 < width; c2++) {
                if (next[c2] && next[c2].text) colHeader[c2] = (colHeader[c2] + ' ' + next[c2].text).trim();
            }
        }
    }

    var col = { subject: -1, t1: -1, t2: -1, t3: -1, total: -1, pct: -1 };
    var hdrMax = { t1: 0, t2: 0, t3: 0, total: 0 };
    function headerMaxOf(h) {
        var m = h.match(/\(\s*(\d+(?:\.\d+)?)\s*\)/);
        return m ? parseFloat(m[1]) : 0;
    }
    for (var c3 = 0; c3 < width; c3++) {
        var h = colHeader[c3] || '';
        if (col.subject < 0 && /^(?:name\s*of\s*the\s*)?subjects?\b/i.test(h)) { col.subject = c3; continue; }
        if (col.t3 < 0 && /^(?:III|3rd|third)\b/i.test(h)) { col.t3 = c3; hdrMax.t3 = headerMaxOf(h); continue; }
        if (col.t2 < 0 && /^(?:II|2nd|second)\b/i.test(h)) { col.t2 = c3; hdrMax.t2 = headerMaxOf(h); continue; }
        if (col.t1 < 0 && /^(?:I|1st|first)\b/i.test(h)) { col.t1 = c3; hdrMax.t1 = headerMaxOf(h); continue; }
        if (col.total < 0 && /^total\b/i.test(h)) { col.total = c3; hdrMax.total = headerMaxOf(h); continue; }
        if (col.pct < 0 && /percent|%/i.test(h)) { col.pct = c3; continue; }
    }
    if (col.subject < 0) col.subject = 0;
    // positional fallback if numerals were not recognisable
    if (col.t1 < 0 || col.t2 < 0 || col.t3 < 0) {
        var free = [];
        for (var c4 = col.subject + 1; c4 < width; c4++) if (c4 !== col.total && c4 !== col.pct) free.push(c4);
        if (col.t1 < 0 && free.length > 0) col.t1 = free[0];
        if (col.t2 < 0 && free.length > 1) col.t2 = free[1];
        if (col.t3 < 0 && free.length > 2) col.t3 = free[2];
    }
    if (hdrMax.t1 <= 0) hdrMax.t1 = 20;
    if (hdrMax.t2 <= 0) hdrMax.t2 = 30;
    if (hdrMax.t3 <= 0) hdrMax.t3 = 50;
    if (hdrMax.total <= 0) hdrMax.total = hdrMax.t1 + hdrMax.t2 + hdrMax.t3;

    function cellAt(row, c) { return (c >= 0 && row[c]) ? row[c].text : ''; }

    // ---- Group rows into subject blocks ----
    var blocks = [];
    var cur = null;
    var grandRow = null;
    for (var r2 = hr + headerRowCount; r2 < grid.length; r2++) {
        var row = grid[r2];
        var rowHasAny = false;
        for (var q = 0; q < width; q++) if (row[q] && row[q].text) { rowHasAny = true; break; }
        if (!rowHasAny) continue;
        var nameCell = row[col.subject];
        var nameTxt = (nameCell && !nameCell.spanned) ? nameCell.text : '';
        if (/^(?:grand\s*total|sub\s*total|aggregate|total)\b/i.test(nameTxt)) { grandRow = row; cur = null; continue; }
        if (nameTxt) {
            cur = { name: nameTxt, rows: [row] };
            blocks.push(cur);
        } else if (cur) {
            cur.rows.push(row);
        }
    }

    var orderedSubjects = [];
    var subjectsMap = {};
    blocks.forEach(function(block) {
        var def = resolveSubjectDef(block.name);
        if (!def) return;
        var labelMax = { t1: 0, t2: 0, t3: 0, total: 0 };
        var markRow = null;
        block.rows.forEach(function(row) {
            var anyLabel = false;
            ['t1', 't2', 't3', 'total'].forEach(function(k) {
                var ln = labelNumber(cellAt(row, col[k]));
                if (ln !== null) { anyLabel = true; labelMax[k] = ln; }
            });
            if (!anyLabel) markRow = row;   // the row that carries the real marks
        });
        if (!markRow) return;

        var t1 = markCell(cellAt(markRow, col.t1));
        var t2 = markCell(cellAt(markRow, col.t2));
        var t3 = markCell(cellAt(markRow, col.t3));
        var tot = markCell(cellAt(markRow, col.total));

        var m1 = labelMax.t1 || hdrMax.t1;
        var m2 = labelMax.t2 || hdrMax.t2;
        var m3 = labelMax.t3 || hdrMax.t3;
        var mt = labelMax.total || (labelMax.t1 || labelMax.t2 || labelMax.t3 ? (m1 + m2 + m3) : hdrMax.total);

        var finalKey = def.key;
        if (subjectsMap[finalKey]) {
            var suffix = 2;
            while (subjectsMap[finalKey + '_' + suffix]) suffix++;
            finalKey = finalKey + '_' + suffix;
        }
        orderedSubjects.push({ key: finalKey, code: def.code, displayName: def.displayName, maxT1: m1, maxT2: m2, maxT3: m3, maxTotal: mt });
        subjectsMap[finalKey] = { t1: t1, t2: t2, t3: t3, total: tot };
    });

    // ---- Printed Grand Total row (used only as fallback by the app) ----
    var printed = { t1: '', t2: '', t3: '', grandTotal: '', percentage: '', position: '' };
    if (grandRow) {
        printed.t1 = obtainedOf(cellAt(grandRow, col.t1));
        printed.t2 = obtainedOf(cellAt(grandRow, col.t2));
        printed.t3 = obtainedOf(cellAt(grandRow, col.t3));
        printed.grandTotal = obtainedOf(cellAt(grandRow, col.total));
        var pctTxt = cleanText(cellAt(grandRow, col.pct)).replace('%', '').trim();
        if (/^\d+(?:\.\d+)?${'$'}/.test(pctTxt)) printed.percentage = pctTxt;
    }
    // a term with no marks anywhere must not show a printed "0"
    ['t1', 't2', 't3'].forEach(function(k) {
        var any = orderedSubjects.some(function(s) { return !!subjectsMap[s.key][k]; });
        if (!any) printed[k] = '';
    });
    var posBody = bodyTxt.match(/(?:Position|Rank\s*in\s*Class)\s*[:\-]?\s*(\d+)/i);
    if (posBody) printed.position = posBody[1];

    function headerLabel(prefix, keyName) {
        var uniq = [];
        orderedSubjects.forEach(function(s) { var v = s[keyName]; if (v > 0 && uniq.indexOf(v) === -1) uniq.push(v); });
        return prefix + ' (' + (uniq.length > 0 ? uniq.join('/') : '-') + ')';
    }

    return JSON.stringify({
        ok: true,
        sessionExpired: false,
        rollNo: detectedRoll,
        studentName: detectedName,
        className: detectedClass,
        section: detectedSection,
        matchedSubjectCount: orderedSubjects.length,
        orderedSubjects: orderedSubjects,
        subjects: subjectsMap,
        termHeaders: {
            term1Header: headerLabel('1ST SUMMATIVE', 'maxT1'),
            term2Header: headerLabel('2ND SUMMATIVE', 'maxT2'),
            term3Header: headerLabel('3RD SUMMATIVE', 'maxT3')
        },
        printed: printed
    });
})();

""".trimIndent()
    }

    fun tryCloseInPageMarksheetOrModalJs(): String = """
        (function() {
            var closeSelectors = [
                '.modal.show .close',
                '.modal.show .btn-close',
                '.modal.show [data-dismiss="modal"]',
                '.modal.show [data-bs-dismiss="modal"]',
                '.modal.in .close',
                '.modal.in [data-dismiss="modal"]',
                '[role="dialog"]:not([hidden]) button.close',
                '[role="dialog"]:not([hidden]) .btn-close'
            ];
            for (var i = 0; i < closeSelectors.length; i++) {
                var btn = document.querySelector(closeSelectors[i]);
                if (btn && btn.offsetParent !== null) {
                    btn.click();
                    return JSON.stringify({ handled: true, mode: 'modal_closed' });
                }
            }

            var bodyTxt = (document.body && document.body.innerText) ? document.body.innerText : '';
            var onMarksheetView = /Summative\s+Evaluation|Progress\s+Report|Holistic\s+Report|Mark\s*sheet/i.test(bodyTxt) ||
                !!document.querySelector('section.sheet, .sheet, .print-page, .marksheet-page, #printableArea, #printArea');
            var onStudentList = !!document.querySelector('table[data-bsp-student-table="true"]') ||
                /Showing\s+\d+\s+to\s+\d+\s+of\s+\d+\s+entries/i.test(bodyTxt);

            if (onMarksheetView && !onStudentList) {
                var actionButtons = Array.from(document.querySelectorAll('a, button'));
                for (var j = 0; j < actionButtons.length; j++) {
                    var el = actionButtons[j];
                    var txt = (el.innerText || el.title || '').trim().toLowerCase();
                    if (/^(back|back\s*to\s*list|return|close)$/.test(txt) && el.offsetParent !== null) {
                        el.click();
                        return JSON.stringify({ handled: true, mode: 'back_button_clicked' });
                    }
                }
                return JSON.stringify({ handled: false, onMarksheetView: true });
            }
            return JSON.stringify({ handled: false, onMarksheetView: false });
        })();
    """.trimIndent()

    fun clickPaginationPageJs(targetPage: Int, mode: String = "next"): String = """
        (function() {
            var currentActive = document.querySelector('.dataTables_paginate .paginate_button.active, .pagination li.active a, .pagination li.active span, .paginate_button.current');
            var currentPage = currentActive ? (parseInt((currentActive.innerText || '').trim(), 10) || 1) : 1;

            if ($targetPage > 0 && currentPage === $targetPage && '$mode' === 'exact') {
                return JSON.stringify({ ok: true, alreadyOnPage: true, page: currentPage });
            }

            var firstRowBefore = '';
            var tbl = document.querySelector('table[data-bsp-student-table="true"]') || document.querySelector('table');
            if (tbl) {
                var firstRow = tbl.querySelector('tbody tr');
                if (firstRow) firstRowBefore = (firstRow.innerText || '').trim();
            }

            if ('$mode' === 'exact' && $targetPage > 0) {
                var numBtns = Array.from(document.querySelectorAll('.dataTables_paginate .paginate_button a, .dataTables_paginate .paginate_button, .pagination li a, .pagination li button'));
                var exactBtn = numBtns.find(function(b) {
                    return (b.innerText || '').trim() === String($targetPage) && !/disabled/i.test(b.className || '');
                });
                if (exactBtn) {
                    exactBtn.click();
                    return JSON.stringify({ ok: true, clicked: true, mode: 'exact', prevPage: currentPage, prevFirstRow: firstRowBefore });
                }
                if (currentPage < $targetPage) {
                    var stepNext = document.querySelector('.dataTables_paginate .paginate_button.next:not(.disabled) a, .dataTables_paginate .paginate_button.next:not(.disabled), li.next:not(.disabled) a, a.next:not(.disabled), button.next:not([disabled])');
                    if (stepNext) {
                        var snParent = (stepNext.parentElement && stepNext.parentElement.className) || '';
                        if (!/disabled/i.test((stepNext.className || '') + ' ' + snParent)) {
                            stepNext.click();
                            return JSON.stringify({ ok: true, clicked: true, mode: 'step_next', prevPage: currentPage, prevFirstRow: firstRowBefore });
                        }
                    }
                } else if (currentPage > $targetPage) {
                    var stepPrev = document.querySelector('.dataTables_paginate .paginate_button.previous:not(.disabled) a, .dataTables_paginate .paginate_button.previous:not(.disabled), li.previous:not(.disabled) a, a.previous:not(.disabled), button.previous:not([disabled])');
                    if (stepPrev) {
                        var spParent = (stepPrev.parentElement && stepPrev.parentElement.className) || '';
                        if (!/disabled/i.test((stepPrev.className || '') + ' ' + spParent)) {
                            stepPrev.click();
                            return JSON.stringify({ ok: true, clicked: true, mode: 'step_prev', prevPage: currentPage, prevFirstRow: firstRowBefore });
                        }
                    }
                }
            }

            var nextBtn = document.querySelector('.dataTables_paginate .paginate_button.next:not(.disabled) a, .dataTables_paginate .paginate_button.next:not(.disabled), li.next:not(.disabled) a, a.next:not(.disabled), button.next:not([disabled])');
            if (nextBtn) {
                var parentCls = (nextBtn.parentElement && nextBtn.parentElement.className) || '';
                if (!/disabled/i.test((nextBtn.className || '') + ' ' + parentCls)) {
                    nextBtn.click();
                    return JSON.stringify({ ok: true, clicked: true, mode: 'next', prevPage: currentPage, prevFirstRow: firstRowBefore });
                }
            }

            return JSON.stringify({ ok: false, error: 'Next page button not found or disabled' });
        })();
    """.trimIndent()

    fun restoreHrpcFiltersJs(snapshotJson: String): String {
        val qSnapshot = JSONObject.quote(snapshotJson)
        return """
            (function() {
                if (window.__bspRestoringFilters) {
                    return JSON.stringify({ triggeredSearch: true, alreadyRestoring: true });
                }
                var snap = JSON.parse($qSnapshot || '{}');

                function findSelect(patterns) {
                    var selects = Array.from(document.querySelectorAll('select'));
                    for (var p = 0; p < patterns.length; p++) {
                        var rx = patterns[p];
                        for (var i = 0; i < selects.length; i++) {
                            var s = selects[i];
                            var lbl = '';
                            if (s.id) {
                                var l = document.querySelector('label[for="' + s.id + '"]');
                                if (l) lbl = l.innerText || '';
                            }
                            if (!lbl && s.closest('.form-group, td, div')) {
                                lbl = (s.closest('.form-group, td, div').innerText || '').split('\n')[0] || '';
                            }
                            var meta = ((s.name || '') + ' ' + (s.id || '') + ' ' + lbl).toLowerCase();
                            if (rx.test(meta)) return s;
                        }
                    }
                    return null;
                }

                function setSelectValue(patterns, wanted) {
                    if (!wanted || (!wanted.value && !wanted.text)) return { found: false, changed: false, missingOption: false };
                    var s = findSelect(patterns);
                    if (!s) return { found: false, changed: false, missingOption: false };
                    if (wanted.value && s.value === wanted.value) return { found: true, changed: false, missingOption: false };
                    var opts = Array.from(s.options || []);
                    var match = opts.find(function(o) { return wanted.value && o.value === wanted.value; }) ||
                        opts.find(function(o) { return wanted.text && (o.text || '').trim().toLowerCase() === wanted.text.toLowerCase(); });
                    if (match && s.value !== match.value) {
                        s.value = match.value;
                        s.dispatchEvent(new Event('change', { bubbles: true }));
                        if (window.jQuery) {
                            try { window.jQuery(s).val(match.value).trigger('change'); } catch(e) {}
                        }
                        return { found: true, changed: true, missingOption: false };
                    }
                    return { found: true, changed: false, missingOption: !match && !!(wanted.value || wanted.text) };
                }

                function findSearchBtn() {
                    var btns = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], a.btn'));
                    return btns.find(function(b) {
                        var txt = (b.innerText || b.value || '').trim().toLowerCase();
                        return /^(search|show|submit|get\s*details|find)${'$'}/.test(txt);
                    }) || btns.find(function(b) {
                        var txt = (b.innerText || b.value || '').trim().toLowerCase();
                        return /\b(search|show|get\s*details)\b/.test(txt) && !/reset|clear|back|logout|cancel/.test(txt);
                    });
                }

                var hasTableRows = !!document.querySelector('table tbody tr td') &&
                    /Showing\s+\d+\s+to\s+\d+/i.test((document.body && document.body.innerText) || '');

                var rYear = setSelectValue([/academic|session|year/], snap.academicYear);
                var rMed = setSelectValue([/medium/], snap.medium);
                var rCls = setSelectValue([/\bclass\b|grade/], snap.className);
                var rSec = setSelectValue([/section/], snap.section);
                var changed = rYear.changed || rMed.changed || rCls.changed || rSec.changed;

                if (changed || !hasTableRows) {
                    var searchBtn = findSearchBtn();
                    if (searchBtn) {
                        window.__bspRestoringFilters = true;
                        var attempts = 0;
                        function waitCascadeAndClick() {
                            attempts++;
                            if (rMed.missingOption) rMed = setSelectValue([/medium/], snap.medium);
                            if (rCls.missingOption) rCls = setSelectValue([/\bclass\b|grade/], snap.className);
                            if (rSec.missingOption) rSec = setSelectValue([/section/], snap.section);

                            var stillWaitingAjax = (rMed.missingOption || rCls.missingOption || rSec.missingOption) && attempts < 10;
                            if (stillWaitingAjax) {
                                setTimeout(waitCascadeAndClick, 180);
                                return;
                            }
                            var btnNow = findSearchBtn();
                            window.__bspRestoringFilters = false;
                            if (btnNow) btnNow.click();
                        }
                        setTimeout(waitCascadeAndClick, changed ? 320 : 120);
                        return JSON.stringify({ triggeredSearch: true, changed: changed });
                    }
                }
                return JSON.stringify({ triggeredSearch: false, changed: changed, hasTableRows: hasTableRows });
            })();
        """.trimIndent()
    }

    fun inspectHprcFillContextJs(): String = """
        (function() {
            var docs = [document];
            Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
                try {
                    if (fr.contentDocument && fr.contentDocument.body) docs.push(fr.contentDocument);
                } catch (e) {}
            });

            var activeDoc = document;
            var bestTable = null;
            var bestWritableCount = 0;

            function isWritableMarkCtrl(el) {
                if (!el || el.disabled || el.readOnly) return false;
                if (el.offsetParent === null) return false;
                if (el.tagName === 'SELECT') return true;
                if (el.tagName === 'INPUT') {
                    var t = (el.type || 'text').toLowerCase();
                    if (t !== 'text' && t !== 'number' && t !== 'tel' && t !== '') return false;
                    var meta = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
                    if (/search|filter|captcha|password|login/.test(meta)) return false;
                    return true;
                }
                return false;
            }

            docs.forEach(function(d) {
                Array.from(d.querySelectorAll('table')).forEach(function(tbl) {
                    var ctrls = Array.from(tbl.querySelectorAll('tbody input, tbody select, tr input, tr select')).filter(isWritableMarkCtrl);
                    if (ctrls.length > bestWritableCount) {
                        bestWritableCount = ctrls.length;
                        bestTable = tbl;
                        activeDoc = d;
                    }
                });
            });

            function selectedLabel(patterns) {
                for (var di = 0; di < docs.length; di++) {
                    var selects = Array.from(docs[di].querySelectorAll('select'));
                    for (var pi = 0; pi < patterns.length; pi++) {
                        for (var si = 0; si < selects.length; si++) {
                            var s = selects[si];
                            var lbl = '';
                            if (s.id) {
                                var l = docs[di].querySelector('label[for="' + s.id + '"]');
                                if (l) lbl = l.innerText || '';
                            }
                            var meta = ((s.name || '') + ' ' + (s.id || '') + ' ' + lbl).toLowerCase();
                            if (patterns[pi].test(meta) && s.selectedIndex >= 0) {
                                var opt = s.options[s.selectedIndex];
                                var txt = opt ? (opt.text || '').trim() : '';
                                if (txt && !/^--?\s*select/i.test(txt)) return txt;
                            }
                        }
                    }
                }
                return '';
            }

            var scopeParts = [];
            docs.forEach(function(d) {
                Array.from(d.querySelectorAll('h1, h2, h3, h4, h5, .box-title, .page-title, .panel-title, caption, legend')).forEach(function(h) {
                    scopeParts.push((h.innerText || '').replace(/\s+/g, ' ').trim());
                });
            });
            if (bestTable) {
                scopeParts.push(Array.from(bestTable.querySelectorAll('th')).map(function(th) { return (th.innerText || '').replace(/\s+/g, ' ').trim(); }).join(' '));
            }
            scopeParts.push(selectedLabel([/evaluation|assessment|category|exam/]));
            var pageText = scopeParts.join('\n');
            var urlLower = (location.href || '').toLowerCase();
            var detectedCategory = '';
            if (/formative|\bfee?\b/.test(urlLower) || /formative\s+evaluation/i.test(pageText)) {
                detectedCategory = 'Formative';
            } else if (/lpcd/.test(urlLower) || /\blpcd\b|qualitative\s+indicator/i.test(pageText)) {
                detectedCategory = 'LPCD';
            } else if (/bco/.test(urlLower) || /\bbco\b|co-?curricular|\bpart\s*[-:]?\s*a\b/i.test(pageText)) {
                detectedCategory = 'BCO';
            } else if (/summative|marks_entry|mark_entry/.test(urlLower) || /summative\s+evaluation/i.test(pageText)) {
                detectedCategory = 'Summative';
            }

            var headerLabels = [];
            if (bestTable) {
                var headThs = Array.from(bestTable.querySelectorAll('thead tr:last-child th, thead th, tr:first-child th'));
                headerLabels = headThs.map(function(th) { return (th.innerText || '').replace(/\s+/g, ' ').trim(); });
            }

            var rows = [];
            if (bestTable) {
                var bodyRows = Array.from(bestTable.querySelectorAll('tbody tr'));
                if (bodyRows.length === 0) bodyRows = Array.from(bestTable.querySelectorAll('tr')).slice(1);

                var writableRowCounter = 0;
                bodyRows.forEach(function(tr) {
                    var ctrls = Array.from(tr.querySelectorAll('input, select')).filter(isWritableMarkCtrl);
                    if (ctrls.length === 0) return;

                    var tds = Array.from(tr.querySelectorAll('td'));
                    var roll = '';
                    var studentCode = '';
                    var studentName = '';

                    tds.forEach(function(td, ci) {
                        if (td.querySelector('input, select')) return;
                        var t = (td.innerText || '').replace(/\s+/g, ' ').trim();
                        if (!t) return;
                        if (!studentCode && /^\d{10,18}${'$'}/.test(t)) {
                            studentCode = t;
                        } else if (!roll && /^\d{1,4}${'$'}/.test(t) && ci <= 2) {
                            roll = t;
                        } else if (!studentName && /[A-Za-z]{2,}/.test(t) && !/^(male|female|yes|no|pass|fail)$/i.test(t)) {
                            studentName = t;
                        }
                    });

                    var fields = ctrls.map(function(el, fi) {
                        var cell = el.closest('td');
                        var colIdx = cell ? Array.from(tr.children).indexOf(cell) : -1;
                        var hdr = (colIdx >= 0 && colIdx < headerLabels.length) ? headerLabels[colIdx] : ('Col ' + (fi + 1));
                        return {
                            fieldIdx: fi,
                            label: hdr || el.name || el.id || ('Field ' + (fi + 1)),
                            tag: el.tagName,
                            max: el.getAttribute('max') || '',
                            currentVal: el.value || ''
                        };
                    });

                    rows.push({
                        rowIndex: writableRowCounter,
                        roll: roll,
                        studentCode: studentCode,
                        studentName: studentName,
                        fields: fields
                    });
                    writableRowCounter++;
                });
            }

            return JSON.stringify({
                ok: true,
                detected: bestWritableCount > 0,
                category: detectedCategory || 'Summative',
                academicYear: selectedLabel([/academic|session|year/]),
                medium: selectedLabel([/medium/]),
                className: selectedLabel([/\bclass\b|grade/]),
                section: selectedLabel([/section/]),
                subject: selectedLabel([/subject|language/]),
                term: selectedLabel([/term|summative|evaluation/]),
                portalUrl: location.href,
                writableRowCount: rows.length,
                rows: rows
            });
        })();
    """.trimIndent()

    fun applyHprcFillPlanJs(planJson: String): String {
        val qPlan = JSONObject.quote(planJson)
        return """
            (function() {
                var plan = JSON.parse($qPlan || '[]');
                var docs = [document];
                Array.from(document.querySelectorAll('iframe')).forEach(function(fr) {
                    try {
                        if (fr.contentDocument && fr.contentDocument.body) docs.push(fr.contentDocument);
                    } catch (e) {}
                });

                function isWritableMarkCtrl(el) {
                    if (!el || el.disabled || el.readOnly) return false;
                    if (el.offsetParent === null) return false;
                    if (el.tagName === 'SELECT') return true;
                    if (el.tagName === 'INPUT') {
                        var t = (el.type || 'text').toLowerCase();
                        if (t !== 'text' && t !== 'number' && t !== 'tel' && t !== '') return false;
                        var meta = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
                        if (/search|filter|captcha|password|login/.test(meta)) return false;
                        return true;
                    }
                    return false;
                }

                var bestTable = null;
                var bestCount = 0;
                docs.forEach(function(d) {
                    Array.from(d.querySelectorAll('table')).forEach(function(tbl) {
                        var c = Array.from(tbl.querySelectorAll('tbody input, tbody select, tr input, tr select')).filter(isWritableMarkCtrl).length;
                        if (c > bestCount) { bestCount = c; bestTable = tbl; }
                    });
                });

                if (!bestTable) {
                    return JSON.stringify({ ok: false, filledFields: 0, error: 'Writable portal marks table not found.' });
                }

                function setCtrlVal(el, val) {
                    if (el.tagName === 'SELECT') {
                        var opts = Array.from(el.options || []);
                        var target = opts.find(function(o) {
                            return o.value.toLowerCase() === String(val).toLowerCase() ||
                                (o.text || '').trim().toLowerCase() === String(val).trim().toLowerCase();
                        });
                        if (!target) return false;
                        el.value = target.value;
                    } else {
                        var maxAttr = parseFloat(el.getAttribute('max') || '');
                        var numV = parseFloat(val);
                        if (!isNaN(maxAttr) && !isNaN(numV) && numV > maxAttr) return false;
                        var proto = Object.getPrototypeOf(el);
                        var setter = Object.getOwnPropertyDescriptor(proto, 'value') && Object.getOwnPropertyDescriptor(proto, 'value').set;
                        if (setter) setter.call(el, String(val)); else el.value = String(val);
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    el.dispatchEvent(new Event('blur', { bubbles: true }));
                    return true;
                }

                var bodyRows = Array.from(bestTable.querySelectorAll('tbody tr'));
                if (bodyRows.length === 0) bodyRows = Array.from(bestTable.querySelectorAll('tr')).slice(1);
                var dataTrs = bodyRows.filter(function(tr) {
                    return Array.from(tr.querySelectorAll('input, select')).some(isWritableMarkCtrl);
                });

                var filledFields = 0;
                var updatedRows = 0;

                plan.forEach(function(entry) {
                    var tr = dataTrs[entry.rowIndex];
                    if (!tr) return;
                    var ctrls = Array.from(tr.querySelectorAll('input, select')).filter(isWritableMarkCtrl);
                    var rowChanged = false;
                    (entry.writes || []).forEach(function(w) {
                        var el = ctrls[w.fieldIdx];
                        if (el && setCtrlVal(el, w.value)) {
                            filledFields++;
                            rowChanged = true;
                        }
                    });
                    if (rowChanged) {
                        updatedRows++;
                        tr.style.outline = '2px solid #16a34a';
                    }
                });

                // Strictly DO NOT click any Save / Submit button automatically.
                return JSON.stringify({
                    ok: true,
                    updatedRows: updatedRows,
                    filledFields: filledFields
                });
            })();
        """.trimIndent()
    }
}
