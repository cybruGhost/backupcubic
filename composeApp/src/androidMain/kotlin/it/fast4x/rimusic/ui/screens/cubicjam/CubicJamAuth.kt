package it.fast4x.rimusic.ui.screens.cubicjam

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun CubicJamAuth(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header - using music.xml drawable
        Icon(
            painter = painterResource(app.kreate.android.R.drawable.music),
            contentDescription = "Cubic Jam",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (isLoginMode) "Login to Cubic Jam" else "Create Cubic Jam Account",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isLoginMode) 
                "Connect with friends and share your music taste" 
            else 
                "Join the Cubic Jam community",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Email field - using email icon from your list
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            placeholder = { Text("your.email@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = { 
                Icon(
                    painter = painterResource(app.kreate.android.R.drawable.eye),
                    contentDescription = "Email"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Password field - using lock icon
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text("Enter your password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { 
                Icon(
                    painter = painterResource(app.kreate.android.R.drawable.server),
                    contentDescription = "Password"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Username field (only for signup)
        if (!isLoginMode) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("Choose a username") },
                singleLine = true,
                leadingIcon = { 
                    Icon(
                        painter = painterResource(app.kreate.android.R.drawable.person),
                        contentDescription = "Username"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Error message
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Success message
        successMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Submit button - using checkmark for login, add for signup
        Button(
            onClick = {
                if (isLoading) return@Button
                
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please fill in all fields"
                    return@Button
                }
                
                if (!isLoginMode && username.isBlank()) {
                    errorMessage = "Please choose a username"
                    return@Button
                }
                
                isLoading = true
                errorMessage = null
                successMessage = null
                
                scope.launch {
                    try {
                        val result = if (isLoginMode) {
                            login(email, password, context)
                        } else {
                            signup(email, password, username, context)
                        }
                        
                        result.onSuccess { authResponse ->
                            if (authResponse.success) {
                                successMessage = if (isLoginMode) 
                                    "Login successful! Redirecting..." 
                                else 
                                    "Account created! Redirecting..."
                                
                                // Navigate back after successful auth
                                navController.navigateUp()
                            } else {
                                errorMessage = "Authentication failed"
                            }
                        }.onFailure { throwable ->
                            Timber.tag("CubicJam").e(throwable, "Authentication error")
                            errorMessage = when {
                                throwable.message?.contains("timeout", ignoreCase = true) == true -> 
                                    "Connection timeout. Please check your internet"
                                throwable.message?.contains("400", ignoreCase = true) == true -> 
                                    if (isLoginMode) "Invalid email or password" else "Username already taken"
                                throwable.message?.contains("401", ignoreCase = true) == true -> 
                                    "Invalid credentials"
                                else -> "Authentication failed: ${throwable.message}"
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "Network error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (isLoginMode) app.kreate.android.R.drawable.checkmark 
                        else app.kreate.android.R.drawable.add
                    ),
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isLoading) "Please wait..." else if (isLoginMode) "Login" else "Create Account")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Toggle between login/signup
        TextButton(
            onClick = {
                isLoginMode = !isLoginMode
                errorMessage = null
                successMessage = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isLoginMode) 
                    "Don't have an account? Sign up" 
                else 
                    "Already have an account? Login",
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Back button - using chevron_back
        OutlinedButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(app.kreate.android.R.drawable.chevron_back),
                contentDescription = "Back"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go Back")
        }
    }
}