// Centre Imam Malik — web admin dashboard (same Firestore data as the Android app).
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  initializeFirestore, persistentLocalCache, collection, doc, onSnapshot, setDoc, deleteDoc, addDoc, writeBatch,
  getDocs, query, where
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js?v=11";

const ADMIN_EMAIL = "sidibey487@gmail.com";
const WELCOME = "أهلًا وسهلًا بكم في مركز الإمام مالك لتحفيظ القرآن الكريم والدراسات الإسلامية";
const HADITH = "«خيرُكم مَن تعلَّم القرآنَ وعلَّمه»";
const DUA = "اللهم اجزِ مؤسِّس هذا المركز الحاج بانيما يارا خيرَ الجزاء، واجعل هذا العمل في ميزان حسناته، " +
  "واغفر له ولوالديه، ولجميع المسلمين والمسلمات، الأحياء منهم والأموات. آمين";
const DEFAULT_NAME = "مركز الإمام مالك لتحفيظ القرآن الكريم والدراسات الإسلامية";
const AR_MONTHS = ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"];
const EXPENSE_CATEGORIES = ["رواتب المعلمين", "الإيجار", "الكهرباء والماء", "المصاحف والكتب", "الأدوات واللوازم", "الصيانة", "النقل", "مصروفات يومية", "أخرى"];

// ---------------------------------------------------------------- helpers
const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const pad = (n) => String(n).padStart(2, "0");
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const currentMonth = () => today().slice(0, 7);
const monthName = (m) => { const [y, mm] = m.split("-"); return `${AR_MONTHS[+mm - 1] ?? m} ${y}`; };
const shiftMonth = (m, d) => { const [y, mm] = m.split("-").map(Number); const t = new Date(y, mm - 1 + d, 1); return `${t.getFullYear()}-${pad(t.getMonth() + 1)}`; };
const academicMonths = (m) => { const [y, mm] = m.split("-").map(Number); const s = mm >= 9 ? y : y - 1; return Array.from({ length: 12 }, (_, i) => shiftMonth(`${s}-09`, i)); };
const yearLabel = (m) => { const a = academicMonths(m); return `${a[0].slice(0, 4)} / ${a[11].slice(0, 4)}`; };
// Left-to-right isolate so "1 520 000" is not shown as "000 520 1" inside Arabic text.
const num = (n) => "\u2066" + Number(n || 0).toLocaleString("en-US").replace(/,/g, " ") + "\u2069";
// "+30 000" / "−20 000": sign kept on the left of the number inside Arabic text.
const signed = (n) => n === 0 ? "0" : "\u2066" + (n > 0 ? "+" : "−") + Number(Math.abs(n)).toLocaleString("en-US").replace(/,/g, " ") + "\u2069";
const money = (n) => `${num(n)} ${state.settings.currency || "فرنك"}`;
// Card amount: big number on one line, small currency label.
const amt = (n) => `${num(n)}<span class="cur">${esc(state.settings.currency || "فرنك")}</span>`;
const ACTIVE = "نشط";

function toast(msg) {
  const t = $("#toast"); t.textContent = msg; t.classList.remove("hidden");
  clearTimeout(toast.timer); toast.timer = setTimeout(() => t.classList.add("hidden"), 3500);
}
function openModal(html, onMount) {
  $("#modal-box").innerHTML = html; $("#modal").classList.remove("hidden");
  $("#modal").onclick = (e) => { if (e.target.id === "modal") closeModal(); };
  onMount?.($("#modal-box"));
}
function closeModal() { $("#modal").classList.add("hidden"); $("#modal-box").innerHTML = ""; }
function confirmBox(text, onYes, yes = "حذف") {
  openModal(`<h3>تأكيد</h3><p>${esc(text)}</p><div class="modal-actions">
    <button class="btn danger" id="yes">${esc(yes)}</button><button class="btn outline" id="no">إلغاء</button></div>`, (b) => {
    b.querySelector("#yes").onclick = () => { closeModal(); onYes(); };
    b.querySelector("#no").onclick = closeModal;
  });
}
const fail = (e) => toast(String(e?.message || e).includes("permission") ? "ليست لديك صلاحية لتنفيذ هذا الإجراء" : "حدث خطأ: " + (e?.message || e));

// ---------------------------------------------------------------- setup check
if (String(firebaseConfig.apiKey).startsWith("PASTE")) {
  $("#app").innerHTML = `<div class="setup"><h2 style="color:var(--green)">إعداد لوحة الإدارة</h2>
  <p>لتعمل اللوحة يجب إضافة إعدادات تطبيق الويب من Firebase مرة واحدة:</p>
  <ol><li>Firebase Console ← ⚙️ Project settings ← General ← <b>Add app</b> ← أيقونة الويب <code>&lt;/&gt;</code>.</li>
  <li>اكتب اسمًا (مثل <code>centre-web</code>) ثم <b>Register app</b>، وانسخ قيمتي <code>apiKey</code> و<code>appId</code>.</li>
  <li>ضعهما في الملف <code>web/firebase-config.js</code>.</li>
  <li>Authentication ← Settings ← <b>Authorized domains</b> ← أضف نطاق الموقع.</li></ol></div>`;
  throw new Error("firebase config missing");
}

const fb = initializeApp(firebaseConfig);
const auth = getAuth(fb);
const db = initializeFirestore(fb, { localCache: persistentLocalCache() });

// ---------------------------------------------------------------- state
const state = {
  user: null, profile: null, settings: { name: DEFAULT_NAME, currency: "فرنك" },
  students: [], halaqat: [], payments: [], expenses: [], staff: [], payroll: [], unsub: [], loaded: false,
  filters: { q: "", group: "", feeMonth: currentMonth(), expMonth: currentMonth(), year: currentMonth(), payMonth: currentMonth() }
};
const isAdmin = () => state.user?.email?.toLowerCase() === ADMIN_EMAIL || state.profile?.role === "admin";
const isStaff = () => isAdmin() || state.profile?.role === "teacher";
const groups = () => [...new Set([...state.halaqat.map((h) => h.name), ...state.students.map((s) => s.group)].filter(Boolean))].sort();

function audit(action, details) {
  if (!isStaff()) return;
  addDoc(collection(db, "auditLog"), {
    action, details, by: state.profile?.name || state.user?.email || "", byUid: state.user?.uid || "", at: new Date()
  }).catch(() => {});
}

function listen() {
  state.unsub.forEach((u) => u()); state.unsub = [];
  const sub = (ref, fn) => state.unsub.push(onSnapshot(ref, fn, fail));
  sub(doc(db, "settings", "centre"), (s) => { if (s.exists()) state.settings = { name: DEFAULT_NAME, currency: "فرنك", ...s.data() }; render(); });
  sub(collection(db, "students"), (q) => {
    state.students = q.docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => (a.name || "").localeCompare(b.name || "", "ar"));
    state.loaded = true; render();
  });
  sub(collection(db, "halaqat"), (q) => { state.halaqat = q.docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => a.name.localeCompare(b.name, "ar")); render(); });
  if (isAdmin()) {
    sub(collection(db, "payments"), (q) => { state.payments = q.docs.map((d) => ({ id: d.id, type: "monthly", ...d.data() })); render(); });
    sub(collection(db, "expenses"), (q) => { state.expenses = q.docs.map((d) => ({ id: d.id, ...d.data() })); render(); });
    sub(collection(db, "teachers"), (q) => { state.staff = q.docs.map((d) => ({ id: d.id, category: "مدرس", status: ACTIVE, ...d.data() }))
      .sort((a, b) => (a.name || "").localeCompare(b.name || "", "ar")); render(); });
    sub(collection(db, "payroll"), (q) => { state.payroll = q.docs.map((d) => ({ id: d.id, ...d.data() })); render(); });
  }
}

onAuthStateChanged(auth, (u) => {
  state.user = u; state.profile = null;
  if (!u) { state.unsub.forEach((x) => x()); renderLogin(); return; }
  onSnapshot(doc(db, "users", u.uid), (s) => {
    const first = state.profile === null;
    state.profile = s.exists() ? s.data() : { role: "student" };
    if (!isStaff()) { $("#app").innerHTML = `<div class="center"><h2>هذه اللوحة للإدارة والمعلمين</h2>
      <p class="muted">حسابك (${esc(u.email)}) لا يملك صلاحية الدخول. يمكنك استخدام تطبيق الهاتف.</p>
      <button class="btn" id="lo">تسجيل الخروج</button></div>`; $("#lo").onclick = () => signOut(auth); return; }
    if (first) listen();
    render();
  }, fail);
});

// ---------------------------------------------------------------- login
function renderLogin() {
  $("#app").innerHTML = `<div class="login-page">
    <section class="login-hero">
      <div class="kb" style="background-image:url(building.jpg)"></div><div class="kb-shade"></div>
      <div class="login-hero-text">
        <img src="logo.png" alt="" class="hero-logo">
        <div class="slides">
          <div class="slide active"><h1>${WELCOME}</h1><p class="slide-sub">بسم الله الرحمن الرحيم</p></div>
          <div class="slide"><h1 class="ayah">﴿وَلَقَدْ يَسَّرْنَا الْقُرْآنَ لِلذِّكْرِ فَهَلْ مِن مُّدَّكِرٍ﴾</h1><p class="hadith">${HADITH}</p></div>
          <div class="slide"><div class="dua">🤲 ${DUA}</div></div>
          <div class="slide"><h1>حلقات تحفيظ القرآن الكريم والدراسات الإسلامية</h1>
            <p class="slide-sub">📍 داودابوغو، باماكو • 🕌 تعليم كتاب الله لأبنائنا وبناتنا</p></div>
        </div>
        <div class="dots">${[0, 1, 2, 3].map((i) => `<button class="dot ${i ? "" : "on"}" data-i="${i}" aria-label="الشريحة ${i + 1}"></button>`).join("")}</div>
      </div>
    </section>
    <section class="login-side"><div class="login">
      <img src="logo.png" alt=""><h2>مركز الإمام مالك</h2>
      <div class="muted">لوحة الإدارة — داودابوغو، باماكو</div>
      <label>البريد الإلكتروني</label><input id="em" type="email" dir="ltr" autocomplete="username">
      <label>كلمة المرور</label><div style="position:relative"><input id="pw" type="password" dir="ltr" autocomplete="current-password" style="padding-left:44px">
      <button type="button" id="eye" title="إظهار كلمة المرور" style="position:absolute;left:6px;top:50%;transform:translateY(-50%);border:0;background:none;font-size:18px">👁</button></div>
      <button class="btn" id="go">تسجيل الدخول</button>
      <div style="margin-top:10px"><a href="#" id="reset">نسيت كلمة المرور؟</a></div><div class="error" id="err"></div>
      <div class="login-foot">بسم الله الرحمن الرحيم</div>
    </div></section></div>`;
  // Rotating welcome slides
  clearInterval(state.slideTimer);
  let idx = 0;
  const show = (i) => {
    idx = (i + 4) % 4;
    document.querySelectorAll(".slide").forEach((el, k) => el.classList.toggle("active", k === idx));
    document.querySelectorAll(".dot").forEach((el, k) => el.classList.toggle("on", k === idx));
  };
  document.querySelectorAll(".dot").forEach((d) => d.onclick = () => { show(+d.dataset.i); clearInterval(state.slideTimer);
    state.slideTimer = setInterval(() => show(idx + 1), 6500); });
  state.slideTimer = setInterval(() => { if (!document.querySelector(".slides")) return clearInterval(state.slideTimer); show(idx + 1); }, 6500);
  $("#eye").onclick = () => { const p = $("#pw"); p.type = p.type === "password" ? "text" : "password"; p.focus(); };
  const go = () => {
    if (!$("#em").value.trim()) { $("#err").textContent = "اكتب البريد الإلكتروني"; return; }
    if (!$("#pw").value) { $("#err").textContent = "اكتب كلمة المرور"; return; }
    $("#err").textContent = "";
    signInWithEmailAndPassword(auth, $("#em").value.trim(), $("#pw").value).catch((e) => {
      const c = e?.code || "";
      $("#err").textContent = c.includes("network") ? "لا يوجد اتصال بالإنترنت"
        : c.includes("too-many-requests") ? "محاولات كثيرة؛ انتظر قليلًا أو استعمل «نسيت كلمة المرور»"
        : c.includes("credential") || c.includes("password") || c.includes("user-not-found") ? "البريد أو كلمة المرور غير صحيحة (تحقق من لغة لوحة المفاتيح)"
        : "تعذّر الدخول: " + c;
    });
  };
  $("#go").onclick = go; $("#pw").onkeydown = (e) => { if (e.key === "Enter") go(); };  // must not return false: that would block typing
  $("#reset").onclick = (e) => { e.preventDefault(); const em = $("#em").value.trim();
    if (!em) { $("#err").textContent = "اكتب البريد أولًا"; return; }
    sendPasswordResetEmail(auth, em).then(() => toast("أُرسل رابط الاستعادة إلى بريدك")).catch(fail); };
}

