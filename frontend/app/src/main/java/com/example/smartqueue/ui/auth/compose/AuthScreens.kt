package com.example.smartqueue.ui.auth.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartqueue.ui.components.AnimatedPrimaryButton
import com.example.smartqueue.ui.theme.SmartQTheme
import kotlinx.coroutines.delay

/**
 * Login Screen — Compose with form animations
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (email: String, password: String) -> Unit = { _, _ -> },
    onNavigateToRegister: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
) {
    SmartQTheme {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var showForm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(300)
            showForm = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Header
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = fadeIn(),
                        label = "header",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.large,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Q",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }

                            Text(
                                "SmartQ",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            Text(
                                "Intelligent Healthcare Queuing",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Email Field
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                        label = "email_field",
                    ) {
                        AuthInputField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email",
                            placeholder = "doctor@hospital.com",
                            keyboardType = KeyboardType.Email,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )
                    }
                }

                // Password Field
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                        label = "password_field",
                    ) {
                        AuthInputField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "Enter your password",
                            isPassword = true,
                            isPasswordVisible = isPasswordVisible,
                            onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        )
                    }
                }

                // Login Button
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                        label = "login_button",
                    ) {
                        AnimatedPrimaryButton(
                            text = "Login",
                            onClick = {
                                isLoading = true
                                onLoginSuccess(email, password)
                            },
                            isLoading = isLoading,
                            enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        )
                    }
                }

                // Forgot Password & Register Links
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                        label = "links",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Forgot Password?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .padding(8.dp)
                                    .androidx.compose.foundation.clickable(
                                        enabled = true,
                                        onClick = onForgotPassword,
                                    ),
                            )

                            Row(
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "Don't have an account? ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Register",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .padding(4.dp)
                                        .androidx.compose.foundation.clickable(
                                            enabled = true,
                                            onClick = onNavigateToRegister,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Register Screen — Compose with validation animations
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: (name: String, email: String, password: String, phone: String, age: Int) -> Unit = { _, _, _, _, _ -> },
    onNavigateToLogin: () -> Unit = {},
) {
    SmartQTheme {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var fullName by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var isConfirmPasswordVisible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var showForm by remember { mutableStateOf(false) }
        var phone by remember { mutableStateOf("") }
        var ageText by remember { mutableStateOf("") }

        // Validation states
        val isPasswordMatch = password == confirmPassword && password.isNotEmpty()
        val isPasswordStrong = password.length >= 8
        val isEmailValid = email.contains("@")
        val isPhoneValid = phone.length >= 10
        val age = ageText.toIntOrNull() ?: 0
        val isAgeValid = age in 1..120

        LaunchedEffect(Unit) {
            delay(300)
            showForm = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                // Header
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = fadeIn(),
                        label = "header",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Create Account",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Join SmartQ Healthcare",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Full Name Field
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AuthInputField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = "Full Name",
                            placeholder = "Dr. John Smith",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                // Email Field
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AuthInputField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email",
                            placeholder = "doctor@hospital.com",
                            keyboardType = KeyboardType.Email,
                            trailingIcon = if (email.isNotEmpty() && isEmailValid) {
                                @Composable {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Email valid",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                // Password Field
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AuthInputField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "At least 8 characters",
                            isPassword = true,
                            isPasswordVisible = isPasswordVisible,
                            onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AuthInputField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(12) },
                            label = "Phone",
                            placeholder = "10-digit number",
                            keyboardType = KeyboardType.Phone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AuthInputField(
                            value = ageText,
                            onValueChange = { ageText = it.filter { c -> c.isDigit() }.take(3) },
                            label = "Age",
                            placeholder = "Enter age",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                // Password Strength Indicator
                item {
                    AnimatedVisibility(
                        visible = showForm && password.isNotEmpty(),
                        enter = fadeIn(),
                    ) {
                        PasswordStrengthIndicator(
                            isStrong = isPasswordStrong,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                // Confirm Password Field
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AuthInputField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirm Password",
                            placeholder = "Re-enter password",
                            isPassword = true,
                            isPasswordVisible = isConfirmPasswordVisible,
                            onPasswordVisibilityToggle = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                            trailingIcon = if (confirmPassword.isNotEmpty()) {
                                @Composable {
                                    val (icon, color) = if (isPasswordMatch) {
                                        Icons.Filled.Check to Color(0xFF4CAF50)
                                    } else {
                                        Icons.Filled.Check to Color(0xFFEB4141)
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = if (isPasswordMatch) "Passwords match" else "Passwords don't match",
                                        tint = color,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        )
                    }
                }

                // Register Button
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        AnimatedPrimaryButton(
                            text = "Create Account",
                            onClick = {
                                isLoading = true
                                onRegisterSuccess(fullName, email, password, phone, age)
                            },
                            isLoading = isLoading,
                            enabled = isPasswordMatch &&
                                isPasswordStrong &&
                                isEmailValid &&
                                isPhoneValid &&
                                isAgeValid &&
                                fullName.isNotEmpty() &&
                                !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        )
                    }
                }

                // Login Link
                item {
                    AnimatedVisibility(
                        visible = showForm,
                        enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(),
                    ) {
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Already have an account? ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Login",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .padding(4.dp)
                                    .androidx.compose.foundation.clickable(
                                        enabled = true,
                                        onClick = onNavigateToLogin,
                                    ),
                            )
                        }
                    }
                }

                item {
                    Box(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Animated Form Input Field
 */
@Composable
fun AuthInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityToggle: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val isFocused = remember { mutableStateOf(false) }

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused.value) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "border_color"
    )

    Card(
        modifier = modifier
            .height(56.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    visualTransformation = if (isPassword && !isPasswordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            }

            if (isPassword && onPasswordVisibilityToggle != null) {
                IconButton(
                    onClick = onPasswordVisibilityToggle,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Filled.Visibility
                        } else {
                            Icons.Filled.VisibilityOff
                        },
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

/**
 * Password Strength Indicator
 */
@Composable
fun PasswordStrengthIndicator(
    isStrong: Boolean,
    modifier: Modifier = Modifier,
) {
    val strengthColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isStrong) Color(0xFF4CAF50) else Color(0xFFEB4141),
        label = "strength_color"
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    strengthColor,
                    shape = MaterialTheme.shapes.extraSmall,
                ),
        )
        Text(
            if (isStrong) "Strong password" else "Password too weak",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = strengthColor,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
