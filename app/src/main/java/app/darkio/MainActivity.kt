package app.darkio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DarkIOColors.Bg)
                    .padding(horizontal = DarkIOSpace.S5, vertical = DarkIOSpace.TopInset),
            ) {
                BasicText(stringResource(R.string.hello), style = DarkIOType.Data.copy(color = DarkIOColors.Ink))
            }
        }
    }
}