// ---------------------------------------------------------------- shell
// [route, icon, label, adminOnly, section]
const NAV = [
  ["home", "🏠", "الرئيسية", false, ""],
  ["students", "👥", "الطلاب", false, "التعليم"], ["halaqat", "🕌", "الحلقات", false, "التعليم"],
  ["fees", "💰", "الرسوم الشهرية", true, "المالية"], ["registration", "📝", "رسوم التسجيل", true, "المالية"],
  ["expenses", "🧾", "المصروفات", true, "المالية"], ["summary", "📊", "الملخص المالي", true, "المالية"], ["report", "📄", "التقرير الشهري", true, "المالية"],
  ["staff", "👨‍🏫", "الموظفون", true, "الموظفون"], ["payroll", "💵", "الرواتب", true, "الموظفون"],
  ["phone", "📱", "تطبيق الهاتف", false, "التطبيق"]
];
const APK_URL = "https://github.com/YoussoufSid/centre-imam-malik-android/releases/latest/download/centre-imam-malik.apk";
const SITE_URL = "https://youssoufsid.github.io/centre-imam-malik-android/";
function navHtml(active) {
  let last = null;
  return NAV.filter((n) => !n[3] || isAdmin()).map((n) => {
    const head = n[4] && n[4] !== last ? `<div class="nav-sec">${n[4]}</div>` : ""; last = n[4];
    return `${head}<a href="#/${n[0]}" class="${n[0] === active ? "active" : ""}"><span class="ico">${n[1]}</span>${n[2]}</a>`;
  }).join("");
}
window.addEventListener("hashchange", () => render());
const dark = () => localStorage.getItem("theme") === "dark";
const applyTheme = () => document.body.classList.toggle("dark", dark());
applyTheme();
const route = () => (location.hash.replace("#/", "") || "home");

function render() {
  if (!state.user || !state.profile || !isStaff()) return;
  const r = route();
  const views = { home: viewHome, students: viewStudents, halaqat: viewHalaqat, fees: viewFees, registration: viewRegistration, expenses: viewExpenses, summary: viewSummary, staff: viewStaff, payroll: viewPayroll, phone: viewPhone, report: viewReport };
  const allowed = NAV.find((n) => n[0] === r && (!n[3] || isAdmin())) ? r : "home";
  const focus = document.activeElement?.id; const caret = document.activeElement?.selectionStart;
  const title = NAV.find((n) => n[0] === allowed);
  $("#app").innerHTML = `<div class="layout${state.menuOpen ? " menu-open" : ""}">
    <header class="mbar"><button id="menuBtn" aria-label="القائمة">☰</button><b>${title[1]} ${title[2]}</b>
      <button id="theme2" aria-label="الوضع الليلي">${dark() ? "☀️" : "🌙"}</button><img src="logo.png" alt=""></header>
    <aside class="side">
      <div class="brand"><img src="logo.png" alt=""><b>${esc(state.settings.name || DEFAULT_NAME)}</b></div>
      <nav>${navHtml(allowed)}</nav>
      <div class="user">${esc(state.profile?.name || state.user.email)}<br>${isAdmin() ? "الإدارة" : "معلم"}<br>
      <button id="logout">تسجيل الخروج</button> <button id="theme">${dark() ? "☀️ الوضع النهاري" : "🌙 الوضع الليلي"}</button></div></aside>
    <main><div class="print-head"><img src="logo.png" alt=""><h3>${esc(state.settings.name || DEFAULT_NAME)}</h3>
      <div>${esc(state.settings.address || "")}</div></div>${state.loaded ? views[allowed]() : '<div class="center muted">جارٍ تحميل البيانات…</div>'}</main></div>`;
  ["#theme", "#theme2"].forEach((id) => { $(id).onclick = () => { localStorage.setItem("theme", dark() ? "light" : "dark"); applyTheme(); render(); }; });
  $("#menuBtn").onclick = () => { state.menuOpen = !state.menuOpen; render(); };
  document.querySelectorAll(".side nav a").forEach((a) => a.addEventListener("click", () => { state.menuOpen = false; }));
  $("#logout").onclick = () => confirmBox("هل تريد تسجيل الخروج؟", () => signOut(auth), "خروج");
  const h = document.querySelector("main > h1"); if (h) h.dataset.ico = title[1];
  bind[allowed]?.();
  if (focus && document.getElementById(focus)) { const el = document.getElementById(focus); el.focus(); try { el.setSelectionRange(caret, caret); } catch {} }
}
const bind = {};
const filterStudents = (list) => list.filter((s) =>
  (!state.filters.q || (s.name || "").includes(state.filters.q.trim()) || (s.phone || "").includes(state.filters.q.trim())) &&
  (!state.filters.group || (state.filters.group === "-" ? !s.group : s.group === state.filters.group)));
const groupSelect = (id = "fgroup") => `<select id="${id}"><option value="">كل الحلقات</option>${groups().map((g) =>
  `<option ${state.filters.group === g ? "selected" : ""}>${esc(g)}</option>`).join("")}<option value="-" ${state.filters.group === "-" ? "selected" : ""}>بدون حلقة</option></select>`;
function bindFilters() {
  const q = $("#fq"); if (q) q.oninput = () => { state.filters.q = q.value; render(); };
  const g = $("#fgroup"); if (g) g.onchange = () => { state.filters.group = g.value; render(); };
}

// ---------------------------------------------------------------- WhatsApp
/** Mali numbers: 8 digits → +223; "00" prefix dropped. Same rule as the phone app. */
function waNumber(phone) {
  let d = String(phone || "").replace(/\D/g, "");
  if (d.startsWith("00")) d = d.slice(2);
  if (d.length === 8) d = "223" + d;
  return d.length >= 10 ? d : null;
}
const waLink = (phone, text) => { const n = waNumber(phone); return n ? `https://wa.me/${n}?text=${encodeURIComponent(text)}` : null; };

// ---------------------------------------------------------------- certificate (printable, Islamic frame)
function printCertificate(s) {
  const h = s.hizbs || 0, khatm = h >= 60;
  const body = khatm ? "قد أتمّ حفظ كتاب الله تعالى كاملًا (ستين حزبًا) بفضل الله وتوفيقه."
    : h === 0 ? "تقديرًا لجهده ومواظبته في حلقات تحفيظ القرآن الكريم."
    : `قد أتمّ حفظ ${h} حزبًا من كتاب الله تعالى${h >= 2 ? ` (≈ ${Math.floor(h / 2)} جزءًا)` : ""}، تقديرًا لجهده ومواظبته.`;
  let hijri = ""; try { hijri = new Date().toLocaleDateString("ar-SA-u-ca-islamic-umalqura-nu-latn", { day: "numeric", month: "long", year: "numeric" }); } catch {}
  showPrintOverlay(`<div class="cert"><div class="cert-in">
    <span class="corner tl">۞</span><span class="corner tr">۞</span><span class="corner bl">۞</span><span class="corner br">۞</span>
    <img src="logo.png" alt=""><div class="cert-centre">${esc(state.settings.name || DEFAULT_NAME)}</div>
    <div class="cert-title">${khatm ? "شهادة ختم القرآن الكريم" : "شهادة تقدير"}</div>
    <div class="cert-orn">❁ ❁ ❁</div>
    <p>يشهد المركز بأن الطالب/ة</p><div class="cert-name">${esc(s.name)}</div><p class="cert-body">${body}</p>
    <p class="cert-ayah">﴿وَلَقَدْ يَسَّرْنَا الْقُرْآنَ لِلذِّكْرِ فَهَلْ مِن مُّدَّكِرٍ﴾</p>
    <p class="muted">نسأل الله أن يجعله من أهل القرآن الذين هم أهل الله وخاصته.</p>
    <div class="cert-sign"><div>حُرّر بتاريخ \u2066${today()}\u2069${hijri ? `<br>الموافق ${esc(hijri)}` : ""}</div>
      ${s.teacher ? `<div>المعلم<br><b>${esc(s.teacher)}</b><hr></div>` : ""}<div>مدير المركز<br><b>${esc(state.settings.director || "")}</b><hr></div></div>
  </div></div>`);
}

