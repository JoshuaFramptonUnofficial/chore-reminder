package com.chorereminder.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Day-granularity presentation policy; never used by reminder scheduling. */
@Serializable
data class VisibilitySpec(val mode: String = "IMMEDIATE", val amount: Int = 1, val unit: String = "DAYS") {
    fun validate() {
        require(mode in listOf("IMMEDIATE", "AFTER_COMPLETION", "BEFORE_DUE"))
        require(amount in 1..999 && unit in listOf("DAYS", "WEEKS", "MONTHS"))
    }

    private fun LocalDate.offset(sign: Long): LocalDate = when (unit) {
        "WEEKS" -> plusWeeks(sign * amount)
        "MONTHS" -> plusMonths(sign * amount)
        else -> plusDays(sign * amount)
    }

    fun isVisible(today: LocalDate, due: LocalDate, completed: LocalDate?): Boolean {
        if (due <= today) return true
        val appears = when (mode) {
            "AFTER_COMPLETION" -> completed?.offset(1) ?: return true
            "BEFORE_DUE" -> due.offset(-1)
            else -> return true
        }
        return today >= appears
    }
}

@Entity(tableName = "visibility_settings")
data class VisibilitySettingsEntity(
    @PrimaryKey val id: Int = 1,
    @Embedded val rule: VisibilitySpec = VisibilitySpec(),
)

data class BackupSnapshot(
    val first: List<CategoryEntity>, val second: List<TaskEntity>, val third: List<CompletionEntity>,
    val defaultVisibility: VisibilitySpec = VisibilitySpec(),
)
