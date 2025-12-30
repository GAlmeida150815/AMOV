package pt.isec.amov.tp.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

// --- Add Protected Dialog ---
@Composable
fun AddProtectedDialog(code: String?, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val msgCopied = stringResource(R.string.msg_code_copied)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_add_protected)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (code == null) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.msg_share_code))
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(code))
                                Toast.makeText(context, msgCopied, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = code,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(stringResource(R.string.lbl_click_to_copy), fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}


// --- Add Monitor Dialog ---
@Composable
fun AddMonitorDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val isLoading = viewModel.isLoading
    val errorRes = viewModel.associationError

    AlertDialog(
        onDismissRequest = {
            viewModel.clearAssociationState()
            onDismiss()
        },
        title = { Text(stringResource(R.string.title_add_monitor)) },
        text = {
            Column {
                Text(stringResource(R.string.hint_enter_code))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorRes != null,
                    supportingText = {
                        if (errorRes != null) {
                            Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.associateMonitor(code, onSuccess) },
                enabled = !isLoading && code.length == 6
            ) {
                Text(stringResource(R.string.btn_associate))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.clearAssociationState()
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}