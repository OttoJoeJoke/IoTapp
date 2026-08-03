package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scanner.ScanViewModel

@Composable
fun MainContainerScreen(
    viewModel: ScanViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val serverUrl by viewModel.serverUrl.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = "3D Tarayıcı"
                        )
                    },
                    label = {
                        Text(
                            text = "3D Tarayıcı",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.testTag("tab_3d_scanner")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Canlı Panel"
                        )
                    },
                    label = {
                        Text(
                            text = "Canlı Panel",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.testTag("tab_live_panel")
                )
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Tab 0: 3D Scanner (Camera + Telemetry + Radar + Multi-part upload)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (selectedTab != 0) Modifier.padding(bottom = 0.dp) else Modifier
                    )
            ) {
                // Keep ScannerScreen composed in memory so camera, sensors, and frame history stay alive
                AnimatedVisibility(
                    visible = selectedTab == 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ScannerScreen(viewModel = viewModel)
                }
            }

            // Tab 1: Live Dashboard Web View
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = selectedTab == 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LivePanelWebView(dashboardUrl = serverUrl)
                }
            }
        }
    }
}
