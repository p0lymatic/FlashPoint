package com.p0lymatic.flashpoint.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.p0lymatic.flashpoint.R
import com.p0lymatic.flashpoint.ui.components.NonagonCookieShape

private data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String,
    val avatarResId: Int,
    val isAi: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    val contributors = listOf(
        Contributor(
            name = "p0lymatic",
            role = "Project Vision & Architecture",
            githubUrl = "https://github.com/p0lymatic",
            avatarResId = R.drawable.p0lymatic
        ),
        Contributor(
            name = "alephtheagent",
            role = "Autonomous Orchestrator & DevOps",
            githubUrl = "https://github.com/alephtheagent",
            avatarResId = R.drawable.alephtheagent
        ),
        Contributor(
            name = "longpanda (Ventoy)",
            role = "Ventoy Multi-boot Engine Creator",
            githubUrl = "https://github.com/ventoy",
            avatarResId = R.drawable.ventoy
        ),
        Contributor(
            name = "Pete Batard (Rufus)",
            role = "Rufus Author & Inspiration",
            githubUrl = "https://github.com/pbatard",
            avatarResId = R.drawable.pbatard
        ),
        Contributor(
            name = "Google Gemini",
            role = "Implementation & Refactoring",
            githubUrl = "https://github.com/google-gemini",
            avatarResId = R.drawable.gemini,
            isAi = true
        ),
        Contributor(
            name = "Anthropic Claude",
            role = "Foundation & Core Architecture",
            githubUrl = "https://github.com/anthropics",
            avatarResId = R.drawable.claude,
            isAi = true
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "FlashPoint",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.4 (GPL-3.0)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Built out of pure engineering spite for apps that charge $0.99 or force users to watch 60-second video ads just to write an ISO to a USB drive over OTG.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Hall of Fame Header
            Text(
                text = "Hall of Fame",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // Contributors List
            val cookieShape = NonagonCookieShape(points = 9, innerRadiusRatio = 0.85f)
            val aiLogoShape = RoundedCornerShape(16.dp)

            contributors.forEach { contributor ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (contributor.isAi) {
                            // Clean AI Logo with tight outline (no cookie)
                            Surface(
                                shape = aiLogoShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(56.dp)
                                    .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), aiLogoShape)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = contributor.avatarResId),
                                        contentDescription = contributor.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            // 9-sided Cookie Avatar for human contributors
                            Surface(
                                shape = cookieShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(56.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, cookieShape)
                            ) {
                                Image(
                                    painter = painterResource(id = contributor.avatarResId),
                                    contentDescription = contributor.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(cookieShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contributor.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri(contributor.githubUrl)
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = contributor.role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