// ---------------------------------------------------------------- student ID cards
/** "s001" → "S001"; generated ids → last 6 characters. Same rule as the phone app. */
const studentNumber = (id) => (/^[sS]\d+$/.test(id) ? id : String(id).slice(-6)).toUpperCase();
const ltrText = (t) => `\u2066${esc(t)}\u2069`;
const CENTRE_FR = "Centre Imam Malik pour la mémorisation du Saint Coran et les études islamiques";
const frDate = (d) => /^\d{4}-\d{2}-\d{2}$/.test(d || "") ? `${d.slice(8, 10)}/${d.slice(5, 7)}/${d.slice(0, 4)}` : (d || "");
/** Bilingual (Arabic / French) student ID card. */
function cardHtml(s) {
  const year = yearLabel(currentMonth());
  const row = (ar, fr, v) => v ? `<div><span>${ar} <i>${fr}</i> :</span> ${v}</div>` : "";
  return `<div class="idcard"><div class="idc-head"><img src="logo.png" alt=""><div><b>${esc(state.settings.name || DEFAULT_NAME)}</b>
      <em>${CENTRE_FR}</em><span>بطاقة طالب • Carte d'élève</span></div></div>
    <div class="idc-body"><div class="idc-info"><div class="idc-name">${esc(s.name)}</div>
      ${row("الحلقة", "Groupe", esc(s.group || ""))}${row("المعلم", "Enseignant", esc(s.teacher || ""))}
      ${row("تاريخ الميلاد", "Né(e) le", s.birthDate ? ltrText(frDate(s.birthDate)) : "")}
      ${row("ولي الأمر", "Tuteur", s.phone ? ltrText(s.phone) : "")}${row("التسجيل", "Inscription", s.enrollmentDate ? ltrText(frDate(s.enrollmentDate)) : "")}</div>
      <div class="idc-side"><div class="idc-photo">${s.photo ? `<img src="${s.photo}" alt="">` : "الصورة<br>Photo"}</div><div class="idc-no">رقم / ${ltrText("N° " + studentNumber(s.id))}</div></div></div>
    <div class="idc-foot">📍 ${esc(state.settings.address || "داودابوغو، باماكو")} — ${ltrText("Daoudabougou, Bamako")}${state.settings.phone ? ` • ☎ ${ltrText(state.settings.phone)}` : ""}<br>
      العام الدراسي ${ltrText(year)} • ${ltrText("Année scolaire " + year)}</div></div>`;
}
function printCards(list) {
  if (!list.length) { toast("لا يوجد طلاب"); return; }
  showPrintOverlay(`<div class="cards-sheet">${list.map(cardHtml).join("")}</div>`,
    `${list.length} بطاقة — 10 بطاقات في كل صفحة A4، قصّها على الخطوط`);
}
/** Full-screen preview with Print / Close; only its content is printed. */
function showPrintOverlay(inner, note = "") {
  const box = document.createElement("div"); box.id = "cert";
  box.innerHTML = `<div class="no-print cert-actions"><button class="btn" id="cprint">🖨️ طباعة</button> <button class="btn outline" id="cclose">إغلاق</button>
    ${note ? `<div class="cert-note">${esc(note)}</div>` : ""}</div>${inner}`;
  document.body.appendChild(box); document.body.classList.add("cert-mode");
  box.querySelector("#cprint").onclick = () => window.print();
  box.querySelector("#cclose").onclick = () => { box.remove(); document.body.classList.remove("cert-mode"); };
}

// ---------------------------------------------------------------- monthly report (admin)
function viewReport() {
  const m = state.filters.reportMonth || currentMonth();
  const sum = (l) => l.reduce((a, x) => a + (x.amount || 0), 0);
  const active = state.students.filter((s) => (s.status || ACTIVE) === ACTIVE);
  const newSt = state.students.filter((s) => (s.enrollmentDate || "").startsWith(m));
  const monthly = state.payments.filter((p) => p.type === "monthly" && p.month === m);
  const reg = state.payments.filter((p) => p.type === "registration" && p.month === m);
  const billable = active.filter((s) => (s.monthlyFee || 0) > 0);
  const paidIds = new Set(monthly.map((p) => p.studentId));
  const unpaid = billable.filter((s) => !paidIds.has(s.id));
  const expected = billable.reduce((a, s) => a + s.monthlyFee, 0);
  const exps = state.expenses.filter((e) => e.month === m);
  const cats = {}; exps.forEach((e) => { cats[e.category] = (cats[e.category] || 0) + (e.amount || 0); });
  const pay = state.payroll.filter((p) => p.month === m);
  const income = sum(monthly) + sum(reg), out = sum(exps);
  const top = active.filter((s) => s.hizbs > 0).sort((a, b) => b.hizbs - a.hizbs).slice(0, 5);
  return `<h1>التقرير الشهري</h1><div class="sub">${monthName(m)} — ملخص جاهز للطباعة أو الإرسال للإدارة</div>
  <div class="toolbar"><button class="btn outline small" id="pm">◀ الشهر السابق</button><b>${monthName(m)}</b>
    <button class="btn outline small" id="nm">الشهر التالي ▶</button><button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button></div>
  <div class="cards"><div class="card stat green"><div class="l">الإيرادات</div><div class="v">${amt(income)}</div></div>
    <div class="card stat red"><div class="l">المصروفات</div><div class="v">${amt(out)}</div></div>
    <div class="card stat ${income - out >= 0 ? "green" : "red"}"><div class="l">الرصيد</div><div class="v">${amt(income - out)}</div></div></div>
  <div class="panel"><h3>الطلاب</h3><table><tbody>
    <tr><td>الطلاب النشطون</td><td class="num">${num(active.length)}</td></tr>
    <tr><td>الطلاب / الطالبات</td><td class="num">${num(active.filter((s) => s.gender === "ذكر").length)} / ${num(active.filter((s) => s.gender === "أنثى").length)}</td></tr>
    <tr><td>المسجلون الجدد هذا الشهر</td><td class="num">${num(newSt.length)}</td></tr>
    <tr><td>الحلقات</td><td class="num">${num(groups().length)}</td></tr></tbody></table></div>
  <div class="panel"><h3>الرسوم</h3><table><tbody>
    <tr><td>الرسوم الشهرية المحصّلة</td><td class="num">${money(sum(monthly))}</td></tr>
    <tr><td>المتوقع</td><td class="num">${money(expected)}</td></tr>
    <tr><td>نسبة التحصيل</td><td class="num">${num(expected ? Math.round(sum(monthly) * 100 / expected) : 0)}٪</td></tr>
    <tr><td>دفعوا / لم يدفعوا</td><td class="num">${num(billable.length - unpaid.length)} / <b style="color:var(--red)">${num(unpaid.length)}</b></td></tr>
    <tr><td>رسوم التسجيل</td><td class="num">${money(sum(reg))}</td></tr></tbody></table></div>
  <div class="panel"><h3>المصروفات</h3><table><tbody>
    ${Object.entries(cats).sort((a, b) => b[1] - a[1]).map(([c, v]) => `<tr><td>${esc(c)}</td><td class="num">${money(v)}</td></tr>`).join("") || '<tr><td class="muted">لا مصروفات</td></tr>'}
    </tbody></table>${pay.length ? `<p class="muted">الرواتب: ${num(pay.length)} موظف — المستلم ${money(pay.reduce((a, p) => a + (p.received || 0), 0))}</p>` : ""}</div>
  ${top.length ? `<div class="panel"><h3>لوحة الشرف</h3>${honorList(top)}</div>` : ""}
  ${unpaid.length ? `<div class="panel"><h3>لم يدفعوا رسوم ${monthName(m)} (${num(unpaid.length)})</h3><div class="muted">${unpaid.map((s) => esc(s.name)).join("، ")}</div></div>` : ""}`;
}
bind.report = () => {
  const go = (d) => { state.filters.reportMonth = shiftMonth(state.filters.reportMonth || currentMonth(), d); render(); };
  $("#pm").onclick = () => go(-1); $("#nm").onclick = () => go(1);
};
const honorList = (top) => `<div class="honor">${top.map((s, i) => `<div class="honor-row"><span class="medal">${["🥇", "🥈", "🥉", "🏅", "🏅"][i]}</span>
  <b>${esc(s.name)}</b><span class="muted">${esc(s.group || "")}</span><span class="hz">${num(s.hizbs)} حزبًا</span>
  <div class="bar"><div style="width:${Math.min(100, s.hizbs * 100 / 60)}%"></div></div></div>`).join("")}</div>`;

// ---------------------------------------------------------------- phone app
function viewPhone() {
  return `<h1>تطبيق الهاتف</h1><div class="sub">التثبيت والتحديث على الهاتف والحاسوب</div>
  <div class="panel phone-panel">
    <h3>تثبيت التطبيق أو تحديثه على هاتف أندرويد</h3>
    <a class="btn big" href="${APK_URL}">⬇️ تنزيل أحدث إصدار</a>
    <ol class="steps">
      <li>افتح هذه الصفحة على الهاتف واضغط «تنزيل أحدث إصدار».</li>
      <li>بعد التنزيل افتح الملف <b>centre-imam-malik.apk</b>.</li>
      <li>إذا طلب الهاتف الإذن اختر «السماح من هذا المصدر»، ثم اضغط <b>تثبيت</b> (أو <b>تحديث</b>).</li>
    </ol>
    <p class="muted">التحديث لا يمسح البيانات؛ كل البيانات محفوظة في السحابة ومشتركة بين الهاتف والحاسوب.
      وبعد هذا التثبيت يُظهر التطبيق نفسه زر «تحديث» كلما صدر إصدار جديد.</p>
  </div>
  <div class="panel">
    <h3>💻 الحاسوب</h3>
    <p>لوحة الحاسوب تتحدث تلقائيًا؛ يكفي فتح الرابط (أو أيقونة التطبيق المثبتة):</p>
    <p class="ltr-link"><a href="${SITE_URL}">${SITE_URL}</a></p>
    <p class="muted">يمكن أيضًا فتح هذا الرابط على الهاتف واختيار «إضافة إلى الشاشة الرئيسية».</p>
    <button class="btn small outline" id="copySite">نسخ الرابط</button>
  </div>`;
}
bind.phone = () => { const b = $("#copySite"); if (b) b.onclick = () => navigator.clipboard?.writeText(SITE_URL).then(() => toast("تم نسخ الرابط")); };

