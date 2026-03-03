package com.tacz.legacy.common.domain.gunpack

public enum class GunPackIssueSeverity {
    INFO,
    WARNING,
    ERROR
}

public data class GunPackIssue(
    val severity: GunPackIssueSeverity,
    val code: String,
    val field: String,
    val message: String
)

public class GunPackCompatibilityReport {

    private val issues: MutableList<GunPackIssue> = mutableListOf()

    public fun addIssue(issue: GunPackIssue) {
        issues += issue
    }

    public fun addInfo(code: String, field: String, message: String) {
        addIssue(GunPackIssue(GunPackIssueSeverity.INFO, code, field, message))
    }

    public fun addWarning(code: String, field: String, message: String) {
        addIssue(GunPackIssue(GunPackIssueSeverity.WARNING, code, field, message))
    }

    public fun addError(code: String, field: String, message: String) {
        addIssue(GunPackIssue(GunPackIssueSeverity.ERROR, code, field, message))
    }

    public fun allIssues(): List<GunPackIssue> = issues.toList()

    public fun hasErrors(): Boolean = issues.any { it.severity == GunPackIssueSeverity.ERROR }

    public fun hasWarnings(): Boolean = issues.any { it.severity == GunPackIssueSeverity.WARNING }

}