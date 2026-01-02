package pt.isec.amov.tp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import pt.isec.amov.tp.R
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: DashboardViewModel
) {
    // --- ViewModel State ---
    val allProtecteds = viewModel.myProtecteds

    // --- State Variables ---
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    val activeUser = allProtecteds.find { it.uid == selectedUserId }

    // --- Camera State ---
    val defaultLocation = LatLng(40.2033, -8.4103)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }
    var hasCenteredMap by remember { mutableStateOf(false) }
    LaunchedEffect(activeUser?.uid) {
        activeUser?.uid?.let { viewModel.startTrackingUser(it) }
    }

    // --- MODIFICACIÓN: Mover la cámara automáticamente cuando cambie la ubicación ---
    LaunchedEffect(activeUser?.location) {
        activeUser?.location?.let { geo ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(geo.latitude, geo.longitude), 15f)
            )
        }
    }

    LaunchedEffect(allProtecteds) {
        if (!hasCenteredMap && allProtecteds.isNotEmpty()) {
            val firstWithLoc = allProtecteds.firstOrNull { it.location != null }
            if (firstWithLoc?.location != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(firstWithLoc.location!!.latitude, firstWithLoc.location!!.longitude),
                    12f
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- MAP ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true),
            onMapClick = {
                selectedUserId = null
            }
        ) {
            // --- LOOP -> draw pins ---
            allProtecteds.forEach { user ->
                val userLoc = user.location
                if (userLoc != null) {
                    val isSelected = user.uid == selectedUserId

                    val iconColor = if (isSelected)
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    else
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)

                    val zIndex = if (isSelected) 1.0f else 0.0f

                    Marker(
                        state = MarkerState(position = LatLng(userLoc.latitude, userLoc.longitude)),
                        title = user.name,
                        icon = iconColor,
                        onClick = {
                            selectedUserId = user.uid
                            false
                        }
                    )
                }
            }
        }

        // --- Floating Info Card ---
        if (activeUser != null && activeUser.location != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Nome
                    Text(
                        text = activeUser.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Status
                    Text(
                        text = stringResource(R.string.lbl_monitoring_active),
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Data
                    val date = activeUser.lastUpdate?.toDate()
                    val dateStr = if (date != null) {
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(date)
                    } else "..."

                    Text(
                        text = stringResource(R.string.lbl_last_update, dateStr),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Coords
                    Text(
                        text = "Lat: ${activeUser.location!!.latitude} | Lng: ${activeUser.location!!.longitude}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                Text(
                    text = stringResource(R.string.title_map_all),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}