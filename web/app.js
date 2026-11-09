// Centre Imam Malik — web admin dashboard (same Firestore data as the Android app).
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  initializeFirestore, persistentLocalCache, collection, doc, onSnapshot, setDoc, deleteDoc, addDoc, writeBatch,
  getDocs, query, where
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const ADMIN_EMAIL = "sidibey487@gmail.com";
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
  students: [], halaqat: [], payments: [], expenses: [], unsub: [], loaded: false,
  filters: { q: "", group: "", feeMonth: currentMonth(), expMonth: currentMonth(), year: currentMonth() }
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
  $("#app").innerHTML = `<div class="login"><img src="logo.png" alt=""><h2>مركز الإمام مالك</h2>
    <div class="muted">لوحة الإدارة</div>
    <label>البريد الإلكتروني</label><input id="em" type="email" dir="ltr">
    <label>كلمة المرور</label><input id="pw" type="password" dir="ltr">
    <button class="btn" id="go">تسجيل الدخول</button>
    <div style="margin-top:10px"><a href="#" id="reset">نسيت كلمة المرور؟</a></div><div class="error" id="err"></div></div>`;
  const go = () => signInWithEmailAndPassword(auth, $("#em").value.trim(), $("#pw").value)
    .catch(() => { $("#err").textContent = "البريد أو كلمة المرور غير صحيحة"; });
  $("#go").onclick = go; $("#pw").onkeydown = (e) => e.key === "Enter" && go();
  $("#reset").onclick = (e) => { e.preventDefault(); const em = $("#em").value.trim();
    if (!em) { $("#err").textContent = "اكتب البريد أولًا"; return; }
    sendPasswordResetEmail(auth, em).then(() => toast("أُرسل رابط الاستعادة إلى بريدك")).catch(fail); };
}

// ---------------------------------------------------------------- shell
const NAV = [
  ["home", "🏠", "الرئيسية", false], ["students", "👥", "الطلاب", false], ["halaqat", "🕌", "الحلقات", false],
  ["fees", "💰", "الرسوم الشهرية", true], ["registration", "📝", "رسوم التسجيل", true],
  ["expenses", "🧾", "المصروفات", true], ["summary", "📊", "الملخص المالي", true]
];
window.addEventListener("hashchange", () => render());
const route = () => (location.hash.replace("#/", "") || "home");

