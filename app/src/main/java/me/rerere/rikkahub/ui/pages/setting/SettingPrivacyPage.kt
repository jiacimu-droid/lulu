package me.rerere.rikkahub.ui.pages.setting

import android.app.ActivityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

@Composable
fun SettingPrivacyPage() {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("清除全部应用数据？") },
            text = {
                Text("角色、聊天、记忆、文件、API Key 和设置都会从本机永久删除，且无法恢复。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("永久清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("隐私与数据") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("本地优先") },
                ) {
                    item(
                        headlineContent = { Text("角色、聊天、记忆和设置默认保存在本机") },
                        supportingContent = { Text("公开版不包含共享 API Key，也不启用项目方分析或崩溃上报。") },
                    )
                    item(
                        headlineContent = { Text("第三方服务由用户自行选择") },
                        supportingContent = { Text("模型、语音、搜索和云同步仅在配置并使用时向对应服务发送完成请求所需的数据。") },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("权限") },
                ) {
                    item(
                        headlineContent = { Text("按功能申请") },
                        supportingContent = { Text("通知、麦克风、相机、位置、短信、日历和设备感知权限只用于用户主动启用的对应功能，可随时在系统设置中撤销。") },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("删除数据") },
                ) {
                    item(
                        onClick = { showDeleteDialog = true },
                        headlineContent = {
                            Text(
                                text = "清除全部应用数据",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = { Text("永久删除本机的角色、聊天、记忆、文件、密钥和设置") },
                    )
                }
            }
        }
    }
}
