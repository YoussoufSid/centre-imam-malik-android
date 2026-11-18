// Pure logic shared with the phone app (same rules as MemoLog.kt / Fees.kt). It never touches the page or Firebase,
// so it is unit-tested in CI (web/tests) with the same examples as the app's tests.

const AR_MONTHS = ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"];
const pad = (n) => String(n).padStart(2, "0");
const monthName = (m) => { const [y, mm] = m.split("-"); return `${AR_MONTHS[+mm - 1] ?? m} ${y}`; };
const shiftMonth = (m, d) => { const [y, mm] = m.split("-").map(Number); const t = new Date(y, mm - 1 + d, 1); return `${t.getFullYear()}-${pad(t.getMonth() + 1)}`; };
const academicMonths = (m) => { const [y, mm] = m.split("-").map(Number); const s = mm >= 9 ? y : y - 1; return Array.from({ length: 12 }, (_, i) => shiftMonth(`${s}-09`, i)); };
const yearLabel = (m) => { const a = academicMonths(m); return `${a[0].slice(0, 4)} / ${a[11].slice(0, 4)}`; };
const studentNumber = (id) => (/^[sS]\d+$/.test(id) ? id : String(id).slice(-6)).toUpperCase();
const staffNumber = (id) => { const m = /^staff_(\d+)$/.exec(id); return m ? "E" + String(+m[1]).padStart(3, "0") : String(id).slice(-6).toUpperCase(); };
const SURAHS = [["الفاتحة", 7], ["البقرة", 286], ["آل عمران", 200], ["النساء", 176], ["المائدة", 120], ["الأنعام", 165], ["الأعراف", 206], ["الأنفال", 75], ["التوبة", 129], ["يونس", 109], ["هود", 123], ["يوسف", 111], ["الرعد", 43], ["إبراهيم", 52], ["الحجر", 99], ["النحل", 128], ["الإسراء", 111], ["الكهف", 110], ["مريم", 98], ["طه", 135], ["الأنبياء", 112], ["الحج", 78], ["المؤمنون", 118], ["النور", 64], ["الفرقان", 77], ["الشعراء", 227], ["النمل", 93], ["القصص", 88], ["العنكبوت", 69], ["الروم", 60], ["لقمان", 34], ["السجدة", 30], ["الأحزاب", 73], ["سبأ", 54], ["فاطر", 45], ["يس", 83], ["الصافات", 182], ["ص", 88], ["الزمر", 75], ["غافر", 85], ["فصلت", 54], ["الشورى", 53], ["الزخرف", 89], ["الدخان", 59], ["الجاثية", 37], ["الأحقاف", 35], ["محمد", 38], ["الفتح", 29], ["الحجرات", 18], ["ق", 45], ["الذاريات", 60], ["الطور", 49], ["النجم", 62], ["القمر", 55], ["الرحمن", 78], ["الواقعة", 96], ["الحديد", 29], ["المجادلة", 22], ["الحشر", 24], ["الممتحنة", 13], ["الصف", 14], ["الجمعة", 11], ["المنافقون", 11], ["التغابن", 18], ["الطلاق", 12], ["التحريم", 12], ["الملك", 30], ["القلم", 52], ["الحاقة", 52], ["المعارج", 44], ["نوح", 28], ["الجن", 28], ["المزمل", 20], ["المدثر", 56], ["القيامة", 40], ["الإنسان", 31], ["المرسلات", 50], ["النبأ", 40], ["النازعات", 46], ["عبس", 42], ["التكوير", 29], ["الانفطار", 19], ["المطففين", 36], ["الانشقاق", 25], ["البروج", 22], ["الطارق", 17], ["الأعلى", 19], ["الغاشية", 26], ["الفجر", 30], ["البلد", 20], ["الشمس", 15], ["الليل", 21], ["الضحى", 11], ["الشرح", 8], ["التين", 8], ["العلق", 19], ["القدر", 5], ["البينة", 8], ["الزلزلة", 8], ["العاديات", 11], ["القارعة", 11], ["التكاثر", 8], ["العصر", 3], ["الهمزة", 9], ["الفيل", 5], ["قريش", 4], ["الماعون", 7], ["الكوثر", 3], ["الكافرون", 6], ["النصر", 3], ["المسد", 5], ["الإخلاص", 4], ["الفلق", 5], ["الناس", 6]];
const LOG_NEW = "حفظ جديد", LOG_REV = "مراجعة";
const GRADES = ["ممتاز", "جيد جدًا", "جيد", "مقبول", "يحتاج إعادة"];
const dParse = (s) => { const [y, m, d] = s.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d)); };
const dKey = (d) => d.toISOString().slice(0, 10);
const dAdd = (s, n) => { const d = dParse(s); d.setUTCDate(d.getUTCDate() + n); return dKey(d); };
const dm = (s) => `${s.slice(8, 10)}/${s.slice(5, 7)}`;
const TERM_NAMES = ["الفصل الأول", "الفصل الثاني", "الفصل الثالث", "الفصل الرابع"];
const academicStart = (d) => (+d.slice(5, 7) >= 9 ? +d.slice(0, 4) : +d.slice(0, 4) - 1);
const defaultTerms = (st) => [[`${st}-09-01`, `${st}-11-30`], [`${st}-12-01`, `${st + 1}-02-28`], [`${st + 1}-03-01`, `${st + 1}-05-31`], [`${st + 1}-06-01`, `${st + 1}-08-31`]]
  .map(([from, to], i) => ({ name: TERM_NAMES[i], from, to }));
