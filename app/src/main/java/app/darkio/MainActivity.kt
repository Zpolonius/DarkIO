package app.darkio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.darkio.data.AppRepository
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType

class MainActivity : ComponentActivity() {
    private lateinit var apps: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        apps = AppRepository(this, lifecycleScope)
        setContent {
            val list = apps.apps.collectAsState().value
            LazyColumn(
                Modifier.fillMaxSize().background(DarkIOColors.Bg),
                contentPadding = PaddingValues(horizontal = DarkIOSpace.S5, vertical = DarkIOSpace.TopInset),
            ) {
                items(list, key = { it.id }) { app ->
                    Box(Modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchMin), contentAlignment = Alignment.CenterStart) {
                        BasicText(app.label, style = DarkIOType.Data.copy(color = DarkIOColors.Ink))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        apps.start()
    }

    override fun onStop() {
        apps.stop()
        super.onStop()
    }
}