// ---------------------------------------------------------------- home
function viewHome() {
  const active = state.students.filter((s) => (s.status || ACTIVE) === ACTIVE);
  const m = currentMonth();
  const monthly = state.payments.filter((p) => p.type === "monthly" && p.month === m);
  const billable = active.filter((s) => (s.monthlyFee || 0) > 0);
  const paidIds = new Set(monthly.map((p) => p.studentId));
  const paidCount = billable.filter((s) => paidIds.has(s.id)).length;
  const unpaid = billable.length - paidCount;
  const pct = billable.length ? Math.round(paidCount * 100 / billable.length) : 0;
  const d = new Date();
  const dateAr = d.toLocaleDateString("ar", { weekday: "long", day: "numeric", month: "long", year: "numeric", numberingSystem: "latn" });
  let hijri = ""; try { hijri = d.toLocaleDateString("ar-SA-u-ca-islamic-umalqura-nu-latn", { day: "numeric", month: "long", year: "numeric" }); } catch {}
  const shortcuts = [["#/students", "👥", "الطلاب"], ["#/halaqat", "🕌", "الحلقات"],
    ...(isAdmin() ? [["#/fees", "💰", "تسجيل الرسوم"], ["#/expenses", "🧾", "مصروف جديد"], ["#/payroll", "💵", "الرواتب"], ["#/summary", "📊", "الملخص المالي"]] : [])];
  return `<div class="banner">
      <div class="banner-text">
        <div class="banner-date">${esc(dateAr)}${hijri ? ` — ${esc(hijri)}` : ""} • العام الدراسي ${yearLabel(m)}</div>
        <h1>السلام عليكم ورحمة الله وبركاته</h1>
        <p>${WELCOME}</p>
        <p class="hadith">${HADITH}</p>
        <div class="dua">🤲 ${DUA}</div>
      </div>
      <div class="banner-img"><div class="kb" style="background-image:url(building.jpg)"></div></div>
    </div>
  <div class="cards">
    <div class="card stat green"><div class="l">الطلاب النشطون</div><div class="v">${active.length}</div></div>
    <div class="card stat"><div class="l">الطلاب / الطالبات</div><div class="v">${active.filter((s) => s.gender === "ذكر").length} / ${active.filter((s) => s.gender === "أنثى").length}</div></div>
    <div class="card stat"><div class="l">الحلقات</div><div class="v">${groups().length}</div></div>
    ${isAdmin() ? `<div class="card stat gold"><div class="l">الموظفون</div><div class="v">${state.staff.filter((t) => (t.status || ACTIVE) === ACTIVE).length}</div></div>` : ""}
  </div>
  ${isAdmin() ? `<div class="panel"><div class="row-between"><h3>رسوم ${monthName(m)}</h3><a href="#/fees" class="btn small outline">فتح جدول الرسوم ←</a></div>
    <div class="progress"><div style="width:${pct}%"></div></div>
    <div class="row-between muted" style="margin-top:6px"><span>دفع <b style="color:var(--green)">${paidCount}</b> من ${billable.length} (${pct}٪) — المحصَّل ${money(monthly.reduce((a, p) => a + (p.amount || 0), 0))}</span>
    <span>لم يدفع <b style="color:var(--red)">${unpaid}</b></span></div></div>` : ""}
  <div class="shortcuts">${shortcuts.map(([h, i, t]) => `<a href="${h}" class="shortcut"><span>${i}</span>${t}</a>`).join("")}</div>
  ${(() => { const top = active.filter((s) => s.hizbs > 0).sort((a, b) => b.hizbs - a.hizbs).slice(0, 5);
    return top.length ? `<h3 class="sec-title">🏆 لوحة الشرف — الأكثر حفظًا</h3><div class="panel">${honorList(top)}</div>` : ""; })()}
  <h3 class="sec-title">الحلقات</h3>
  <div class="halaqa-cards">${groups().map((g) => { const l = active.filter((s) => s.group === g); const h = state.halaqat.find((x) => x.name === g);
    const boys = l.filter((s) => s.gender === "ذكر").length, girls = l.filter((s) => s.gender === "أنثى").length;
    return `<a href="#/students" data-g="${esc(g)}" class="glink hcard"><b>${esc(g)}</b>
      <div class="muted">${h?.teacher ? "👤 " + esc(h.teacher) : "لم يُحدَّد المعلم"}${h?.schedule ? " • 🕒 " + esc(h.schedule) : ""}</div>
      <div class="hnums"><span><b>${l.length}</b> طالب</span><span>👦 ${boys}</span><span>👧 ${girls}</span></div></a>`; }).join("")}</div>`;
}
bind.home = () => document.querySelectorAll(".glink").forEach((a) => a.onclick = () => { state.filters.group = a.dataset.g; });

// ---------------------------------------------------------------- students
function viewStudents() {
  const list = filterStudents(state.students);
  return `<h1>الطلاب</h1><div class="sub">${state.students.length} طالب مسجل</div>
  <div class="toolbar"><input id="fq" placeholder="🔍 بحث بالاسم أو الهاتف" value="${esc(state.filters.q)}">${groupSelect()}
    <button class="btn" id="add">+ إضافة طالب</button><button class="btn outline" id="cards">🪪 بطاقات الطلاب</button><button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button>
    <span class="muted">${list.length} نتيجة</span></div>
  <div class="table-wrap"><table><thead><tr><th class="num">#</th><th>الاسم</th><th>الجنس</th><th>الحلقة</th><th>المعلم</th><th>هاتف ولي الأمر</th>
  <th class="num">الأحزاب</th>${isAdmin() ? '<th class="num">الرسوم الشهرية</th>' : ""}<th>الحالة</th><th class="no-print"></th></tr></thead><tbody>
  ${list.map((s, i) => `<tr><td class="num">${i + 1}</td><td><span class="avatar">${s.photo ? `<img src="${s.photo}" alt="">` : ""}</span><b>${esc(s.name)}</b></td><td>${esc(s.gender)}</td><td>${esc(s.group)}</td>
    <td>${esc(s.teacher)}</td><td dir="ltr" style="text-align:right">${esc(s.phone)}</td><td class="num">${s.hizbs || 0}/60</td>
    ${isAdmin() ? `<td class="num">${num(s.monthlyFee)}</td>` : ""}
    <td><span class="chip ${(s.status || ACTIVE) === ACTIVE ? "ok" : "gray"}">${esc(s.status || ACTIVE)}</span></td>
    <td class="actions no-print"><button class="btn small outline" data-edit="${s.id}">تعديل</button>
    <button class="btn small gold" data-cert="${s.id}" title="شهادة">🎓</button>
    <button class="btn small outline" data-card="${s.id}" title="بطاقة الطالب">🪪</button>
    ${isAdmin() ? `<button class="btn small danger" data-del="${s.id}">حذف</button>` : ""}</td></tr>`).join("") || `<tr><td colspan="10" class="muted">لا توجد نتائج</td></tr>`}
  </tbody></table></div>`;
}
bind.students = () => {
  bindFilters();
  $("#add").onclick = () => studentForm({ status: ACTIVE, enrollmentDate: today(), group: state.filters.group && state.filters.group !== "-" ? state.filters.group : "" });
  $("#cards").onclick = () => printCards(filterStudents(state.students).filter((s) => (s.status || ACTIVE) === ACTIVE));
  document.querySelectorAll("[data-card]").forEach((b) => b.onclick = () => printCards([state.students.find((s) => s.id === b.dataset.card)]));
  document.querySelectorAll("[data-cert]").forEach((b) => b.onclick = () => printCertificate(state.students.find((s) => s.id === b.dataset.cert)));
  document.querySelectorAll("[data-edit]").forEach((b) => b.onclick = () => studentForm(state.students.find((s) => s.id === b.dataset.edit)));
  document.querySelectorAll("[data-del]").forEach((b) => b.onclick = () => {
    const s = state.students.find((x) => x.id === b.dataset.del);
    confirmBox(`حذف «${s.name}» وكل سجلاته (الحضور، التسميع، الدرجات، المدفوعات)؟`, () => deleteStudent(s));
  });
};

/** Reads an image file, centre-crops it to 3:4 and returns a small JPEG data URL (~10 KB). */
function resizePhoto(file, w = 240, h = 320) {
  return new Promise((ok, ko) => {
    const img = new Image(); const url = URL.createObjectURL(file);
    img.onload = () => {
      const r = Math.max(w / img.width, h / img.height), sw = w / r, sh = h / r;
      const c = document.createElement("canvas"); c.width = w; c.height = h;
      c.getContext("2d").drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, 0, 0, w, h);
      URL.revokeObjectURL(url); ok(c.toDataURL("image/jpeg", 0.72));
    };
    img.onerror = () => { URL.revokeObjectURL(url); ko(new Error("تعذّرت قراءة الصورة")); };
    img.src = url;
  });
}

function studentForm(s) {
  const opts = groups().map((g) => `<option ${s.group === g ? "selected" : ""}>${esc(g)}</option>`).join("");
  openModal(`<h3>${s.id ? "تعديل ملف الطالب" : "إضافة طالب"}</h3>
    <div class="photo-pick"><div class="ph-box" id="phBox">${s.photo ? `<img src="${s.photo}" alt="">` : "👤"}</div><div>
      <label class="btn small outline">📷 إضافة / تغيير الصورة<input type="file" id="phFile" accept="image/*" hidden></label>
      <button class="btn small danger ${s.photo ? "" : "hidden"}" id="phDel" type="button">حذف الصورة</button>
      <div class="muted" style="font-size:12px">تظهر على بطاقة الطالب. على الهاتف يمكن التقاطها بالكاميرا.</div></div></div>
    <label>الاسم الكامل</label><input id="n" value="${esc(s.name)}">
    <div class="row2"><div><label>الجنس</label><select id="g"><option></option><option ${s.gender === "ذكر" ? "selected" : ""}>ذكر</option><option ${s.gender === "أنثى" ? "selected" : ""}>أنثى</option></select></div>
    <div><label>الحلقة</label><select id="gr"><option value="">بدون حلقة</option>${opts}</select></div></div>
    <div class="row2"><div><label>تاريخ الميلاد</label><input id="bd" type="date" value="${esc(s.birthDate)}"></div>
    <div><label>تاريخ التسجيل</label><input id="ed" type="date" value="${esc(s.enrollmentDate)}"></div></div>
    <div class="row2"><div><label>هاتف ولي الأمر</label><input id="ph" dir="ltr" value="${esc(s.phone)}"></div>
    <div><label>المعلم</label><input id="t" value="${esc(s.teacher)}"></div></div>
    <div class="row2"><div><label>المستوى</label><input id="lv" value="${esc(s.level)}"></div><div><label>العنوان</label><input id="ad" value="${esc(s.address)}"></div></div>
    ${isAdmin() ? `<div class="row2"><div><label>الرسوم الشهرية</label><input id="mf" type="number" min="0" value="${s.monthlyFee || ""}"></div>
    <div><label>رسوم التسجيل</label><input id="rf" type="number" min="0" value="${s.registrationFee || ""}"></div></div>` : ""}
    <label>الحالة</label><select id="st"><option ${s.status !== "متوقف" ? "selected" : ""}>نشط</option><option ${s.status === "متوقف" ? "selected" : ""}>متوقف</option></select>
    <div class="modal-actions"><button class="btn" id="save">حفظ</button><button class="btn outline" id="cancel">إلغاء</button></div>`, (b) => {
    b.querySelector("#gr").onchange = (e) => { const h = state.halaqat.find((x) => x.name === e.target.value); if (h?.teacher && !b.querySelector("#t").value) b.querySelector("#t").value = h.teacher; };
    b.querySelector("#cancel").onclick = closeModal;
    let photo;  // undefined = unchanged, "" = removed, data URL = new
    const showPhoto = (p) => { b.querySelector("#phBox").innerHTML = p ? `<img src="${p}" alt="">` : "👤"; b.querySelector("#phDel").classList.toggle("hidden", !p); };
    b.querySelector("#phFile").onchange = (e) => { const f = e.target.files[0]; if (!f) return;
      resizePhoto(f).then((p) => { photo = p; showPhoto(p); }).catch(fail); };
    b.querySelector("#phDel").onclick = () => { photo = ""; showPhoto(""); };
    b.querySelector("#save").onclick = () => {
      const v = (id) => b.querySelector("#" + id)?.value?.trim() ?? "";
      if (!v("n")) { toast("اسم الطالب مطلوب"); return; }
      const data = { name: v("n"), gender: v("g"), group: v("gr"), birthDate: v("bd"), enrollmentDate: v("ed"), phone: v("ph"),
        teacher: v("t"), level: v("lv"), address: v("ad"), status: v("st"), updatedAt: new Date() };
      if (isAdmin()) { data.monthlyFee = +v("mf") || 0; data.registrationFee = +v("rf") || 0; }
      const ref = s.id ? doc(db, "students", s.id) : doc(collection(db, "students"));
      if (!s.id) data.hizbs = 0;
      if (photo !== undefined) data.photo = photo;
      setDoc(ref, data, { merge: true }).then(() => toast(s.id ? "تم تحديث بيانات الطالب" : "تمت إضافة الطالب")).catch(fail);
      closeModal();
    };
  });
}

