package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.example.R
import com.example.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isLoading = viewModel.isLoading.value

    // Clean, pure white canvas
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Center Branding Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Polished modern logo container with decorative glow & rings
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    // Outer subtle pulse ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFF4F46E5).copy(alpha = 0.08f),
                            radius = size.minDimension / 2f
                        )
                        drawCircle(
                            color = Color(0xFF6366F1).copy(alpha = 0.2f),
                            radius = (size.minDimension / 2f) - 10.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Main circular logo badge
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                spotColor = Color(0xFF4F46E5).copy(alpha = 0.35f),
                                ambientColor = Color(0xFF4F46E5).copy(alpha = 0.2f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF4F46E5), // Indigo 600
                                        Color(0xFF3B82F6), // Blue 500
                                        Color(0xFF06B6D4)  // Cyan 500
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "App Logo",
                            modifier = Modifier.size(54.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Welcome Greeting Headline
                Text(
                    text = stringResource(R.string.welcome_greeting),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), // Slate 800
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4F46E5), // Indigo
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF64748B), // Slate 500
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Bottom Action Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = Color(0xFF4F46E5),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.connecting),
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                } else {
                    // Primary Sign-In Button
                    Button(
                        onClick = {
                            viewModel.signInWithGoogle(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(18.dp),
                                spotColor = Color(0xFF4F46E5).copy(alpha = 0.3f)
                            )
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.sign_in_now),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Demo / Fast Guest Access Button
                    OutlinedButton(
                        onClick = {
                            viewModel.signInDemoUser(
                                context,
                                "Siti Rahma",
                                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("demo_sign_in_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4F46E5)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.fast_demo_login),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val annotatedString = buildAnnotatedString {
                    append(stringResource(R.string.login_agreement_prefix))
                    
                    pushStringAnnotation(tag = "policy", annotation = "https://sites.google.com/view/geofriendst/halaman-muka")
                    withStyle(style = SpanStyle(color = Color(0xFF4F46E5), fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) {
                        append(stringResource(R.string.privacy_policy))
                    }
                    pop()
                    
                    append(stringResource(R.string.and_word))
                    
                    pushStringAnnotation(tag = "terms", annotation = "https://sites.google.com/view/geofriendst/halaman-muka")
                    withStyle(style = SpanStyle(color = Color(0xFF4F46E5), fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) {
                        append(stringResource(R.string.terms_and_conditions))
                    }
                    pop()
                    append(stringResource(R.string.login_agreement_suffix))
                }

                ClickableText(
                    text = annotatedString,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    ),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "policy", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                        annotatedString.getStringAnnotations(tag = "terms", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