const DEFAULT_OFF = [5, 7];
const WEEKDAYS = ["الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"];
const isoDow = (d) => (dParse(d).getUTCDay() + 6) % 7 + 1;
const isSchoolDay = (d, off, ev) => !off.includes(isoDow(d)) && !eventsOn(ev, d).some((e) => e.type === "عطلة");
const schoolDays = (from, to, off, ev) => { let n = 0; for (let d = from; d <= to; d = dAdd(d, 1)) if (isSchoolDay(d, off, ev)) n++; return n; };
const termIndexAt = (terms, d) => { const i = terms.findIndex((t) => d >= t.from && d <= t.to); if (i >= 0) return i;
  let k = -1; terms.forEach((t, j) => { if (t.from <= d) k = j; }); return k >= 0 ? k : 0; };
const eventsOn = (ev, d) => ev.filter((e) => d >= e.from && d <= (e.to || e.from));
const upcomingEvents = (ev, d, days = 30) => { const lim = dAdd(d, days); return ev.filter((e) => (e.to || e.from) >= d && e.from <= lim).sort((a, b) => a.from.localeCompare(b.from)); };
function periodRange(p, anchor, termsFor = defaultTerms) {
  const y = +anchor.slice(0, 4), mo = +anchor.slice(5, 7), start = mo >= 9 ? y : y - 1, yl = `\u2066${start} / ${start + 1}\u2069`;
  if (p === "DAY") return { from: anchor, to: anchor, label: `\u2066${anchor}\u2069` };
  if (p === "WEEK") { /* week: Saturday–Friday */ const back = (dParse(anchor).getUTCDay() + 1) % 7; const from = dAdd(anchor, -back), to = dAdd(from, 6);
    return { from, to, label: `أسبوع \u2066${dm(from)} – ${dm(to)}\u2069` }; }
  if (p === "MONTH") return { from: anchor.slice(0, 7) + "-01", to: anchor.slice(0, 7) + "-31", label: monthName(anchor.slice(0, 7)) };
  if (p === "TERM") { const ts = termsFor(start), t = ts[termIndexAt(ts, anchor)]; return { from: t.from, to: t.to, label: `${t.name} ${yl}` }; }
  return { from: `${start}-09-01`, to: `${start + 1}-08-31`, label: `العام الدراسي ${yl}` };
}
function shiftPeriod(p, anchor, delta, termsFor = defaultTerms) {
  if (p === "DAY") return dAdd(anchor, delta);
  if (p === "WEEK") return dAdd(anchor, 7 * delta);
  if (p === "MONTH") return shiftMonth(anchor.slice(0, 7), delta) + "-01";
  if (p === "TERM") { const st = academicStart(anchor), ts = termsFor(st), k = termIndexAt(ts, anchor) + delta;
    return k < 0 ? termsFor(st - 1)[3].from : k >= ts.length ? termsFor(st + 1)[0].from : ts[k].from; }
  return (+anchor.slice(0, 4) + delta) + anchor.slice(4);
}
const ayat = (l) => (l.fromAyah > 0 && l.toAyah >= l.fromAyah ? l.toAyah - l.fromAyah + 1 : 0);
const gradeScore = (g) => { const i = GRADES.indexOf(g); return i < 0 ? null : 5 - i; };
const gradeFromScore = (a) => GRADES[Math.min(GRADES.length - 1, Math.max(0, 5 - Math.round(a)))];
const logOrder = (a, b) => (a.date || "").localeCompare(b.date || "") || ((a.createdAt?.seconds ?? a.createdAt ?? 0) - (b.createdAt?.seconds ?? b.createdAt ?? 0));
function summarizeMemo(students, logs) {
  const by = {}; logs.forEach((l) => (by[l.studentId] = by[l.studentId] || []).push(l));
  return students.map((s) => {
    const l = (by[s.id] || []).slice().sort(logOrder), nw = l.filter((x) => x.type === LOG_NEW), rv = l.filter((x) => x.type === LOG_REV);
    const sc = l.map((x) => gradeScore(x.grade)).filter((x) => x != null);
    return { s, logs: l, newS: nw.length, revS: rv.length, newA: nw.reduce((a, x) => a + ayat(x), 0), revA: rv.reduce((a, x) => a + ayat(x), 0),
      surahs: [...new Set(nw.map((x) => x.surah).filter(Boolean))], avg: sc.length ? sc.reduce((a, b) => a + b, 0) / sc.length : null,
      days: new Set(l.map((x) => x.date)).size };
  });
}
function nextPosition(last) {
  if (!last || !last.surah) return null;
  const i = SURAHS.findIndex((x) => x[0] === last.surah); if (i < 0) return { surah: last.surah, from: 0, to: 0 };
  const max = SURAHS[i][1], size = Math.max(1, ayat(last));
  if (last.toAyah >= 1 && last.toAyah < max) { const f = last.toAyah + 1; return { surah: last.surah, from: f, to: Math.min(max, f + size - 1) }; }
  if (last.toAyah <= 0) return { surah: last.surah, from: 0, to: 0 };
  const prev = SURAHS[i > 0 ? i - 1 : i]; return { surah: prev[0], from: 1, to: Math.min(prev[1], size) };
}
const plainNum = (n) => Number(n || 0).toLocaleString("en-US").replace(/,/g, " ");
/** A month's fee is overdue from the due day (default the 25th) of that month. */
const isMonthDue = (m, t, dueDay = 25) => { const cur = t.slice(0, 7); return m < cur || (m === cur && +t.slice(8, 10) >= dueDay); };
/** Arrears of a student in an academic year: unpaid due months and the amount still owed (fee × due months − paid). */
function feeArrears(s, months, payments, t, dueDay = 25) {
  const mine = payments.filter((p) => p.type === "monthly" && p.studentId === s.id);
  const due = months.filter((m) => isMonthDue(m, t, dueDay));
  const unpaid = due.filter((m) => !mine.some((p) => p.month === m));
  return { s, months: unpaid, amount: Math.max(0, (s.monthlyFee || 0) * due.length - mine.reduce((a, p) => a + (p.amount || 0), 0)) };
}

export { LOG_REV, AR_MONTHS, monthName, shiftMonth, academicMonths, yearLabel, studentNumber, staffNumber, SURAHS, LOG_NEW, GRADES, dParse, dKey, dAdd, dm, TERM_NAMES, academicStart, defaultTerms, DEFAULT_OFF, WEEKDAYS, isoDow, isSchoolDay, schoolDays, termIndexAt, eventsOn, upcomingEvents, periodRange, shiftPeriod, ayat, gradeScore, gradeFromScore, logOrder, summarizeMemo, nextPosition, plainNum, isMonthDue, feeArrears };