async function deleteStudent(s) {
  try {
    const batch = writeBatch(db);
    let n = 0;
    for (const c of ["attendance", "quranLogs", "grades", "payments"]) {
      const snap = await getDocs(query(collection(db, c), where("studentId", "==", s.id)));
      snap.forEach((d) => { batch.delete(d.ref); n++; });
    }
    batch.delete(doc(db, "students", s.id));
    await batch.commit();
    audit("حذف طالب", `${s.name} (${s.group || "بدون حلقة"}) مع ${n} سجل`);
    toast(`تم حذف ${s.name} و${n} سجلًا مرتبطًا به`);
  } catch (e) { fail(e); }
}

// ---------------------------------------------------------------- halaqat
function viewHalaqat() {
  const reg = new Set(state.halaqat.map((h) => h.name));
  const extra = groups().filter((g) => !reg.has(g));
  return `<h1>الحلقات</h1><div class="sub">${state.halaqat.length} حلقة مسجلة</div>
  ${isAdmin() ? '<div class="toolbar"><button class="btn" id="addh">+ حلقة جديدة</button></div>' : ""}
  <div class="table-wrap"><table><thead><tr><th>الحلقة</th><th>المعلم</th><th>المواعيد</th><th class="num">الطلاب</th><th>ملاحظات</th><th></th></tr></thead><tbody>
  ${state.halaqat.map((h) => `<tr><td><b>${esc(h.name)}</b></td><td>${esc(h.teacher)}</td><td>${esc(h.schedule)}</td>
    <td class="num">${state.students.filter((s) => s.group === h.name && (s.status || ACTIVE) === ACTIVE).length}</td><td>${esc(h.notes)}</td>
    <td class="actions">${isAdmin() ? `<button class="btn small outline" data-h="${h.id}">تعديل</button>` : ""}</td></tr>`).join("")}
  ${extra.map((g) => `<tr><td>${esc(g)} <span class="chip gray">غير مسجلة</span></td><td></td><td></td>
    <td class="num">${state.students.filter((s) => s.group === g).length}</td><td></td>
    <td class="actions">${isAdmin() ? `<button class="btn small" data-reg="${esc(g)}">تسجيل</button>` : ""}</td></tr>`).join("")}
  </tbody></table></div>`;
}
bind.halaqat = () => {
  $("#addh") && ($("#addh").onclick = () => halaqaForm({}));
  document.querySelectorAll("[data-h]").forEach((b) => b.onclick = () => halaqaForm(state.halaqat.find((h) => h.id === b.dataset.h)));
  document.querySelectorAll("[data-reg]").forEach((b) => b.onclick = () => halaqaForm({ name: b.dataset.reg }));
};
function halaqaForm(h) {
  openModal(`<h3>${h.id ? "تعديل الحلقة" : "حلقة جديدة"}</h3>
    <label>اسم الحلقة</label><input id="n" value="${esc(h.name)}"><label>المعلم</label><input id="t" value="${esc(h.teacher)}">
    <label>المواعيد</label><input id="sc" value="${esc(h.schedule)}" placeholder="السبت–الأربعاء 16:00–18:00">
    <label>ملاحظات</label><input id="no" value="${esc(h.notes)}">
    <div class="modal-actions"><button class="btn" id="save">حفظ</button><button class="btn outline" id="cancel">إلغاء</button>
    ${h.id ? '<button class="btn danger" id="del">حذف الحلقة</button>' : ""}</div>`, (b) => {
    b.querySelector("#cancel").onclick = closeModal;
    b.querySelector("#del") && (b.querySelector("#del").onclick = () => confirmBox(`حذف «${h.name}»؟ لا يُحذف أي طالب.`, () => {
      deleteDoc(doc(db, "halaqat", h.id)).then(() => { audit("حذف حلقة", h.name); toast("حُذفت الحلقة"); }).catch(fail); }));
    b.querySelector("#save").onclick = async () => {
      const name = b.querySelector("#n").value.trim(), teacher = b.querySelector("#t").value.trim();
      if (!name) { toast("اسم الحلقة مطلوب"); return; }
      if (state.halaqat.some((x) => x.name === name && x.id !== h.id)) { toast("توجد حلقة بهذا الاسم"); return; }
      try {
        const ref = h.id ? doc(db, "halaqat", h.id) : doc(collection(db, "halaqat"));
        const batch = writeBatch(db);
        batch.set(ref, { name, teacher, schedule: b.querySelector("#sc").value.trim(), notes: b.querySelector("#no").value.trim(), updatedAt: new Date() });
        // Renaming / changing teacher updates the students of the halaqa (same behaviour as the app).
        if (h.name && (h.name !== name || (h.teacher || "") !== teacher)) {
          state.students.filter((s) => s.group === h.name).forEach((s) => {
            const upd = { group: name, updatedAt: new Date() };
            if (!s.teacher || s.teacher === h.teacher) upd.teacher = teacher;
            batch.update(doc(db, "students", s.id), upd);
          });
        }
        await batch.commit(); closeModal(); toast("تم حفظ الحلقة");
      } catch (e) { fail(e); }
    };
  });
}

// ---------------------------------------------------------------- monthly fees grid
function viewFees() {
  const months = academicMonths(state.filters.year);
  const cm = currentMonth();
  const dueMonths = months.filter((m) => m <= cm).length;
  const list = filterStudents(state.students).filter((s) => (s.status || ACTIVE) === ACTIVE || state.payments.some((p) => p.studentId === s.id));
  const pay = {}; state.payments.filter((p) => p.type === "monthly").forEach((p) => { pay[`${p.month}_${p.studentId}`] = p; });
  let totPaid = 0, totDue = 0, totLate = 0;  // arrears summed per student (advance payments don't offset others)
  const rows = list.map((s, i) => {
    const fee = s.monthlyFee || 0;
    let paid = 0;
    const cells = months.map((m) => { const p = pay[`${m}_${s.id}`]; if (p) paid += p.amount || 0;
      const cls = p ? "paid" : (m <= cm && fee > 0 ? "late" : (m > cm ? "future" : ""));
      return `<td class="cell ${cls}" data-s="${s.id}" data-m="${m}" title="${monthName(m)}">${p ? num(p.amount) : (m <= cm && fee > 0 ? "—" : "")}</td>`; }).join("");
    const due = fee * dueMonths; const late = Math.max(0, due - paid);
    totPaid += paid; totDue += due; totLate += late;
    const wa = late ? waLink(s.phone, `السلام عليكم ورحمة الله وبركاته،\nنذكّركم بأن على الطالب/ة ${s.name} رسومًا متأخرة قدرها ${num(late).replace(/[\u2066\u2069]/g, "")} ${state.settings.currency || "فرنك"} (العام الدراسي ${yearLabel(state.filters.year)}).\nجزاكم الله خيرًا.\n${state.settings.name || DEFAULT_NAME}`) : null;
    return `<tr><td class="num">${i + 1}</td><td><b>${esc(s.name)}</b><div class="muted" style="font-size:12px">${esc(s.group)}</div></td>
      <td class="num">${num(fee)}</td>${cells}<td class="num"><b>${num(paid)}</b></td><td class="num">${num(due)}</td>
      <td class="num" style="color:${late ? "var(--red)" : "inherit"}"><b>${num(late)}</b></td>
      <td class="no-print">${wa ? `<a class="wa" href="${wa}" target="_blank" rel="noopener" title="تذكير ولي الأمر عبر واتساب">💬</a>` : ""}</td></tr>`;
  }).join("");
  const colTotals = months.map((m) => list.reduce((a, s) => a + (pay[`${m}_${s.id}`]?.amount || 0), 0));
  return `<h1>الرسوم الشهرية</h1><div class="sub">العام الدراسي ${yearLabel(state.filters.year)} — اضغط على خانة الشهر لتسجيل الدفع أو إلغائه</div>
  <div class="cards"><div class="card stat green"><div class="l">المحصَّل (${dueMonths} أشهر مستحقة)</div><div class="v">${amt(totPaid)}</div></div>
    <div class="card stat"><div class="l">المستحق حتى الآن</div><div class="v">${amt(totDue)}</div></div>
    <div class="card stat red"><div class="l">المتأخرات</div><div class="v">${amt(totLate)}</div></div></div>
  <div class="toolbar"><button class="btn outline small" id="py">◀ السنة السابقة</button><b>${yearLabel(state.filters.year)}</b>
    <button class="btn outline small" id="ny">السنة التالية ▶</button>
    <input id="fq" placeholder="🔍 بحث" value="${esc(state.filters.q)}">${groupSelect()}
    <button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button></div>
  <div class="table-wrap grid"><table><thead><tr><th class="num">#</th><th>الطالب</th><th class="num">الرسم</th>
    ${months.map((m) => `<th class="num">${AR_MONTHS[+m.slice(5) - 1]}</th>`).join("")}
    <th class="num">المدفوع</th><th class="num">المستحق</th><th class="num">المتأخرات</th><th class="no-print">تذكير</th></tr></thead>
  <tbody>${rows || '<tr><td colspan="18" class="muted">لا يوجد طلاب</td></tr>'}</tbody>
  <tfoot><tr><td></td><td>المجموع</td><td></td>${colTotals.map((t) => `<td class="num">${num(t)}</td>`).join("")}
    <td class="num">${num(totPaid)}</td><td class="num">${num(totDue)}</td><td class="num">${num(totLate)}</td><td class="no-print"></td></tr></tfoot></table></div>`;
}
bind.fees = () => {
  bindFilters();
  $("#py").onclick = () => { state.filters.year = shiftMonth(academicMonths(state.filters.year)[0], -12); render(); };
  $("#ny").onclick = () => { state.filters.year = shiftMonth(academicMonths(state.filters.year)[0], 12); render(); };
  document.querySelectorAll("td.cell").forEach((td) => td.onclick = () => {
    const s = state.students.find((x) => x.id === td.dataset.s); const m = td.dataset.m;
    const p = state.payments.find((x) => x.id === `${m}_${s.id}`);
    if (p) { confirmBox(`إلغاء دفع ${s.name} لشهر ${monthName(m)} (${money(p.amount)})؟`, () =>
      deleteDoc(doc(db, "payments", p.id)).then(() => { audit("إلغاء دفع", `${s.name} — ${monthName(m)} — ${money(p.amount)}`); toast("تم إلغاء الدفع"); }).catch(fail), "إلغاء الدفع"); return; }
    payForm(s, m, "monthly", s.monthlyFee);
  });
};
function payForm(s, m, type, def) {
  openModal(`<h3>${type === "monthly" ? `دفع ${monthName(m)}` : "رسوم التسجيل"} — ${esc(s.name)}</h3>
    <label>المبلغ</label><input id="a" type="number" min="0" value="${def || ""}"><label>تاريخ الدفع</label><input id="d" type="date" value="${today()}">
    <div class="modal-actions"><button class="btn" id="save">حفظ</button><button class="btn outline" id="cancel">إلغاء</button></div>`, (b) => {
    b.querySelector("#a").focus();
    b.querySelector("#cancel").onclick = closeModal;
    b.querySelector("#save").onclick = () => {
      const amount = +b.querySelector("#a").value || 0; if (amount <= 0) { toast("أدخل المبلغ"); return; }
      const id = type === "monthly" ? `${m}_${s.id}` : `reg_${s.id}`;
      setDoc(doc(db, "payments", id), { studentId: s.id, studentName: s.name, month: type === "monthly" ? m : currentMonth(), amount,
        paidOn: b.querySelector("#d").value || today(), recordedBy: state.profile?.name || "", type, updatedAt: new Date() })
        .then(() => { audit(type === "monthly" ? "دفع رسوم شهرية" : "دفع رسوم تسجيل", `${s.name} — ${type === "monthly" ? monthName(m) + " — " : ""}${money(amount)}`); toast("تم تسجيل الدفع"); })
        .catch(fail);
      closeModal();
    };
  });
}

