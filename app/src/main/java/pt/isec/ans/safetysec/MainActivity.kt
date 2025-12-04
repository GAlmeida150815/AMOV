package pt.isec.ans.safetysec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pt.isec.ans.safetysec.ui.theme.SafetYSecTheme
import pt.isec.ans.safetysec.ui.screens.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Hace que la app se vea bien en pantallas modernas
        setContent {
            // Mantiene el tema visual de tu proyecto
            SafetYSecTheme {
                // Scaffold gestiona la barra de notificaciones y navegación
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // Surface es un contenedor básico
                    // Le aplicamos el "padding" (margen) para que el contenido no quede tapado por la hora o la batería
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // ¡AQUÍ LLAMAMOS A TU PANTALLA!
                        LoginScreen()
                    }
                }
            }
        }
    }
}