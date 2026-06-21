package com.music.vivi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.vivi.LocalPlayerAwareWindowInsets
import com.music.vivi.R
import com.music.vivi.blend.BlendRecord
import com.music.vivi.viewmodels.BlendResult
import com.music.vivi.viewmodels.BlendViewModel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendScreen(
    navController: NavController,
    viewModel: BlendViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val blendResult by viewModel.blendResult.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isJoining by viewModel.isJoining.collectAsState()
    val joinedBlend by viewModel.joinedBlend.collectAsState()
    val savedBlendCode by viewModel.savedBlendCode.collectAsState()
    val isGeneratingCode by viewModel.isGeneratingCode.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    var myDisplayName by remember { mutableStateOf("") }
    var myCode by remember { mutableStateOf("") }
    var friendCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blend", fontWeight = FontWeight.Bold)
                        Text(
                            "Compare music taste with friends",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GetMyCodeCard(
                    displayName = myDisplayName,
                    onDisplayNameChange = { myDisplayName = it },
                    isGenerating = isGeneratingCode || isSaving,
                    savedCode = savedBlendCode,
                    onGetCode = { viewModel.saveToSupabase(myDisplayName) },
                    colorScheme = colorScheme
                )
            }

            if (!savedBlendCode.isNullOrBlank()) {
                item {
                    CompareCard(
                        myCode = myCode,
                        friendCode = friendCode,
                        onMyCodeChange = { myCode = it.uppercase().take(6) },
                        onFriendCodeChange = { friendCode = it.uppercase().take(6) },
                        isLoading = isLoading,
                        onCompare = { viewModel.createBlendFromCodes(myCode, friendCode) },
                        colorScheme = colorScheme
                    )
                }
            }

            if (error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = error!!,
                            modifier = Modifier.padding(16.dp),
                            color = colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (blendResult != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        BlendResultCard(
                            result = blendResult!!,
                            colorScheme = colorScheme
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                JoinBlendCard(
                    code = joinCode,
                    onCodeChange = { joinCode = it.uppercase().take(6) },
                    isJoining = isJoining,
                    onJoin = { viewModel.joinBlend(joinCode) },
                    joinedBlend = joinedBlend,
                    colorScheme = colorScheme
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GetMyCodeCard(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    isGenerating: Boolean,
    savedCode: String?,
    onGetCode: () -> Unit,
    colorScheme: ColorScheme
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "GET YOUR CODE",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Generate a code from your listening history and share it with a friend",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Your display name") },
                leadingIcon = {
                    Icon(painterResource(R.drawable.person), contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onGetCode() }),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = onGetCode,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGenerating && displayName.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating…")
                } else {
                    Icon(
                        painterResource(R.drawable.ios_share),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Get My Code", fontWeight = FontWeight.SemiBold)
                }
            }

            if (!savedCode.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "YOUR BLEND CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                text = savedCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onPrimaryContainer,
                                letterSpacing = 6.sp
                            )
                            Text(
                                "Share this with a friend",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(savedCode)) }
                        ) {
                            Icon(
                                painterResource(R.drawable.content_copy),
                                contentDescription = "Copy",
                                tint = colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareCard(
    myCode: String,
    friendCode: String,
    onMyCodeChange: (String) -> Unit,
    onFriendCodeChange: (String) -> Unit,
    isLoading: Boolean,
    onCompare: () -> Unit,
    colorScheme: ColorScheme
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "COMPARE TASTES",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp
            )
            OutlinedTextField(
                value = myCode,
                onValueChange = onMyCodeChange,
                label = { Text("Your code") },
                leadingIcon = {
                    Icon(painterResource(R.drawable.person), contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = friendCode,
                onValueChange = onFriendCodeChange,
                label = { Text("Friend's code") },
                leadingIcon = {
                    Icon(painterResource(R.drawable.group_outlined), contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCompare() }),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = onCompare,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && myCode.length >= 4 && friendCode.length >= 4,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Comparing…")
                } else {
                    Text("See Compatibility", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BlendResultCard(
    result: BlendResult,
    colorScheme: ColorScheme
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${result.user1}  ×  ${result.user2}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (result.compatibilityScore > 0) {
                CompatibilityRing(score = result.compatibilityScore, accentColor = colorScheme.primary)
            }

            if (result.sharedArtists.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SHARED ARTISTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(result.sharedArtists) { _, artist ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(artist, style = MaterialTheme.typography.bodySmall) },
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (result.user1Artists.isNotEmpty() || result.user2Artists.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (result.user1Artists.isNotEmpty()) {
                        TopListColumn(
                            title = result.user1.uppercase(),
                            items = result.user1Artists.take(5).map { it.name },
                            modifier = Modifier.weight(1f),
                            colorScheme = colorScheme
                        )
                    }
                    if (result.user1Artists.isNotEmpty() && result.user2Artists.isNotEmpty()) {
                        VerticalDivider(modifier = Modifier.height(160.dp))
                    }
                    if (result.user2Artists.isNotEmpty()) {
                        TopListColumn(
                            title = result.user2.uppercase(),
                            items = result.user2Artists.take(5).map { it.name },
                            modifier = Modifier.weight(1f),
                            colorScheme = colorScheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatibilityRing(score: Int, accentColor: Color) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "score"
    )
    val sweepAngle = (animatedScore / 100f) * 270f
    val ringColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFFFF9800)
        score >= 40 -> accentColor
        else -> Color(0xFFE57373)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val arcOffset = Offset(inset, inset)
            drawArc(
                color = ringColor.copy(alpha = 0.12f), startAngle = 135f, sweepAngle = 270f,
                useCenter = false, topLeft = arcOffset, size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor, startAngle = 135f, sweepAngle = sweepAngle,
                useCenter = false, topLeft = arcOffset, size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${score}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = ringColor
            )
            Text(
                text = when {
                    score >= 80 -> "Twin flames 🔥"
                    score >= 60 -> "Great match ✨"
                    score >= 40 -> "Decent vibes 🎵"
                    else -> "Different worlds 🌍"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TopListColumn(
    title: String,
    items: List<String>,
    modifier: Modifier,
    colorScheme: ColorScheme
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            letterSpacing = 0.5.sp
        )
        items.forEachIndexed { i, name ->
            Text(
                text = "${i + 1}. $name",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = if (i == 0) 1f else 0.7f),
                fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun JoinBlendCard(
    code: String,
    onCodeChange: (String) -> Unit,
    isJoining: Boolean,
    onJoin: () -> Unit,
    joinedBlend: BlendRecord?,
    colorScheme: ColorScheme
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "VIEW SOMEONE'S TASTE",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Enter a friend's blend code to see their top artists",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("Blend code") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onJoin() }),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = onJoin,
                    enabled = code.length >= 4 && !isJoining,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Text("View")
                    }
                }
            }

            if (joinedBlend != null) {
                HorizontalDivider()
                Text(
                    text = joinedBlend.user1_username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val artists = joinedBlend.user1_top_artists
                    .split(",")
                    .filter { it.isNotBlank() }
                    .take(10)
                if (artists.isNotEmpty()) {
                    Text(
                        text = "TOP ARTISTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(artists) { _, artist ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(artist, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }
        }
    }
}