// ---------------------------------------------------------------- registration fees
function viewRegistration() {
  const reg = {}; state.payments.filter((p) => p.type === "registration").forEach((p) => { reg[p.studentId] = p; });
  const list = filterStudents(state.students).filter((s) => (s.registrationFee || 0) > 0 || reg[s.id]);
  const paid = list.reduce((a, s) => a + (reg[s.id]?.amount || 0), 0);
  const rest = list.filter((s) => !reg[s.id]).reduce((a, s) => a + (s.registrationFee || 0), 0);
  return `<h1>رسوم التسجيل</h1><div class="sub">تُدفع مرة واحدة لكل طالب</div>
  <div class="cards"><div class="card stat green"><div class="l">المحصَّل</div><div class="v">${amt(paid)}</div></div>
  <div class="card stat red"><div class="l">المتبقي</div><div class="v">${amt(rest)}</div></div>
  <div class="card stat"><div class="l">لم يدفعوا</div><div class="v">${list.filter((s) => !reg[s.id]).length}</div></div></div>
  <div class="toolbar"><input id="fq" placeholder="🔍 بحث" value="${esc(state.filters.q)}">${groupSelect()}<button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button></div>
  <div class="table-wrap"><table><thead><tr><th class="num">#</th><th>الطالب</th><th>الحلقة</th><th class="num">المستحق</th><th class="num">المدفوع</th><th>تاريخ الدفع</th><th>الحالة</th><th class="no-print"></th></tr></thead><tbody>
  ${list.map((s, i) => { const p = reg[s.id]; return `<tr><td class="num">${i + 1}</td><td><b>${esc(s.name)}</b></td><td>${esc(s.group)}</td>
    <td class="num">${num(s.registrationFee)}</td><td class="num">${p ? num(p.amount) : ""}</td><td>${esc(p?.paidOn || "")}</td>
    <td>${p ? '<span class="chip ok">مدفوع</span>' : '<span class="chip no">غير مدفوع</span>'}</td>
    <td class="actions no-print">${p ? `<button class="btn small outline" data-undo="${s.id}">إلغاء</button>` : `<button class="btn small" data-pay="${s.id}">تسجيل الدفع</button>`}</td></tr>`; }).join("")}
  </tbody></table></div>`;
}
bind.registration = () => {
  bindFilters();
  document.querySelectorAll("[data-pay]").forEach((b) => b.onclick = () => { const s = state.students.find((x) => x.id === b.dataset.pay); payForm(s, null, "registration", s.registrationFee); });
  document.querySelectorAll("[data-undo]").forEach((b) => b.onclick = () => { const s = state.students.find((x) => x.id === b.dataset.undo);
    confirmBox(`إلغاء رسوم تسجيل ${s.name}؟`, () => deleteDoc(doc(db, "payments", `reg_${s.id}`)).then(() => { audit("إلغاء دفع", `${s.name} — رسوم تسجيل`); toast("تم الإلغاء"); }).catch(fail), "إلغاء الدفع"); });
};

// ---------------------------------------------------------------- expenses
function viewExpenses() {
  const m = state.filters.expMonth;
  const list = state.expenses.filter((e) => e.month === m).sort((a, b) => (b.date || "").localeCompare(a.date || ""));
  const total = list.reduce((a, e) => a + (e.amount || 0), 0);
  const byCat = {}; list.forEach((e) => { byCat[e.category] = (byCat[e.category] || 0) + (e.amount || 0); });
  return `<h1>المصروفات</h1><div class="sub">${monthName(m)}</div>
  <div class="toolbar"><button class="btn outline small" id="pm">◀</button><b>${monthName(m)}</b><button class="btn outline small" id="nm">▶</button>
    <button class="btn" id="adde">+ تسجيل مصروف</button><button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button></div>
  <div class="cards"><div class="card stat red"><div class="l">مجموع المصروفات</div><div class="v">${amt(total)}</div></div>
  ${Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([c, v]) => `<div class="card stat"><div class="l">${esc(c)}</div><div class="v" style="font-size:18px">${amt(v)}</div></div>`).join("")}</div>
  <div class="table-wrap"><table><thead><tr><th>التاريخ</th><th>النوع</th><th>البيان</th><th class="num">المبلغ</th><th>سجّله</th><th class="no-print"></th></tr></thead><tbody>
  ${list.map((e) => `<tr><td>${esc(e.date)}</td><td>${esc(e.category)}</td><td>${esc(e.note)}${e.observation ? ` <span class="muted">(${esc(e.observation)})</span>` : ""}</td>
    <td class="num"><b>${num(e.amount)}</b></td><td>${esc(e.recordedBy)}</td>
    <td class="actions no-print">${e.salaryMonth ? '<span class="muted" style="font-size:12px">من شاشة الرواتب</span>' :
      `<button class="btn small outline" data-e="${e.id}">تعديل</button><button class="btn small danger" data-de="${e.id}">حذف</button>`}</td></tr>`).join("")
    || '<tr><td colspan="6" class="muted">لا توجد مصروفات لهذا الشهر</td></tr>'}
  </tbody><tfoot><tr><td colspan="3">المجموع</td><td class="num">${num(total)}</td><td colspan="2"></td></tr></tfoot></table></div>`;
}
bind.expenses = () => {
  $("#pm").onclick = () => { state.filters.expMonth = shiftMonth(state.filters.expMonth, -1); render(); };
  $("#nm").onclick = () => { state.filters.expMonth = shiftMonth(state.filters.expMonth, 1); render(); };
  $("#adde").onclick = () => expenseForm({ date: today(), category: EXPENSE_CATEGORIES[1] });
  document.querySelectorAll("[data-e]").forEach((b) => b.onclick = () => expenseForm(state.expenses.find((x) => x.id === b.dataset.e)));
  document.querySelectorAll("[data-de]").forEach((b) => b.onclick = () => { const e = state.expenses.find((x) => x.id === b.dataset.de);
    confirmBox(`حذف المصروف (${e.category} — ${money(e.amount)})؟`, () => deleteDoc(doc(db, "expenses", e.id))
      .then(() => { audit("حذف مصروف", `${e.category} — ${money(e.amount)} (${e.date})`); toast("تم حذف المصروف"); }).catch(fail)); });
};
function expenseForm(e) {
  openModal(`<h3>${e.id ? "تعديل المصروف" : "مصروف جديد"}</h3>
    <label>النوع</label><select id="c">${EXPENSE_CATEGORIES.map((c) => `<option ${e.category === c ? "selected" : ""}>${c}</option>`).join("")}</select>
    <div class="row2"><div><label>المبلغ</label><input id="a" type="number" min="0" value="${e.amount || ""}"></div>
    <div><label>التاريخ</label><input id="d" type="date" value="${esc(e.date)}"></div></div>
    <label>البيان / ملاحظة</label><input id="n" value="${esc(e.note)}">
    <div class="modal-actions"><button class="btn" id="save">حفظ</button><button class="btn outline" id="cancel">إلغاء</button></div>`, (b) => {
    b.querySelector("#cancel").onclick = closeModal;
    b.querySelector("#save").onclick = () => {
      const amount = +b.querySelector("#a").value || 0; if (amount <= 0) { toast("أدخل المبلغ"); return; }
      const date = b.querySelector("#d").value || today();
      const ref = e.id ? doc(db, "expenses", e.id) : doc(collection(db, "expenses"));
      const category = b.querySelector("#c").value;
      setDoc(ref, { category, amount, date, month: date.slice(0, 7), note: b.querySelector("#n").value.trim(),
        recordedBy: state.profile?.name || "", updatedAt: new Date() }, { merge: true })
        .then(() => { audit(e.id ? "تعديل مصروف" : "تسجيل مصروف", `${category} — ${money(amount)} (${date})`); toast("تم حفظ المصروف"); }).catch(fail);
      state.filters.expMonth = date.slice(0, 7);
      closeModal();
    };
  });
}

