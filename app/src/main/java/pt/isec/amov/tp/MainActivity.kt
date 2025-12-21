package pt.isec.amov.tp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import pt.isec.amov.tp.ui.navigation.SetupNavGraph
import pt.isec.amov.tp.ui.theme.TP_theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TP_theme {
                val navController = rememberNavController()
                SetupNavGraph(navController = navController)
            }
        }
    }
}