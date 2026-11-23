package com.example.data.model

import kotlin.math.roundToInt

enum class ExamPassOutcome {
    PASS,
    FAIL,
    PENDING
}

data class SubjectEvaluation(
    val spec: MarksheetSubjectSpec,
    val continuous: Double? = null,
    val summative: Double? = null,
    val obtained: Double?,
    val highestInClass: Double,
    val effectivePassMarks: Long,
    val isPassed: Boolean,
    val letterGrade: String,
    val gpa: Double
) {
    val hasScore: Boolean get() = obtained != null
}

data class TermMarkBreakdown(
    val examId: String,
    val examTitle: String,
    val continuous: Double? = null,
    val summative: Double? = null,
    val total: Double? = null
) {
    val hasAnyScore: Boolean get() = total != null || continuous != null || summative != null
}

data class CombinedThreeTermSubjectRow(
    val subjectKey: String,
    val subjectName: String,
    val fullMarks: Long,
    val specByExamId: Map<String, MarksheetSubjectSpec>,
    val term1: TermMarkBreakdown,
    val term2: TermMarkBreakdown,
    val term3: TermMarkBreakdown,
    val threeTermAverage: Double?,
    val highestAcrossClass: Double = 0.0,
    val activeTermGrade: String = "—",
    val activeTermPassed: Boolean = false,
    val activeTermHasScore: Boolean = false
) {
    fun breakdownForExam(examId: String): TermMarkBreakdown = when (examId) {
        term1.examId -> term1
        term2.examId -> term2
        term3.examId -> term3
        else -> term1
    }
}

data class CombinedThreeTermTotals(
    val configuredFullMarks: Long,
    val t1ContTotal: Double,
    val t1SumTotal: Double,
    val t1Total: Double,
    val t1HasScore: Boolean,
    val t2ContTotal: Double,
    val t2SumTotal: Double,
    val t2Total: Double,
    val t2HasScore: Boolean,
    val t3ContTotal: Double,
    val t3SumTotal: Double,
    val t3Total: Double,
    val t3HasScore: Boolean,
    val threeTermAvgTotal: Double?,
    val subjectRows: List<CombinedThreeTermSubjectRow>
)

data class StudentExamResultSummary(
    val student: StudentRecord,
    val examId: String,
    val examTitle: String,
    val isFinalExam: Boolean,
    val subjectEvaluations: List<SubjectEvaluation>,
    val scoredCount: Int,
    val totalSubjectsCount: Int,
    val totalObtained: Double,
    val scoredFullMarks: Long,
    val configuredFullMarks: Long,
    val percentage: Int,
    val gpa: Double,
    val outcome: ExamPassOutcome,
    val meritPosition: Int? = null
)

data class ClassExamResultSheet(
    val classLevel: String,
    val exam: ExamTermConfig,
    val isFinalExam: Boolean,
    val applicableSpecs: List<MarksheetSubjectSpec>,
    val highestBySubjectId: Map<String, Double>,
    val studentSummaries: List<StudentExamResultSummary>,
    val appearedCount: Int,
    val passedCount: Int,
    val failedCount: Int,
    val passPercentage: Int
)

object ExamResultCalculator {

    const val FINAL_EXAM_ID = "exam_3"

    fun isFinalExam(exam: ExamTermConfig): Boolean =
        exam.examId == FINAL_EXAM_ID ||
            exam.title.contains("final", ignoreCase = true) ||
            exam.title.contains("annual", ignoreCase = true) ||
            exam.title.contains("বার্ষিক", ignoreCase = true)

    /**
     * Bangladesh school pass rule (kept internal for GPA/Grade/Result & Final Merit):
     * - 100 marks -> 33 pass (below 33 is Fail)
     * - 50 marks  -> 17 pass (below 17 is Fail)
     * - General   -> ceil(fullMarks * 33%)
     */
    fun bangladeshPassMarks(fullMarks: Long): Long {
        val safeFull = fullMarks.coerceIn(1L, 1000L)
        return when (safeFull) {
            100L -> 33L
            50L -> 17L
            else -> ((safeFull * 33L) + 99L) / 100L
        }.coerceIn(1L, safeFull)
    }