// ---------------------------------------------------------------- summary (academic year)
function viewSummary() {
  const months = academicMonths(state.filters.year);
  const inYear = (x) => x.month >= months[0] && x.month <= months[11];
  const ps = state.payments.filter(inYear), es = state.expenses.filter(inYear);
  const monthly = ps.filter((p) => p.type === "monthly").reduce((a, p) => a + (p.amount || 0), 0);
  const reg = ps.filter((p) => p.type === "registration").reduce((a, p) => a + (p.amount || 0), 0);
  const exp = es.reduce((a, e) => a + (e.amount || 0), 0);
  const cats = {}; es.forEach((e) => { cats[e.category] = (cats[e.category] || 0) + (e.amount || 0); });
  const rows = months.map((m) => {
    const mo = ps.filter((p) => p.month === m && p.type === "monthly").reduce((a, p) => a + (p.amount || 0), 0);
    const rg = ps.filter((p) => p.month === m && p.type === "registration").reduce((a, p) => a + (p.amount || 0), 0);
    const ex = es.filter((e) => e.month === m).reduce((a, e) => a + (e.amount || 0), 0);
    const payers = ps.filter((p) => p.month === m && p.type === "monthly").length;
    return `<tr><td>${monthName(m)}</td><td class="num">${num(mo)}</td><td class="num">${payers || ""}</td><td class="num">${num(rg)}</td>
      <td class="num">${num(mo + rg)}</td><td class="num">${num(ex)}</td><td class="num" style="color:${mo + rg - ex < 0 ? "var(--red)" : "var(--green)"}"><b>${num(mo + rg - ex)}</b></td></tr>`;
  }).join("");
  return `<h1>الملخص المالي</h1><div class="sub">العام الدراسي ${yearLabel(state.filters.year)} (سبتمبر – أوت)</div>
  <div class="toolbar"><button class="btn outline small" id="py">◀ السنة السابقة</button><b>${yearLabel(state.filters.year)}</b>
    <button class="btn outline small" id="ny">السنة التالية ▶</button><button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button></div>
  <div class="cards"><div class="card stat green"><div class="l">الرسوم الشهرية</div><div class="v">${amt(monthly)}</div></div>
    <div class="card stat green"><div class="l">رسوم التسجيل</div><div class="v">${amt(reg)}</div></div>
    <div class="card stat"><div class="l">إجمالي الإيرادات</div><div class="v">${amt(monthly + reg)}</div></div>
    <div class="card stat red"><div class="l">المصروفات</div><div class="v">${amt(exp)}</div></div>
    <div class="card stat ${monthly + reg - exp >= 0 ? "green" : "red"}"><div class="l">الرصيد الصافي</div><div class="v">${amt(monthly + reg - exp)}</div></div></div>
  <div class="panel"><h3>حسب الشهر</h3><div class="table-wrap"><table><thead><tr><th>الشهر</th><th class="num">الرسوم الشهرية</th><th class="num">عدد الدافعين</th>
    <th class="num">رسوم التسجيل</th><th class="num">الإيرادات</th><th class="num">المصروفات</th><th class="num">الصافي</th></tr></thead>
    <tbody>${rows}</tbody><tfoot><tr><td>المجموع</td><td class="num">${num(monthly)}</td><td></td><td class="num">${num(reg)}</td>
    <td class="num">${num(monthly + reg)}</td><td class="num">${num(exp)}</td><td class="num">${num(monthly + reg - exp)}</td></tr></tfoot></table></div></div>
  ${Object.keys(cats).length ? `<div class="panel"><h3>المصروفات حسب النوع</h3><div class="table-wrap"><table><tbody>
    ${Object.entries(cats).sort((a, b) => b[1] - a[1]).map(([c, v]) => `<tr><td>${esc(c)}</td><td class="num">${num(v)}</td></tr>`).join("")}</tbody></table></div></div>` : ""}
  <div class="panel"><h3>حسب الحلقة (الرسوم الشهرية)</h3><div class="table-wrap"><table><thead><tr><th>الحلقة</th><th class="num">الطلاب</th><th class="num">المحصَّل</th></tr></thead><tbody>
    ${groups().map((g) => { const ids = new Set(state.students.filter((s) => s.group === g).map((s) => s.id));
      return `<tr><td>${esc(g)}</td><td class="num">${ids.size}</td><td class="num">${num(ps.filter((p) => p.type === "monthly" && ids.has(p.studentId)).reduce((a, p) => a + (p.amount || 0), 0))}</td></tr>`; }).join("")}
  </tbody></table></div></div>`;
}
bind.summary = () => {
  $("#py").onclick = () => { state.filters.year = shiftMonth(academicMonths(state.filters.year)[0], -12); render(); };
  $("#ny").onclick = () => { state.filters.year = shiftMonth(academicMonths(state.filters.year)[0], 12); render(); };
};

// ---------------------------------------------------------------- staff (teachers, administration, supervisors, workers)
const STAFF_CATS = [["مدرس", "المدرسون"], ["إداري", "الإداريون"], ["مراقب", "المراقبون"], ["عامل", "العمال"]];
const PAY_MODES = ["نقدًا", "Orange Money", "Moov Money", "تحويل بنكي"];
const SALARY_CATEGORY = "رواتب المعلمين";

function viewStaff() {
  const total = state.staff.filter((t) => (t.status || ACTIVE) === ACTIVE).reduce((a, t) => a + (t.salary || 0), 0);
  const section = ([cat, plural]) => {
    const list = state.staff.filter((t) => (t.category || "مدرس") === cat);
    if (!list.length) return "";
    return `<div class="panel"><h3>${plural} (${list.length})</h3><div class="table-wrap"><table><thead><tr>
      <th class="num">#</th><th>الاسم</th><th>الوظيفة</th><th>الهاتف</th>${cat === "مدرس" ? "<th>الحلقات</th><th class=\"num\">الطلاب</th>" : ""}
      <th class="num">الراتب الأساسي</th><th>طريقة الدفع</th><th>تاريخ التعيين</th><th>الحالة</th><th class="no-print"></th></tr></thead><tbody>
      ${list.map((t, i) => { const hs = state.halaqat.filter((h) => (h.teacher || "").trim() === (t.name || "").trim());
        const cnt = state.students.filter((s) => hs.some((h) => h.name === s.group) && (s.status || ACTIVE) === ACTIVE).length;
        return `<tr data-st="${t.id}" style="cursor:pointer" title="اضغط لفتح ملف الموظف"><td class="num">${i + 1}</td><td><b>${esc(t.name)}</b>${t.notes ? `<div class="muted" style="font-size:12px">${esc(t.notes)}</div>` : ""}</td>
        <td>${esc(t.function)}</td><td dir="ltr" style="text-align:right">${esc(t.phone)}</td>
        ${cat === "مدرس" ? `<td>${hs.map((h) => esc(h.name)).join("، ") || '<span class="muted">—</span>'}</td><td class="num">${hs.length ? cnt : ""}</td>` : ""}
        <td class="num">${num(t.salary)}</td><td>${esc(t.paymentMode)}</td><td>${esc(t.hireDate)}</td>
        <td><span class="chip ${(t.status || ACTIVE) === ACTIVE ? "ok" : "gray"}">${esc(t.status || ACTIVE)}</span></td>
        <td class="actions no-print"><button class="btn small outline">تعديل</button></td></tr>`; }).join("")}
      </tbody><tfoot><tr><td></td><td>المجموع</td><td colspan="${cat === "مدرس" ? 4 : 2}"></td><td class="num">${num(list.reduce((a, t) => a + (t.salary || 0), 0))}</td><td colspan="4"></td></tr></tfoot></table></div></div>`;
  };
  return `<h1>الموظفون</h1><div class="sub">المدرسون والإداريون والمراقبون والعمال</div>
  <div class="cards"><div class="card stat green"><div class="l">الموظفون النشطون</div><div class="v">${state.staff.filter((t) => (t.status || ACTIVE) === ACTIVE).length}</div></div>
  ${STAFF_CATS.map(([c, p]) => `<div class="card stat"><div class="l">${p}</div><div class="v">${state.staff.filter((t) => (t.category || "مدرس") === c).length}</div></div>`).join("")}
  <div class="card stat gold"><div class="l">مجموع الرواتب الشهرية</div><div class="v">${amt(total)}</div></div></div>
  <div class="toolbar"><button class="btn" id="adds">+ إضافة موظف</button><button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button></div>
  ${STAFF_CATS.map(section).join("") || '<div class="panel muted">لا يوجد موظفون بعد</div>'}`;
}
bind.staff = () => {
  $("#adds").onclick = () => staffForm({ category: "مدرس", status: ACTIVE, hireDate: today() });
  document.querySelectorAll("[data-st]").forEach((b) => b.onclick = () => staffForm(state.staff.find((t) => t.id === b.dataset.st)));
};
function staffForm(t) {
  openModal(`<h3>${t.id ? "ملف الموظف" : "موظف جديد"}</h3>
    <label>الاسم الكامل</label><input id="n" value="${esc(t.name)}">
    <div class="row2"><div><label>الفئة</label><select id="c">${STAFF_CATS.map(([c]) => `<option ${t.category === c ? "selected" : ""}>${c}</option>`).join("")}</select></div>
    <div><label>الوظيفة</label><input id="f" value="${esc(t.function)}" placeholder="مدرس الحلقة، سكرتير، طبّاخة…"></div></div>
    <div class="row2"><div><label>الهاتف</label><input id="ph" dir="ltr" value="${esc(t.phone)}"></div>
    <div><label>الراتب الأساسي</label><input id="sa" type="number" min="0" value="${t.salary || ""}"></div></div>
    <div class="row2"><div><label>طريقة الدفع</label><select id="pm"><option value=""></option>${PAY_MODES.map((m) => `<option ${t.paymentMode === m ? "selected" : ""}>${m}</option>`).join("")}</select></div>
    <div><label>الحالة</label><select id="st"><option ${t.status !== "متوقف" ? "selected" : ""}>نشط</option><option ${t.status === "متوقف" ? "selected" : ""}>متوقف</option></select></div></div>
    <div class="row2"><div><label>تاريخ التعيين</label><input id="hd" type="date" value="${esc(t.hireDate)}"></div>
    <div><label>تاريخ التوقف عن العمل</label><input id="cd" type="date" value="${esc(t.cessationDate)}"></div></div>
    <label>ملاحظات (الاسم بالفرنسية، المؤهل…)</label><input id="no" value="${esc(t.notes)}">
    <div class="modal-actions"><button class="btn" id="save">حفظ</button><button class="btn outline" id="cancel">إلغاء</button>
    ${t.id ? '<button class="btn danger" id="del">حذف</button>' : ""}</div>`, (b) => {
    const v = (id) => b.querySelector("#" + id).value.trim();
    b.querySelector("#cancel").onclick = closeModal;
    b.querySelector("#del") && (b.querySelector("#del").onclick = () => confirmBox(`حذف ملف «${t.name}»؟ تبقى رواتبه المسجلة. يُفضَّل تحديد تاريخ التوقف بدل الحذف.`, () =>
      deleteDoc(doc(db, "teachers", t.id)).then(() => { audit("حذف معلم", t.name); toast("تم حذف الموظف"); }).catch(fail)));
    b.querySelector("#save").onclick = () => {
      if (!v("n")) { toast("الاسم مطلوب"); return; }
      const ref = t.id ? doc(db, "teachers", t.id) : doc(collection(db, "teachers"));
      const status = v("cd") ? "متوقف" : v("st");
      setDoc(ref, { name: v("n"), category: v("c"), function: v("f"), phone: v("ph"), salary: +v("sa") || 0, paymentMode: v("pm"),
        status, hireDate: v("hd"), cessationDate: v("cd"), notes: v("no"), updatedAt: new Date() }, { merge: true })
        .then(() => { if (!t.id) audit("إضافة معلم", v("n")); toast("تم حفظ بيانات الموظف"); }).catch(fail);
      closeModal();
    };
  });
}

