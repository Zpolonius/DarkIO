package app.darkio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType

/**
 * Health Connect rationale-aktivitet (F5, N9).
 * Viser en kort dansk forklaring om, at sundhedsdata kun læses og bliver på telefonen.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthRationaleScreen()
        }
    }
}

@Composable
fun HealthRationaleScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkIOColors.Bg)
            .padding(horizontal = DarkIOSpace.S5)
            .padding(top = DarkIOSpace.TopInset),
    ) {
        Column(Modifier.fillMaxWidth()) {
            BasicText(
                text = stringResource(R.string.health_rationale_title),
                style = DarkIOType.Title.copy(color = DarkIOColors.Ink),
            )
            Spacer(Modifier.height(DarkIOSpace.S3))
            BasicText(
                text = stringResource(R.string.health_rationale_text),
                style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft),
            )
        }
    }
}
