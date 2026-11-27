package app.darkio

import android.app.Activity
import android.content.Intent
import android.os.Bundle

const val EXTRA_OPEN_WORK_HOURS = "app.darkio.EXTRA_OPEN_WORK_HOURS"

/**
 * Konfigurationsaktivitet for Androids "Fyraften" AutomaticZenRule (F23, N9).
 * Åbner DarkIO's arbejdstidsindstillinger via [MainActivity] og afslutter straks.
 */
class WorkHoursConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_WORK_HOURS, true)
        }
        startActivity(intent)
        finish()
    }
}
