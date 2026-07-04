package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val guarantorId: Long? = null,
    val amount: Double,
    val interestRate: Double, // Flat interest rate percentage (e.g. 10.0 for 10%)
    val tenureMonths: Int,
    val outstandingBalance: Double, // Principal remaining (not including future interest)
    val status: String = "ACTIVE", // "ACTIVE" or "PAID_OFF"
    val loanDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Computes the maturity date (loan end date) as epoch millis.
 * = loanDate + tenureMonths calendar months
 */
fun Loan.maturityDateMillis(): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = loanDate
        add(Calendar.MONTH, tenureMonths)
    }
    return cal.timeInMillis
}

/**
 * Returns how many full months have elapsed since disbursement, capped at tenureMonths.
 */
fun Loan.monthsElapsed(nowMillis: Long = System.currentTimeMillis()): Int {
    val start = Calendar.getInstance().apply { timeInMillis = loanDate }
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val years = now.get(Calendar.YEAR) - start.get(Calendar.YEAR)
    val months = now.get(Calendar.MONTH) - start.get(Calendar.MONTH)
    return minOf((years * 12 + months).coerceAtLeast(0), tenureMonths)
}

/**
 * Computes the displayed outstanding balance as of today:
 *   principalRemaining + (interestAccruedToDate - totalInterestPaid)
 *
 * Since interest paid is tracked separately via repayments and this function
 * only has access to the Loan entity, it returns the simple accrued-interest view:
 *   outstandingBalance (principal remaining) + accrued interest not yet paid
 *
 * For flat rate loans:
 *   monthlyInterest = principal × interestRate / 100 / 12
 *   accruedInterest = monthlyInterest × monthsElapsed
 *
 * Pass totalInterestPaid from repayment history to get the net outstanding.
 */
fun Loan.currentOutstandingBalance(
    nowMillis: Long = System.currentTimeMillis(),
    totalInterestPaid: Double = 0.0
): Double {
    if (status == "PAID_OFF") return 0.0
    val monthlyInterest = amount * (interestRate / 100.0) / 12.0
    val accruedInterest = monthlyInterest * monthsElapsed(nowMillis)
    val interestDue = (accruedInterest - totalInterestPaid).coerceAtLeast(0.0)
    return outstandingBalance + interestDue
}
