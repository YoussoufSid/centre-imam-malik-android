// Unit tests for the dashboard's pure logic. Same examples as the phone app's tests (LogicTest.kt),
// so a rule can never drift apart between the app and the website.   Run: node --test web/tests
import test from "node:test";
import assert from "node:assert/strict";
import * as L from "../logic.js";

const plain = (s) => s.replace(/[⁦⁩]/g, "");

test("memorization periods (week = Saturday–Friday, four default terms, academic year)", () => {
  // 2026-11-11 is a Wednesday
  const w = L.periodRange("WEEK", "2026-11-11");
  assert.deepEqual([w.from, w.to, plain(w.label)], ["2026-11-07", "2026-11-13", "أسبوع 07/11 – 13/11"]);
  assert.equal(L.periodRange("MONTH", "2026-11-11").from, "2026-11-01");
  const t1 = L.periodRange("TERM", "2026-11-11"), t2 = L.periodRange("TERM", "2027-02-10");
  assert.deepEqual([t1.from, t1.to], ["2026-09-01", "2026-11-30"]);
  assert.deepEqual([t2.from, t2.to], ["2026-12-01", "2027-02-28"]);
  const y = L.periodRange("YEAR", "2027-05-01");
  assert.deepEqual([y.from, y.to], ["2026-09-01", "2027-08-31"]);
  assert.equal(L.shiftPeriod("TERM", "2026-11-11", 1), "2026-12-01");
  assert.equal(L.shiftPeriod("TERM", "2026-11-11", -1), "2026-06-01");
  assert.equal(L.shiftPeriod("WEEK", "2026-11-11", 1), "2026-11-18");
  assert.equal(L.shiftPeriod("MONTH", "2026-11-30", 1), "2026-12-01");
});

test("terms set by the administration (holiday gap, year change) and events", () => {
  const terms = [{ name: "الفصل الأول", from: "2026-10-01", to: "2026-12-15" }, { name: "الفصل الثاني", from: "2027-01-05", to: "2027-03-20" },
    { name: "الفصل الثالث", from: "2027-04-01", to: "2027-05-31" }, { name: "الفصل الرابع", from: "2027-06-01", to: "2027-07-15" }];
  const f = (start) => (start === 2026 ? terms : L.defaultTerms(start));
  const r = L.periodRange("TERM", "2026-11-11", f);
  assert.deepEqual([r.from, r.to], ["2026-10-01", "2026-12-15"]);
  assert.equal(L.periodRange("TERM", "2026-12-25", f).from, "2026-10-01");           // holidays between terms → previous term
  assert.equal(L.shiftPeriod("TERM", "2026-11-11", 1, f), "2027-01-05");
  assert.equal(L.shiftPeriod("TERM", "2027-07-01", 1, f), "2027-09-01");              // next year falls back to default terms
  const ev = [{ id: "1", type: "عطلة", title: "عطلة الشتاء", from: "2026-12-16", to: "2027-01-04" },
    { id: "2", type: "اختبارات", title: "اختبارات الفصل الأول", from: "2026-12-08", to: "2026-12-12" }];
  assert.deepEqual(L.eventsOn(ev, "2026-12-20").map((e) => e.id), ["1"]);
  assert.deepEqual(L.upcomingEvents(ev, "2026-12-01", 30).map((e) => e.id), ["2", "1"]);
  assert.deepEqual(L.upcomingEvents(ev, "2026-10-01", 30), []);
});

test("school days: every day except Friday and Sunday, holidays excluded", () => {
  assert.equal(L.isoDow("2026-11-11"), 3); assert.equal(L.isoDow("2026-11-13"), 5); assert.equal(L.isoDow("2026-11-08"), 7);
  assert.equal(L.isSchoolDay("2026-11-13", L.DEFAULT_OFF, []), false);
  assert.equal(L.isSchoolDay("2026-11-07", L.DEFAULT_OFF, []), true);                  // Saturday
  assert.equal(L.schoolDays("2026-11-07", "2026-11-13", L.DEFAULT_OFF, []), 5);       // Sat, Mon, Tue, Wed, Thu
  const holiday = [{ id: "h", type: "عطلة", title: "عطلة", from: "2026-11-11", to: "2026-11-11" }];
  assert.equal(L.schoolDays("2026-11-07", "2026-11-13", L.DEFAULT_OFF, holiday), 4);
});