// ---------------------------------------------------------------- payroll (same rules as the app)
// net = base − advance; received − net = withdrawn from (−) / left in (+) the deposit held by the administration.
function viewPayroll() {
  const m = state.filters.payMonth;
  const year = academicMonths(m);
  const byId = {}; state.payroll.filter((p) => p.month === m).forEach((p) => { byId[p.staffId] = p; });
  const staff = state.staff.filter((t) => ((t.status || ACTIVE) === ACTIVE && (!t.hireDate || t.hireDate.slice(0, 7) <= m)) || byId[t.id]);
  const T = { base: 0, adv: 0, net: 0, rec: 0 };
  const rows = (cat, plural) => {
    const list = staff.filter((t) => (t.category || "مدرس") === cat); if (!list.length) return "";
    const st = { base: 0, adv: 0, net: 0, rec: 0 };
    const body = list.map((t) => { const p = byId[t.id]; const base = p?.base ?? t.salary ?? 0, adv = p?.advance || 0, net = base - adv, rec = p?.received || 0;
      st.base += base; st.adv += adv; st.net += net; st.rec += p ? rec : 0;
      const ch = p ? net - rec : 0;
      return `<tr class="clickable" data-pay="${t.id}" style="cursor:pointer"><td><b>${esc(t.name)}</b><div class="muted" style="font-size:12px">${esc(t.function)}</div></td>
        <td class="num">${num(base)}</td><td class="num">${adv ? num(adv) : "—"}</td><td class="num"><b>${num(net)}</b></td>
        <td class="num">${p ? num(rec) : '<span class="chip no">لم يُسجَّل</span>'}</td>
        <td class="num" style="color:${ch > 0 ? "#8a6d00" : ch < 0 ? "var(--red)" : "inherit"}">${p ? signed(ch) : ""}</td>
        <td>${esc(p?.receivedDate || "")}</td><td>${esc(p?.paymentMode || t.paymentMode || "")}</td></tr>`; }).join("");
    T.base += st.base; T.adv += st.adv; T.net += st.net; T.rec += st.rec;
    return `<tr><td colspan="8" style="background:#f7f5ec;font-weight:800;color:var(--green)">${plural}</td></tr>${body}
      <tr style="font-weight:700;background:#fafafa"><td>مجموع ${plural}</td><td class="num">${num(st.base)}</td><td class="num">${num(st.adv)}</td><td class="num">${num(st.net)}</td><td class="num">${num(st.rec)}</td><td colspan="3"></td></tr>`;
  };
  const table = STAFF_CATS.map(([c, p]) => rows(c, p)).join("");
  const yp = state.payroll.filter((p) => p.month >= year[0] && p.month <= year[11]);
  const deposits = state.staff.map((t) => { const mine = yp.filter((p) => p.staffId === t.id);
    const due = mine.reduce((a, p) => a + (p.base || 0) - (p.advance || 0), 0), got = mine.reduce((a, p) => a + (p.received || 0), 0);
    return { t, due, got, bal: due - got }; });
  return `<h1>الرواتب</h1><div class="sub">${monthName(m)} — اضغط على اسم الموظف لتسجيل راتبه أو تعديله</div>
  <div class="toolbar"><button class="btn outline small" id="ppm">◀</button><b>${monthName(m)}</b><button class="btn outline small" id="npm">▶</button>
    <button class="btn outline no-print" onclick="window.print()">🖨️ طباعة كشف الرواتب</button></div>
  <div class="cards"><div class="card stat"><div class="l">الرواتب الأساسية</div><div class="v">${amt(T.base)}</div></div>
    <div class="card stat gold"><div class="l">السلف</div><div class="v">${amt(T.adv)}</div></div>
    <div class="card stat"><div class="l">صافي الرواتب</div><div class="v">${amt(T.net)}</div></div>
    <div class="card stat green"><div class="l">المستلم فعلًا</div><div class="v">${amt(T.rec)}</div></div>
    <div class="card stat red"><div class="l">لم يُسجَّل بعد</div><div class="v">${staff.filter((t) => !byId[t.id]).length}</div></div></div>
  <div class="panel"><h3>كشف رواتب ${monthName(m)}</h3><div class="table-wrap"><table><thead><tr><th>الاسم</th><th class="num">الراتب الأساسي</th><th class="num">السلفة</th>
    <th class="num">صافي الراتب</th><th class="num">المستلم فعلًا</th><th class="num">المتروك (+) / المسحوب (−)</th><th>تاريخ الاستلام</th><th>طريقة الدفع</th></tr></thead>
    <tbody>${table || '<tr><td colspan="8" class="muted">لا يوجد موظفون — أضفهم من صفحة «الموظفون»</td></tr>'}</tbody>
    <tfoot><tr><td>المجموع العام</td><td class="num">${num(T.base)}</td><td class="num">${num(T.adv)}</td><td class="num">${num(T.net)}</td><td class="num">${num(T.rec)}</td><td colspan="3"></td></tr></tfoot></table></div></div>
  <div class="panel"><h3>الرواتب المتروكة أمانةً لدى الإدارة — العام الدراسي ${yearLabel(m)}</h3><div class="table-wrap"><table><thead><tr><th>الاسم</th>
    <th class="num">مجموع المستحق</th><th class="num">مجموع ما استلمه</th><th class="num">الرصيد لدى الإدارة</th></tr></thead><tbody>
    ${deposits.map((d) => `<tr><td>${esc(d.t.name)}</td><td class="num">${num(d.due)}</td><td class="num">${num(d.got)}</td>
      <td class="num" style="color:${d.bal > 0 ? "#8a6d00" : "inherit"}"><b>${num(d.bal)}</b></td></tr>`).join("")}</tbody>
    <tfoot><tr><td>المجموع</td><td class="num">${num(deposits.reduce((a, d) => a + d.due, 0))}</td><td class="num">${num(deposits.reduce((a, d) => a + d.got, 0))}</td>
    <td class="num">${num(deposits.reduce((a, d) => a + d.bal, 0))}</td></tr></tfoot></table></div></div>`;
}
bind.payroll = () => {
  $("#ppm").onclick = () => { state.filters.payMonth = shiftMonth(state.filters.payMonth, -1); render(); };
  $("#npm").onclick = () => { state.filters.payMonth = shiftMonth(state.filters.payMonth, 1); render(); };
  document.querySelectorAll("[data-pay]").forEach((r) => r.onclick = () => payrollForm(state.staff.find((t) => t.id === r.dataset.pay), state.filters.payMonth));
};
function payrollForm(t, m) {
  const id = `${m}_${t.id}`; const p = state.payroll.find((x) => x.id === id);
  const base0 = p?.base ?? t.salary ?? 0, adv0 = p?.advance || 0;
  openModal(`<h3>راتب ${esc(t.name)} — ${monthName(m)}</h3>
    <div class="row2"><div><label>الراتب الأساسي</label><input id="b" type="number" min="0" value="${base0 || ""}"></div>
    <div><label>السلفة (تُخصم)</label><input id="a" type="number" min="0" value="${adv0 || ""}"></div></div>
    <div id="netline" style="margin-top:8px;font-weight:700;color:var(--green)"></div>
    <div class="row2"><div><label>المبلغ المستلم فعلًا</label><input id="r" type="number" min="0" value="${p ? p.received : ""}" placeholder="فارغ = الصافي كاملًا"></div>
    <div><label>تاريخ الاستلام</label><input id="d" type="date" value="${esc(p?.receivedDate || today())}"></div></div>
    <label>طريقة الدفع</label><select id="pm"><option value=""></option>${PAY_MODES.map((x) => `<option ${(p?.paymentMode || t.paymentMode) === x ? "selected" : ""}>${x}</option>`).join("")}</select>
    <div id="depline" class="muted" style="margin-top:8px"></div>
    <div class="modal-actions"><button class="btn" id="save">حفظ</button><button class="btn outline" id="cancel">إلغاء</button>
    ${p ? '<button class="btn danger" id="del">حذف سجل هذا الشهر</button>' : ""}</div>`, (b) => {
    const val = (k) => +b.querySelector("#" + k).value || 0;
    const upd = () => { const net = val("b") - val("a"); const rRaw = b.querySelector("#r").value; const r = rRaw === "" ? net : +rRaw;
      b.querySelector("#netline").textContent = `صافي الراتب: ${money(net)}`;
      const ch = net - r; b.querySelector("#depline").textContent = ch > 0 ? `سيُترك أمانةً لدى الإدارة: ${money(ch)}` : ch < 0 ? `سيُسحب من الأمانة: ${money(-ch)}` : ""; };
    ["b", "a", "r"].forEach((k) => b.querySelector("#" + k).oninput = upd); upd();
    b.querySelector("#cancel").onclick = closeModal;
    b.querySelector("#del") && (b.querySelector("#del").onclick = () => confirmBox(`حذف راتب ${t.name} لشهر ${monthName(m)}؟`, async () => {
      try { const batch = writeBatch(db); batch.delete(doc(db, "payroll", id)); batch.delete(doc(db, "expenses", "salary_" + id)); await batch.commit();
        audit("حذف راتب", `${t.name} — ${monthName(m)}`); toast("حُذف سجل الراتب"); } catch (e) { fail(e); } }));
    b.querySelector("#save").onclick = async () => {
      const base = val("b"), advance = val("a"); if (base <= 0) { toast("أدخل الراتب الأساسي"); return; }
      const rRaw = b.querySelector("#r").value; const received = rRaw === "" ? Math.max(0, base - advance) : +rRaw;
      const date = b.querySelector("#d").value || today(), mode = b.querySelector("#pm").value;
      try {
        const batch = writeBatch(db);
        batch.set(doc(db, "payroll", id), { staffId: t.id, name: t.name, month: m, base, advance, received, receivedDate: date,
          paymentMode: mode, recordedBy: state.profile?.name || "", updatedAt: new Date() });
        const cash = advance + received;  // cash actually paid → mirrored as an expense (same as the app)
        if (cash > 0) batch.set(doc(db, "expenses", "salary_" + id), { category: SALARY_CATEGORY, amount: cash, date, month: date.slice(0, 7),
          note: `راتب ${t.name} — ${monthName(m)}`, teacherId: t.id, salaryMonth: m, recordedBy: state.profile?.name || "", updatedAt: new Date() });
        else batch.delete(doc(db, "expenses", "salary_" + id));
        await batch.commit();
        const left = base - advance - received;
        audit("راتب", `${t.name} — ${monthName(m)}: أساسي ${money(base)}، سلفة ${money(advance)}، مستلم ${money(received)}` +
          (left > 0 ? `، متروك أمانة ${money(left)}` : left < 0 ? `، مسحوب من الأمانة ${money(-left)}` : ""));
        toast(`حُفظ راتب ${t.name}`); closeModal();
      } catch (e) { fail(e); }
    };
  });
}