    fun effectivePassMarks(spec: MarksheetSubjectSpec): Long {
        if (spec.fullMarks == 50L && (spec.passMarks <= 0L || spec.passMarks == 33L)) {
            return 17L
        }
        return if (spec.passMarks > 0L) spec.passMarks.coerceAtMost(spec.fullMarks)
        else bangladeshPassMarks(spec.fullMarks)
    }

    fun evaluateGrade(
        obtained: Double?,
        fullMarks: Long,
        passMarks: Long
    ): Triple<Boolean, String, Double> {
        if (obtained == null) return Triple(false, "—", 0.0)
        if (obtained < passMarks.toDouble()) return Triple(false, "F", 0.0)
        val safeFull = fullMarks.coerceAtLeast(1L).toDouble()
        val pct = (obtained / safeFull) * 100.0
        val (grade, gpa) = when {
            pct >= 80.0 -> "A+" to 5.0
            pct >= 70.0 -> "A" to 4.0
            pct >= 60.0 -> "A-" to 3.5
            pct >= 50.0 -> "B" to 3.0
            pct >= 40.0 -> "C" to 2.0
            else -> "D" to 1.0
        }
        return Triple(true, grade, gpa)
    }

    fun formatMark(value: Double): String {
        val rounded = value.roundToInt().toDouble()
        return if (kotlin.math.abs(value - rounded) < 0.01) {
            rounded.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }

    fun formatGpa(gpa: Double): String = String.format(java.util.Locale.US, "%.2f", gpa)

    fun computeCombinedThreeTermTotals(
        student: StudentRecord,
        examConfigs: List<ExamTermConfig>,
        activeExamId: String = FINAL_EXAM_ID
    ): CombinedThreeTermTotals {
        val normalized = ExamDefaults.ensureThreeExams(examConfigs)
        val e1 = normalized[0]
        val e2 = normalized[1]
        val e3 = normalized[2]

        // Preserve subject order while aligning identical subject names across the 3 exams
        val groupedSpecs = linkedMapOf<String, MutableMap<String, MarksheetSubjectSpec>>()
        val displayNames = linkedMapOf<String, String>()
        val fullMarksByKey = linkedMapOf<String, Long>()

        normalized.forEach { exam ->
            exam.subjects.filter { it.appliesToClass(student.classLevel) }.forEach { spec ->
                val normKey = spec.subjectName.trim().lowercase().ifBlank { spec.subjectId }
                val mapForSub = groupedSpecs.getOrPut(normKey) { mutableMapOf() }
                mapForSub[exam.examId] = spec
                displayNames.putIfAbsent(normKey, spec.subjectName.ifBlank { spec.subjectId })
                fullMarksByKey.putIfAbsent(normKey, spec.fullMarks)
            }
        }

        fun extractTerm(exam: ExamTermConfig, spec: MarksheetSubjectSpec?): TermMarkBreakdown {
            if (spec == null) {
                return TermMarkBreakdown(examId = exam.examId, examTitle = exam.title)
            }
            val cont = student.examContinuousDouble(exam.examId, spec.subjectId)
            val sum = student.examSummativeDouble(exam.examId, spec.subjectId)
            val total = student.examMarkDouble(exam.examId, spec.subjectId)
            return TermMarkBreakdown(
                examId = exam.examId,
                examTitle = exam.title,
                continuous = cont,
                summative = sum,
                total = total
            )
        }

        val rows = groupedSpecs.keys.map { key ->
            val specs = groupedSpecs[key].orEmpty()
            val t1 = extractTerm(e1, specs[e1.examId])
            val t2 = extractTerm(e2, specs[e2.examId])
            val t3 = extractTerm(e3, specs[e3.examId])
            val termTotals = listOfNotNull(t1.total, t2.total, t3.total)
            val avg = if (termTotals.isNotEmpty()) {
                (termTotals.average() * 10.0).roundToInt() / 10.0
            } else null

            val evalSpec = specs[activeExamId] ?: specs.values.first()
            val activeTotal = when (activeExamId) {
                e1.examId -> t1.total
                e2.examId -> t2.total
                else -> t3.total
            } ?: avg
            val (passed, grade, _) = evaluateGrade(
                obtained = activeTotal,
                fullMarks = evalSpec.fullMarks,
                passMarks = effectivePassMarks(evalSpec)
            )

            CombinedThreeTermSubjectRow(
                subjectKey = key,
                subjectName = displayNames[key].orEmpty(),
                fullMarks = fullMarksByKey[key] ?: 100L,
                specByExamId = specs,
                term1 = t1,
                term2 = t2,
                term3 = t3,
                threeTermAverage = avg,
                activeTermGrade = grade,
                activeTermPassed = passed,
                activeTermHasScore = activeTotal != null
            )
        }

        val t1ContTotal = rows.sumOf { it.term1.continuous ?: 0.0 }
        val t1SumTotal = rows.sumOf { it.term1.summative ?: 0.0 }
        val t1Total = rows.sumOf { it.term1.total ?: 0.0 }
        val t1Has = rows.any { it.term1.total != null }

        val t2ContTotal = rows.sumOf { it.term2.continuous ?: 0.0 }
        val t2SumTotal = rows.sumOf { it.term2.summative ?: 0.0 }
        val t2Total = rows.sumOf { it.term2.total ?: 0.0 }
        val t2Has = rows.any { it.term2.total != null }

        val t3ContTotal = rows.sumOf { it.term3.continuous ?: 0.0 }
        val t3SumTotal = rows.sumOf { it.term3.summative ?: 0.0 }
        val t3Total = rows.sumOf { it.term3.total ?: 0.0 }
        val t3Has = rows.any { it.term3.total != null }

        val scoredTermTotals = buildList {
            if (t1Has) add(t1Total)
            if (t2Has) add(t2Total)
            if (t3Has) add(t3Total)
        }
        val avgOfTerms = if (scoredTermTotals.isNotEmpty()) {
            (scoredTermTotals.average() * 10.0).roundToInt() / 10.0
        } else null

        return CombinedThreeTermTotals(
            configuredFullMarks = rows.sumOf { it.fullMarks },
            t1ContTotal = t1ContTotal,
            t1SumTotal = t1SumTotal,
            t1Total = t1Total,
            t1HasScore = t1Has,
            t2ContTotal = t2ContTotal,
            t2SumTotal = t2SumTotal,
            t2Total = t2Total,
            t2HasScore = t2Has,
            t3ContTotal = t3ContTotal,
            t3SumTotal = t3SumTotal,
            t3Total = t3Total,
            t3HasScore = t3Has,
            threeTermAvgTotal = avgOfTerms,
            subjectRows = rows
        )
    }

    fun computeClassResultSheet(
        exam: ExamTermConfig,
        classLevel: String,
        allStudentsInYear: List<StudentRecord>,
        draftOverridesByStudentId: Map<String, StudentRecord> = emptyMap()
    ): ClassExamResultSheet {
        val classStudents = allStudentsInYear
            .filter { it.classLevel == classLevel }
            .map { draftOverridesByStudentId[it.id] ?: it }
            .sortedWith(compareBy<StudentRecord> { it.rollSortKey() }.thenBy { it.rollNumber }.thenBy { it.primaryName().lowercase() })

        val applicableSpecs = exam.subjects.filter { it.appliesToClass(classLevel) }
        val finalExamFlag = isFinalExam(exam)

        val highestBySubjectId = applicableSpecs.associate { spec ->
            val maxFromPeers = classStudents
                .mapNotNull { it.examMarkDouble(exam.examId, spec.subjectId) }
                .maxOrNull() ?: 0.0
            val effectiveHighest = if (spec.highestMarkOverride > 0L) {
                spec.highestMarkOverride.toDouble()
            } else {
                maxFromPeers
            }
            spec.subjectId to effectiveHighest
        }

        val rawSummaries = classStudents.map { student ->
            val evals = applicableSpecs.map { spec ->
                val cont = student.examContinuousDouble(exam.examId, spec.subjectId)
                val sum = student.examSummativeDouble(exam.examId, spec.subjectId)
                val obtained = student.examMarkDouble(exam.examId, spec.subjectId)
                val passReq = effectivePassMarks(spec)
                val highest = maxOf(highestBySubjectId[spec.subjectId] ?: 0.0, obtained ?: 0.0)
                val (passed, grade, gpa) = evaluateGrade(obtained, spec.fullMarks, passReq)
                SubjectEvaluation(
                    spec = spec,
                    continuous = cont,
                    summative = sum,
                    obtained = obtained,
                    highestInClass = highest,
                    effectivePassMarks = passReq,
                    isPassed = passed,
                    letterGrade = grade,
                    gpa = gpa
                )
            }
            val scored = evals.filter { it.hasScore }
            val totalObtained = scored.sumOf { it.obtained ?: 0.0 }
            val scoredFull = scored.sumOf { it.spec.fullMarks }
            val configuredFull = applicableSpecs.sumOf { it.fullMarks }
            val pct = if (scoredFull > 0L) {
                ((totalObtained * 100.0) / scoredFull.toDouble()).roundToInt().coerceIn(0, 100)
            } else 0
            val anyFailed = scored.any { !it.isPassed }
            val outcome = when {
                scored.isEmpty() -> ExamPassOutcome.PENDING
                anyFailed -> ExamPassOutcome.FAIL
                else -> ExamPassOutcome.PASS
            }
            val overallGpa = when {
                scored.isEmpty() || anyFailed -> 0.0
                else -> ((scored.map { it.gpa }.average()) * 100.0).roundToInt() / 100.0
            }

            StudentExamResultSummary(
                student = student,
                examId = exam.examId,
                examTitle = exam.title,
                isFinalExam = finalExamFlag,
                subjectEvaluations = evals,
                scoredCount = scored.size,
                totalSubjectsCount = applicableSpecs.size,
                totalObtained = totalObtained,
                scoredFullMarks = scoredFull,
                configuredFullMarks = configuredFull,
                percentage = pct,
                gpa = overallGpa,
                outcome = outcome,
                meritPosition = null
            )
        }

        // Merit ranking strictly ONLY for the Final / Annual Examination (not 1st or 2nd Term)
        val meritMap: Map<String, Int> = if (finalExamFlag) {
            val rankedPassers = rawSummaries
                .filter { it.outcome == ExamPassOutcome.PASS && it.scoredCount > 0 }
                .sortedWith(
                    compareByDescending<StudentExamResultSummary> { it.scoredCount }
                        .thenByDescending { it.totalObtained }
                        .thenByDescending { it.percentage }
                        .thenByDescending { it.student.attendancePercentage() }
                        .thenBy { it.student.rollSortKey() }
                        .thenBy { it.student.primaryName().lowercase() }
                )
            rankedPassers.mapIndexed { idx, s -> s.student.id to (idx + 1) }.toMap()
        } else {
            emptyMap()
        }

        val withMerit = rawSummaries.map { summary ->
            val rank = if (finalExamFlag) meritMap[summary.student.id] else null
            summary.copy(meritPosition = rank)
        }

        val appeared = withMerit.count { it.outcome != ExamPassOutcome.PENDING }
        val passed = withMerit.count { it.outcome == ExamPassOutcome.PASS }
        val failed = withMerit.count { it.outcome == ExamPassOutcome.FAIL }
        val passPct = if (appeared > 0) ((passed * 100.0) / appeared.toDouble()).roundToInt() else 0

        return ClassExamResultSheet(
            classLevel = classLevel,
            exam = exam,
            isFinalExam = finalExamFlag,
            applicableSpecs = applicableSpecs,
            highestBySubjectId = highestBySubjectId,
            studentSummaries = withMerit,
            appearedCount = appeared,
            passedCount = passed,
            failedCount = failed,
            passPercentage = passPct
        )
    }

    fun ordinal(rank: Int): String {
        val mod100 = rank % 100
        val suffix = if (mod100 in 11..13) "th" else when (rank % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$rank$suffix"
    }
}
