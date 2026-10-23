/**
 * Flow — open-source photo editor.
 * Home lists projects; the editor applies VSCO cubes. Nothing leaves the device.
 */

package com.zyon.flow

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zyon.flow.edit.Project
import com.zyon.flow.ui.BatchScreen
import com.zyon.flow.ui.EditorScreen
import com.zyon.flow.ui.HomeScreen
import com.zyon.flow.ui.theme.FlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FlowApp() }
    }
}

@Composable
private fun FlowApp() {
    FlowTheme {
        var editingUri by remember { mutableStateOf<Uri?>(null) }
        var editingProject by remember { mutableStateOf<Project?>(null) }
        var inEditor by remember { mutableStateOf(false) }
        var inBatch by remember { mutableStateOf(false) }
        var homeRefresh by remember { mutableIntStateOf(0) }

        BackHandler(enabled = inEditor) {
            inEditor = false
            editingUri = null
            editingProject = null
            homeRefresh++
        }

        BackHandler(enabled = inBatch) {
            inBatch = false
            homeRefresh++
        }

        if (inBatch) {
            BatchScreen(onBack = {
                inBatch = false
                homeRefresh++
            })
        } else if (inEditor) {
            EditorScreen(
                photoUri = editingUri,
                initial = editingProject,
                onBack = {
                    inEditor = false
                    editingUri = null
                    editingProject = null
                    homeRefresh++
                },
                onSaved = { homeRefresh++ },
            )
        } else {
            HomeScreen(
                onOpenPhoto = { uri, project ->
                    editingUri = uri
                    editingProject = project
                    inEditor = true
                },
                onOpenBatch = { inBatch = true },
                refreshKey = homeRefresh,
            )
        }
    }
}