function render() {
  if (!state.user || !state.profile || !isStaff()) return;
  const r = route();
  const views = { home: viewHome, students: viewStudents, halaqat: viewHalaqat, fees: viewFees, registration: viewRegistration, expenses: viewExpenses, summary: viewSummary };
  const allowed = NAV.find((n) => n[0] === r && (!n[3] || isAdmin())) ? r : "home";
  const focus = document.activeElement?.id; const caret = document.activeElement?.selectionStart;
  $("#app").innerHTML = `<div class="layout"><aside class="side">
      <div class="brand"><img src="logo.png" alt=""><b>${esc(state.settings.name || DEFAULT_NAME)}</b></div>
      <nav>${NAV.filter((n) => !n[3] || isAdmin()).map((n) => `<a href="#/${n[0]}" class="${n[0] === allowed ? "active" : ""}">${n[1]} ${n[2]}</a>`).join("")}</nav>
      <div class="user">${esc(state.profile?.name || state.user.email)}<br>${isAdmin() ? "الإدارة" : "معلم"}<br>
      <button id="logout">تسجيل الخروج</button></div></aside>
    <main><div class="print-head"><img src="logo.png" alt=""><h3>${esc(state.settings.name || DEFAULT_NAME)}</h3>
      <div>${esc(state.settings.address || "")}</div></div>${state.loaded ? views[allowed]() : '<div class="center muted">جارٍ تحميل البيانات…</div>'}</main></div>`;
  $("#logout").onclick = () => confirmBox("هل تريد تسجيل الخروج؟", () => signOut(auth), "خروج");
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

// ---------------------------------------------------------------- home
function viewHome() {
  const active = state.students.filter((s) => (s.status || ACTIVE) === ACTIVE);
  const m = currentMonth();
  const monthly = state.payments.filter((p) => p.type === "monthly" && p.month === m);
  const billable = active.filter((s) => (s.monthlyFee || 0) > 0);
  const unpaid = billable.filter((s) => !monthly.some((p) => p.studentId === s.id));
  return `<h1>السلام عليكم ورحمة الله وبركاته</h1><div class="sub">${esc(state.settings.name || DEFAULT_NAME)} — العام الدراسي ${yearLabel(m)}</div>
  <div class="cards">
    <div class="card stat green"><div class="l">الطلاب النشطون</div><div class="v">${active.length}</div></div>
    <div class="card stat"><div class="l">الطلاب / الطالبات</div><div class="v">${active.filter((s) => s.gender === "ذكر").length} / ${active.filter((s) => s.gender === "أنثى").length}</div></div>
    <div class="card stat"><div class="l">الحلقات</div><div class="v">${groups().length}</div></div>
    ${isAdmin() ? `<div class="card stat green"><div class="l">رسوم ${monthName(m)}</div><div class="v">${amt(monthly.reduce((a, p) => a + (p.amount || 0), 0))}</div></div>
    <div class="card stat red"><div class="l">لم يدفعوا هذا الشهر</div><div class="v">${unpaid.length}</div></div>` : ""}
  </div>
  <div class="panel"><h3>الحلقات</h3><div class="table-wrap"><table><thead><tr><th>الحلقة</th><th>المعلم</th><th class="num">الطلاب</th><th class="num">ذكور</th><th class="num">إناث</th></tr></thead><tbody>
  ${groups().map((g) => { const l = active.filter((s) => s.group === g); const h = state.halaqat.find((x) => x.name === g);
    return `<tr><td><a href="#/students" data-g="${esc(g)}" class="glink">${esc(g)}</a></td><td>${esc(h?.teacher || "")}</td><td class="num">${l.length}</td>
    <td class="num">${l.filter((s) => s.gender === "ذكر").length}</td><td class="num">${l.filter((s) => s.gender === "أنثى").length}</td></tr>`; }).join("")}
  </tbody></table></div></div>`;
}
bind.home = () => document.querySelectorAll(".glink").forEach((a) => a.onclick = () => { state.filters.group = a.dataset.g; });

// ---------------------------------------------------------------- students
function viewStudents() {
  const list = filterStudents(state.students);
  return `<h1>الطلاب</h1><div class="sub">${state.students.length} طالب مسجل</div>
  <div class="toolbar"><input id="fq" placeholder="🔍 بحث بالاسم أو الهاتف" value="${esc(state.filters.q)}">${groupSelect()}
    <button class="btn" id="add">+ إضافة طالب</button><button class="btn outline no-print" onclick="window.print()">🖨️ طباعة</button>
    <span class="muted">${list.length} نتيجة</span></div>
  <div class="table-wrap"><table><thead><tr><th class="num">#</th><th>الاسم</th><th>الجنس</th><th>الحلقة</th><th>المعلم</th><th>هاتف ولي الأمر</th>
  <th class="num">الأحزاب</th>${isAdmin() ? '<th class="num">الرسوم الشهرية</th>' : ""}<th>الحالة</th><th class="no-print"></th></tr></thead><tbody>
  ${list.map((s, i) => `<tr><td class="num">${i + 1}</td><td><b>${esc(s.name)}</b></td><td>${esc(s.gender)}</td><td>${esc(s.group)}</td>
    <td>${esc(s.teacher)}</td><td dir="ltr" style="text-align:right">${esc(s.phone)}</td><td class="num">${s.hizbs || 0}/60</td>
    ${isAdmin() ? `<td class="num">${num(s.monthlyFee)}</td>` : ""}
    <td><span class="chip ${(s.status || ACTIVE) === ACTIVE ? "ok" : "gray"}">${esc(s.status || ACTIVE)}</span></td>
    <td class="actions no-print"><button class="btn small outline" data-edit="${s.id}">تعديل</button>
    ${isAdmin() ? `<button class="btn small danger" data-del="${s.id}">حذف</button>` : ""}</td></tr>`).join("") || `<tr><td colspan="10" class="muted">لا توجد نتائج</td></tr>`}
  </tbody></table></div>`;
}
bind.students = () => {
  bindFilters();
  $("#add").onclick = () => studentForm({ status: ACTIVE, enrollmentDate: today(), group: state.filters.group && state.filters.group !== "-" ? state.filters.group : "" });
  document.querySelectorAll("[data-edit]").forEach((b) => b.onclick = () => studentForm(state.students.find((s) => s.id === b.dataset.edit)));
  document.querySelectorAll("[data-del]").forEach((b) => b.onclick = () => {
    const s = state.students.find((x) => x.id === b.dataset.del);
    confirmBox(`حذف «${s.name}» وكل سجلاته (الحضور، التسميع، الدرجات، المدفوعات)؟`, () => deleteStudent(s));
  });
};

function studentForm(s) {
  const opts = groups().map((g) => `<option ${s.group === g ? "selected" : ""}>${esc(g)}</option>`).join("");
  openModal(`<h3>${s.id ? "تعديل ملف الطالب" : "إضافة طالب"}</h3>
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
    b.querySelector("#save").onclick = () => {
      const v = (id) => b.querySelector("#" + id)?.value?.trim() ?? "";
      if (!v("n")) { toast("اسم الطالب مطلوب"); return; }
      const data = { name: v("n"), gender: v("g"), group: v("gr"), birthDate: v("bd"), enrollmentDate: v("ed"), phone: v("ph"),
        teacher: v("t"), level: v("lv"), address: v("ad"), status: v("st"), updatedAt: new Date() };
      if (isAdmin()) { data.monthlyFee = +v("mf") || 0; data.registrationFee = +v("rf") || 0; }
      const ref = s.id ? doc(db, "students", s.id) : doc(collection(db, "students"));
      if (!s.id) data.hizbs = 0;
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
    return `<tr><td class="num">${i + 1}</td><td><b>${esc(s.name)}</b><div class="muted" style="font-size:12px">${esc(s.group)}</div></td>
      <td class="num">${num(fee)}</td>${cells}<td class="num"><b>${num(paid)}</b></td><td class="num">${num(due)}</td>
      <td class="num" style="color:${late ? "var(--red)" : "inherit"}"><b>${num(late)}</b></td></tr>`;
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
    <th class="num">المدفوع</th><th class="num">المستحق</th><th class="num">المتأخرات</th></tr></thead>
  <tbody>${rows || '<tr><td colspan="18" class="muted">لا يوجد طلاب</td></tr>'}</tbody>
  <tfoot><tr><td></td><td>المجموع</td><td></td>${colTotals.map((t) => `<td class="num">${num(t)}</td>`).join("")}
    <td class="num">${num(totPaid)}</td><td class="num">${num(totDue)}</td><td class="num">${num(totLate)}</td></tr></tfoot></table></div>`;
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