test("memorization summary and next position", () => {
  const a = { id: "a", name: "أ" }, b = { id: "b", name: "ب" };
  const logs = [
    { studentId: "a", date: "2026-11-10", type: "حفظ جديد", surah: "النبأ", fromAyah: 1, toAyah: 10, grade: "ممتاز" },
    { studentId: "a", date: "2026-11-11", type: "حفظ جديد", surah: "النبأ", fromAyah: 11, toAyah: 20, grade: "جيد" },
    { studentId: "a", date: "2026-11-11", type: "مراجعة", surah: "الناس", fromAyah: 1, toAyah: 6, grade: "جيد جدًا" }];
  const rows = L.summarizeMemo([a, b], logs), r = rows[0];
  assert.deepEqual([r.newS, r.revS, r.newA, r.revA, r.days], [2, 1, 20, 6, 2]);
  assert.deepEqual(r.surahs, ["النبأ"]);
  assert.equal(r.avg, 4); assert.equal(L.gradeFromScore(r.avg), "جيد جدًا");
  assert.equal(rows[1].logs.length, 0);
  assert.deepEqual(L.nextPosition(r.logs.filter((l) => l.type === "حفظ جديد").pop()), { surah: "النبأ", from: 21, to: 30 });
  const n = L.nextPosition({ surah: "الناس", fromAyah: 1, toAyah: 6 });                // finished surah → the previous one
  assert.equal(n.surah, "الفلق"); assert.equal(n.from, 1);
  assert.equal(L.nextPosition(null), null);
});

test("fees are overdue from the 25th; arrears and amounts", () => {
  assert.equal(L.isMonthDue("2026-11", "2026-11-24"), false);
  assert.equal(L.isMonthDue("2026-11", "2026-11-25"), true);
  assert.equal(L.isMonthDue("2026-10", "2026-11-01"), true);
  assert.equal(L.isMonthDue("2026-12", "2026-11-30"), false);
  assert.equal(L.isMonthDue("2026-11", "2026-11-10", 10), true);
  const months = L.academicMonths("2026-11");
  const s = { id: "s1", name: "آدم", monthlyFee: 20000 };
  const pays = [{ studentId: "s1", month: "2026-09", amount: 20000, type: "monthly" }];
  const a = L.feeArrears(s, months, pays, "2026-11-26");
  assert.deepEqual(a.months, ["2026-10", "2026-11"]); assert.equal(a.amount, 40000);
  const b = L.feeArrears(s, months, pays, "2026-11-20");
  assert.deepEqual(b.months, ["2026-10"]); assert.equal(b.amount, 20000);
  const all = months.map((m) => ({ studentId: "s1", month: m, amount: 20000, type: "monthly" }));
  assert.equal(L.feeArrears(s, months, all, "2026-11-26").amount, 0);
});

test("numbers and identifiers", () => {
  assert.equal(L.studentNumber("s040"), "S040"); assert.equal(L.studentNumber("aBcDeFgH9xy12ab"), "XY12AB");
  assert.equal(L.staffNumber("staff_1"), "E001"); assert.equal(L.staffNumber("staff_12"), "E012"); assert.equal(L.staffNumber("aBcDeFgH9xy12ab"), "XY12AB");
  assert.equal(L.plainNum(1520000), "1 520 000");
  assert.equal(L.monthName("2026-10"), "أكتوبر 2026");
  assert.equal(L.yearLabel("2026-11"), "2026 / 2027");
});
