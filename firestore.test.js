const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const SCHOOL_ID = "482915";
const ADMIN_DEV = "dev_admin_001";
const TEACHER_DEV = "dev_teacher_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function buildStudentPayload(schoolId, created, updated) {
  return {
    schoolId,
    createdAt: created,
    updatedAt: updated,
    studentNameEn: "Nusrat Jahan Mim",
    studentNameBn: "নুসরাত জাহান মিম",
    fatherNameEn: "Kamrul Hasan",
    motherNameEn: "Salma Begum",
    dateOfBirth: "14/03/2016",
    guardianPhone: "01711000001",
    address: "Purbo Para, Ward 3, Kachua, Chandpur",
    photoBase64: "abc123samplebase64photo",
    rollNumber: "01",
    classLevel: "Class 4",
    section: "A",
    academicYear: 2026,
    totalSchoolDays: 180,
    daysPresent: 168,
    promotionStatus: "Promoted",
    customValues: { cf_brn: "20161234567890123" },
    subjectValues: { sub_bangla__f_term1: "88" },
    examResults: { exam_1__es_bangla: "85", exam_2__es_math: "92" },
    sectorNotes: { fixed_name: "Verified" },
    lastUpdatedByName: "Head Teacher Karim",
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Global schools listing and legacy unauthenticated user paths are blocked", async () => {
  const teacherDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(teacherDb.collection("schools").get());
  await assertFails(teacherDb.collection("users").doc("any_uid").get());
  await assertFails(teacherDb.collection("users").doc("any_uid").collection("students").get());
});

test("Shared School ID supports multi-teacher access without login, class attendance logs, exam results, and pending confirmations", async () => {
  const adminDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const schoolRef = adminDb.collection("schools").doc(SCHOOL_ID);

  // Create shared school workspace
  await assertSucceeds(
    schoolRef.set({
      schoolId: SCHOOL_ID,
      schoolName: "Purbo Para Govt. Primary School",
      emisCode: "91020401",
      upazilaDistrict: "Sadar, Cumilla",
      defaultAcademicYear: 2026,
      adminDeviceId: ADMIN_DEV,
      adminName: "Head Teacher Karim",
      teachers: [{ deviceId: ADMIN_DEV, name: "Head Teacher Karim", role: "ADMIN", joinedAtMs: 1700000000000 }],
      examConfigs: [
        { examId: "exam_1", title: "1st Term Exam", subjects: [] },
        { examId: "exam_2", title: "2nd Term Exam", subjects: [] },
        { examId: "exam_3", title: "Final Exam", subjects: [] },
      ],
      recentMessages: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Another teacher joins same school ID
  await assertSucceeds(
    schoolRef.update({
      teachers: [
        { deviceId: ADMIN_DEV, name: "Head Teacher Karim", role: "ADMIN", joinedAtMs: 1700000000000 },
        { deviceId: TEACHER_DEV, name: "Ayesha Khatun", role: "USER", joinedAtMs: 1700000001000 },
      ],
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Student with examResults and photoBase64
  const stuRef = schoolRef.collection("students").doc("stu_1");
  await assertSucceeds(stuRef.set(buildStudentPayload(SCHOOL_ID, created, created)));
  await assertSucceeds(
    stuRef.update({
      examResults: { exam_1__es_bangla: "91", exam_3__es_math: "96" },
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Class-separated attendance sheet log
  const attRef = schoolRef.collection("attendanceLogs").doc("2026_Class_4_2026-11-21");
  await assertSucceeds(
    attRef.set({
      schoolId: SCHOOL_ID,
      academicYear: 2026,
      classLevel: "Class 4",
      dateKey: "2026-11-21",
      monthKey: "2026-11",
      statuses: { stu_1: "P" },
      markedByName: "Head Teacher Karim",
      createdAt: created,
      updatedAt: created,
    })
  );

  // User teacher submits pending change -> Admin approves
  const pendingRef = schoolRef.collection("pendingChanges").doc("chg_1");
  await assertSucceeds(
    pendingRef.set({
      schoolId: SCHOOL_ID,
      entityType: "STUDENT_UPDATE",
      targetId: "stu_1",
      submittedByDeviceId: TEACHER_DEV,
      submittedByName: "Ayesha Khatun",
      summary: "Updated Class 4 Roll 01 marks",
      detailsText: "Bangla 1st Term = 91",
      payloadJson: '{"id":"stu_1"}',
      status: "PENDING",
      reviewedByName: "",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    pendingRef.update({
      status: "APPROVED",
      reviewedByName: "Head Teacher Karim",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // School chat message
  await assertSucceeds(
    schoolRef.collection("messages").doc("msg_1").set({
      schoolId: SCHOOL_ID,
      roomId: SCHOOL_ID,
      senderUid: TEACHER_DEV,
      senderName: "Ayesha Khatun",
      tag: "Exam / Marks",
      text: "Class 4 marks uploaded for confirmation.",
      editedFlag: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Rejects @DocumentId 'id', oversized photoBase64, non-PENDING initial change, and future timestamps", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const schoolRef = db.collection("schools").doc(SCHOOL_ID);

  const withId = buildStudentPayload(SCHOOL_ID, created, created);
  withId.id = "bad_doc";
  await assertFails(schoolRef.collection("students").doc("bad_doc").set(withId));

  const oversizedPhoto = buildStudentPayload(SCHOOL_ID, created, created);
  oversizedPhoto.photoBase64 = "a".repeat(140050);
  await assertFails(schoolRef.collection("students").doc("big_photo").set(oversizedPhoto));

  const future = new Date(Date.now() + 60000);
  await assertFails(
    schoolRef.collection("students").doc("future_doc").set(buildStudentPayload(SCHOOL_ID, future, future))
  );

  // User teacher cannot self-create an already-APPROVED pending change
  await assertFails(
    schoolRef.collection("pendingChanges").doc("cheat_1").set({
      schoolId: SCHOOL_ID,
      entityType: "STUDENT_UPDATE",
      targetId: "stu_1",
      submittedByDeviceId: TEACHER_DEV,
      submittedByName: "Ayesha",
      summary: "Cheat",
      payloadJson: "{}",
      status: "APPROVED",
      reviewedByName: "Self",
      createdAt: created,
      updatedAt: created,
    })
  );
});
