package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.firebase.SchoolRepository
import com.example.data.model.TeacherProfile
import com.example.data.model.generateUniqueSchoolNumber
import com.example.data.model.normalizeSchoolId
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class EntryMode(val label: String) {
    JOIN("Sign Up / Login"),
    CREATE("Create School")
}

private fun generateOtpCode(digits: Int): String {
    val safeDigits = if (digits == 4) 4 else 6
    val minVal = if (safeDigits == 4) 1000 else 100000
    val maxVal = if (safeDigits == 4) 9999 else 999999
    return Random.nextInt(minVal, maxVal + 1).toString()
}

private fun accountKey(schoolId: String, phone: String): String {
    return "cred_${normalizeSchoolId(schoolId)}_${TeacherProfile.normalizePhone(phone)}"
}

@Composable
fun SchoolEntryScreen(
    deviceId: String,
    initialTeacherName: String,
    recentSchools: List<String>,
    repository: SchoolRepository,
    onEnteredSchool: (schoolId: String, teacherName: String) -> Unit
) {
    val context = LocalContext.current
    val credsPrefs = remember(context) {
        context.applicationContext.getSharedPreferences("my_school_teacher_auth", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(EntryMode.JOIN) }
    var joinSchoolId by remember { mutableStateOf(recentSchools.firstOrNull().orEmpty()) }
    var createSchoolId by remember { mutableStateOf(generateUniqueSchoolNumber()) }
    var schoolName by remember { mutableStateOf("") }
    var emisCode by remember { mutableStateOf("") }
    var upazilaDistrict by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf(initialTeacherName) }

    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // OTP & Forgot Password state
    var otpDigits by remember { mutableIntStateOf(6) }
    var generatedOtp by remember { mutableStateOf<String?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var otpVerified by remember { mutableStateOf(false) }
    var isForgotPasswordFlow by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun resetOtpState(clearBanner: Boolean = true) {
        generatedOtp = null
        otpInput = ""
        otpVerified = false
        if (clearBanner) statusBanner = null
    }

    fun sendOtp(schoolKeyRaw: String) {
        val cleanSchool = normalizeSchoolId(schoolKeyRaw)
        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
        if (cleanSchool.length < 2) {
            errorMessage = "Enter a valid School ID first."
            return
        }
        if (normPhone.length < 10) {
            errorMessage = "Enter a valid phone number (e.g. 017XXXXXXXX) to receive OTP."
            return
        }
        errorMessage = null
        val code = generateOtpCode(otpDigits)
        generatedOtp = code
        otpVerified = false
        statusBanner = "A $otpDigits-digit OTP ($code) has been dispatched to $normPhone. Enter it below to verify."
    }

    fun verifyOtpCode() {
        val expected = generatedOtp
        if (expected.isNullOrBlank()) {
            errorMessage = "Tap 'Send OTP' first."
            return
        }
        if (otpInput.trim() == expected) {
            otpVerified = true
            errorMessage = null
            statusBanner = "OTP verified for ${TeacherProfile.normalizePhone(phoneNumber)}."
        } else {
            otpVerified = false
            errorMessage = "Invalid OTP code. Please check the $otpDigits-digit code and try again."
        }
    }

    fun saveDeviceCredentials(schoolKeyRaw: String, phoneRaw: String, plainPassword: String, nameRaw: String) {
        val key = accountKey(schoolKeyRaw, phoneRaw)
        credsPrefs.edit()
            .putString("${key}_hash", TeacherProfile.hashPassword(plainPassword))
            .putString("${key}_name", nameRaw.trim())
            .putString("last_phone", TeacherProfile.normalizePhone(phoneRaw))
            .apply()
    }

    fun validatePasswordPair(pw: String, confirmPw: String): String? {
        if (pw.length < 4) return "Password must be at least 4 characters."
        if (pw != confirmPw) return "Password and Confirm Password do not match."
        return null
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 700.dp

            Image(
                painter = painterResource(id = R.drawable.img_simple_bg),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.35f),
                contentScale = ContentScale.Crop
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            SchoolEntryHeroCard(modifier = Modifier.weight(0.95f))
                            SchoolEntryFormCard(
                                mode = mode,
                                onModeChange = {
                                    mode = it
                                    errorMessage = null
                                    isForgotPasswordFlow = false
                                    resetOtpState()
                                },
                                joinSchoolId = joinSchoolId,
                                onJoinSchoolIdChange = {
                                    joinSchoolId = it.uppercase()
                                    resetOtpState()
                                },
                                recentSchools = recentSchools,
                                createSchoolId = createSchoolId,
                                onCreateSchoolIdChange = {
                                    createSchoolId = it.uppercase()
                                    resetOtpState()
                                },
                                onRegenerateId = { createSchoolId = generateUniqueSchoolNumber() },
                                schoolName = schoolName,
                                onSchoolNameChange = { schoolName = it },
                                emisCode = emisCode,
                                onEmisCodeChange = { emisCode = it },
                                upazilaDistrict = upazilaDistrict,
                                onUpazilaDistrictChange = { upazilaDistrict = it },
                                teacherName = teacherName,
                                onTeacherNameChange = { teacherName = it },
                                phoneNumber = phoneNumber,
                                onPhoneNumberChange = {
                                    phoneNumber = it
                                    resetOtpState()
                                },
                                password = password,
                                onPasswordChange = { password = it },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = { confirmPassword = it },
                                otpDigits = otpDigits,
                                onOtpDigitsChange = {
                                    otpDigits = it
                                    resetOtpState()
                                },
                                generatedOtp = generatedOtp,
                                otpInput = otpInput,
                                onOtpInputChange = { otpInput = it },
                                otpVerified = otpVerified,
                                isForgotPasswordFlow = isForgotPasswordFlow,
                                onToggleForgotPassword = {
                                    isForgotPasswordFlow = !isForgotPasswordFlow
                                    errorMessage = null
                                    resetOtpState()
                                },
                                onSendOtp = {
                                    val targetId = if (mode == EntryMode.JOIN) joinSchoolId else createSchoolId
                                    sendOtp(targetId)
                                },
                                onVerifyOtp = { verifyOtpCode() },
                                statusBanner = statusBanner,
                                isBusy = isBusy,
                                errorMessage = errorMessage,
                                onSubmitJoin = {
                                    scope.launch {
                                        val cleanId = normalizeSchoolId(joinSchoolId)
                                        val cleanTeacher = teacherName.trim()
                                        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
                                        if (cleanId.length < 2 || cleanTeacher.isEmpty() || normPhone.length < 10) {
                                            errorMessage = "Enter Teacher Name, School ID, and valid Phone Number."
                                            return@launch
                                        }
                                        val key = accountKey(cleanId, normPhone)
                                        val existingHash = credsPrefs.getString("${key}_hash", null)
                                        val savedName = credsPrefs.getString("${key}_name", null)

                                        if (isForgotPasswordFlow) {
                                            if (!otpVerified) {
                                                errorMessage = "Verify the OTP sent to $normPhone before setting a new password."
                                                return@launch
                                            }
                                            val pwErr = validatePasswordPair(password, confirmPassword)
                                            if (pwErr != null) {
                                                errorMessage = pwErr
                                                return@launch
                                            }
                                            isBusy = true
                                            errorMessage = null
                                            saveDeviceCredentials(cleanId, normPhone, password, cleanTeacher)
                                            val res = repository.joinSchoolWorkspace(cleanId, cleanTeacher, deviceId)
                                            isBusy = false
                                            res.onSuccess { enteredId ->
                                                isForgotPasswordFlow = false
                                                resetOtpState()
                                                onEnteredSchool(enteredId, cleanTeacher)
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Could not reset password."
                                            }
                                            return@launch
                                        }

                                        if (existingHash == null) {
                                            // First-time sign up on this device: require Confirm Password + OTP verification
                                            val pwErr = validatePasswordPair(password, confirmPassword)
                                            if (pwErr != null) {
                                                errorMessage = pwErr
                                                return@launch
                                            }
                                            if (!otpVerified) {
                                                errorMessage = "First-time sign up requires sending and verifying the $otpDigits-digit OTP sent to your phone."
                                                return@launch
                                            }
                                            isBusy = true
                                            errorMessage = null
                                            saveDeviceCredentials(cleanId, normPhone, password, cleanTeacher)
                                            val res = repository.joinSchoolWorkspace(cleanId, cleanTeacher, deviceId)
                                            isBusy = false
                                            res.onSuccess { enteredId ->
                                                resetOtpState()
                                                onEnteredSchool(enteredId, cleanTeacher)
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Could not open school."
                                            }
                                        } else {
                                            // Returning login on this device: verify password against stored hash
                                            if (password.isBlank()) {
                                                errorMessage = "Enter your password to sign in."
                                                return@launch
                                            }
                                            if (!TeacherProfile.verifyPassword(password, existingHash)) {
                                                errorMessage = "Incorrect password for $normPhone. Use 'Forgot Password' if needed."
                                                return@launch
                                            }
                                            isBusy = true
                                            errorMessage = null
                                            val effectiveName = cleanTeacher.ifBlank { savedName.orEmpty() }
                                            saveDeviceCredentials(cleanId, normPhone, password, effectiveName)
                                            val res = repository.joinSchoolWorkspace(cleanId, effectiveName, deviceId)
                                            isBusy = false
                                            res.onSuccess { enteredId ->
                                                onEnteredSchool(enteredId, effectiveName)
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Could not open school."
                                            }
                                        }
                                    }
                                },
                                onSubmitCreate = {
                                    scope.launch {
                                        val cleanId = normalizeSchoolId(createSchoolId)
                                        val cleanTeacher = teacherName.trim()
                                        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
                                        if (normPhone.length < 10) {
                                            errorMessage = "Enter a valid phone number for the School Admin account."
                                            return@launch
                                        }
                                        val pwErr = validatePasswordPair(password, confirmPassword)
                                        if (pwErr != null) {
                                            errorMessage = pwErr
                                            return@launch
                                        }
                                        if (!otpVerified) {
                                            errorMessage = "Verify the $otpDigits-digit OTP sent to $normPhone to create your Admin account."
                                            return@launch
                                        }
                                        isBusy = true
                                        errorMessage = null
                                        val res = repository.createSchoolWorkspace(
                                            rawSchoolId = cleanId,
                                            schoolName = schoolName,
                                            emisCode = emisCode,
                                            upazilaDistrict = upazilaDistrict,
                                            teacherName = cleanTeacher,
                                            deviceId = deviceId
                                        )
                                        isBusy = false
                                        res.onSuccess { createdId ->
                                            saveDeviceCredentials(createdId, normPhone, password, cleanTeacher)
                                            resetOtpState()
                                            onEnteredSchool(createdId, cleanTeacher)
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "Could not create school."
                                        }
                                    }
                                },
                                credsPrefs = credsPrefs,
                                modifier = Modifier.weight(1.25f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SchoolEntryHeroCard(modifier = Modifier.fillMaxWidth())
                            SchoolEntryFormCard(
                                mode = mode,
                                onModeChange = {
                                    mode = it
                                    errorMessage = null
                                    isForgotPasswordFlow = false
                                    resetOtpState()
                                },
                                joinSchoolId = joinSchoolId,
                                onJoinSchoolIdChange = {
                                    joinSchoolId = it.uppercase()
                                    resetOtpState()
                                },
                                recentSchools = recentSchools,
                                createSchoolId = createSchoolId,
                                onCreateSchoolIdChange = {
                                    createSchoolId = it.uppercase()
                                    resetOtpState()
                                },
                                onRegenerateId = { createSchoolId = generateUniqueSchoolNumber() },
                                schoolName = schoolName,
                                onSchoolNameChange = { schoolName = it },
                                emisCode = emisCode,
                                onEmisCodeChange = { emisCode = it },
                                upazilaDistrict = upazilaDistrict,
                                onUpazilaDistrictChange = { upazilaDistrict = it },
                                teacherName = teacherName,
                                onTeacherNameChange = { teacherName = it },
                                phoneNumber = phoneNumber,
                                onPhoneNumberChange = {
                                    phoneNumber = it
                                    resetOtpState()
                                },
                                password = password,
                                onPasswordChange = { password = it },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = { confirmPassword = it },
                                otpDigits = otpDigits,
                                onOtpDigitsChange = {
                                    otpDigits = it
                                    resetOtpState()
                                },
                                generatedOtp = generatedOtp,
                                otpInput = otpInput,
                                onOtpInputChange = { otpInput = it },
                                otpVerified = otpVerified,
                                isForgotPasswordFlow = isForgotPasswordFlow,
                                onToggleForgotPassword = {
                                    isForgotPasswordFlow = !isForgotPasswordFlow
                                    errorMessage = null
                                    resetOtpState()
                                },
                                onSendOtp = {
                                    val targetId = if (mode == EntryMode.JOIN) joinSchoolId else createSchoolId
                                    sendOtp(targetId)
                                },
                                onVerifyOtp = { verifyOtpCode() },
                                statusBanner = statusBanner,
                                isBusy = isBusy,
                                errorMessage = errorMessage,
                                onSubmitJoin = {
                                    scope.launch {
                                        val cleanId = normalizeSchoolId(joinSchoolId)
                                        val cleanTeacher = teacherName.trim()
                                        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
                                        if (cleanId.length < 2 || cleanTeacher.isEmpty() || normPhone.length < 10) {
                                            errorMessage = "Enter Teacher Name, School ID, and valid Phone Number."
                                            return@launch
                                        }
                                        val key = accountKey(cleanId, normPhone)
                                        val existingHash = credsPrefs.getString("${key}_hash", null)
                                        val savedName = credsPrefs.getString("${key}_name", null)

                                        if (isForgotPasswordFlow) {
                                            if (!otpVerified) {
                                                errorMessage = "Verify the OTP sent to $normPhone before setting a new password."
                                                return@launch
                                            }
                                            val pwErr = validatePasswordPair(password, confirmPassword)
                                            if (pwErr != null) {
                                                errorMessage = pwErr
                                                return@launch
                                            }
                                            isBusy = true
                                            errorMessage = null
                                            saveDeviceCredentials(cleanId, normPhone, password, cleanTeacher)
                                            val res = repository.joinSchoolWorkspace(cleanId, cleanTeacher, deviceId)
                                            isBusy = false
                                            res.onSuccess { enteredId ->
                                                isForgotPasswordFlow = false
                                                resetOtpState()
                                                onEnteredSchool(enteredId, cleanTeacher)
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Could not reset password."
                                            }
                                            return@launch
                                        }

                                        if (existingHash == null) {
                                            val pwErr = validatePasswordPair(password, confirmPassword)
                                            if (pwErr != null) {
                                                errorMessage = pwErr
                                                return@launch
                                            }
                                            if (!otpVerified) {
                                                errorMessage = "First-time sign up requires sending and verifying the $otpDigits-digit OTP sent to your phone."
                                                return@launch
                                            }
                                            isBusy = true
                                            errorMessage = null
                                            saveDeviceCredentials(cleanId, normPhone, password, cleanTeacher)
                                            val res = repository.joinSchoolWorkspace(cleanId, cleanTeacher, deviceId)
                                            isBusy = false
                                            res.onSuccess { enteredId ->
                                                resetOtpState()
                                                onEnteredSchool(enteredId, cleanTeacher)
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Could not open school."
                                            }
                                        } else {
                                            if (password.isBlank()) {
                                                errorMessage = "Enter your password to sign in."
                                                return@launch
                                            }
                                            if (!TeacherProfile.verifyPassword(password, existingHash)) {
                                                errorMessage = "Incorrect password for $normPhone. Use 'Forgot Password' if needed."
                                                return@launch
                                            }
                                            isBusy = true
                                            errorMessage = null
                                            val effectiveName = cleanTeacher.ifBlank { savedName.orEmpty() }
                                            saveDeviceCredentials(cleanId, normPhone, password, effectiveName)
                                            val res = repository.joinSchoolWorkspace(cleanId, effectiveName, deviceId)
                                            isBusy = false
                                            res.onSuccess { enteredId ->
                                                onEnteredSchool(enteredId, effectiveName)
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "Could not open school."
                                            }
                                        }
                                    }
                                },
                                onSubmitCreate = {
                                    scope.launch {
                                        val cleanId = normalizeSchoolId(createSchoolId)
                                        val cleanTeacher = teacherName.trim()
                                        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
                                        if (normPhone.length < 10) {
                                            errorMessage = "Enter a valid phone number for the School Admin account."
                                            return@launch
                                        }
                                        val pwErr = validatePasswordPair(password, confirmPassword)
                                        if (pwErr != null) {
                                            errorMessage = pwErr
                                            return@launch
                                        }
                                        if (!otpVerified) {
                                            errorMessage = "Verify the $otpDigits-digit OTP sent to $normPhone to create your Admin account."
                                            return@launch
                                        }
                                        isBusy = true
                                        errorMessage = null
                                        val res = repository.createSchoolWorkspace(
                                            rawSchoolId = cleanId,
                                            schoolName = schoolName,
                                            emisCode = emisCode,
                                            upazilaDistrict = upazilaDistrict,
                                            teacherName = cleanTeacher,
                                            deviceId = deviceId
                                        )
                                        isBusy = false
                                        res.onSuccess { createdId ->
                                            saveDeviceCredentials(createdId, normPhone, password, cleanTeacher)
                                            resetOtpState()
                                            onEnteredSchool(createdId, cleanTeacher)
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "Could not create school."
                                        }
                                    }
                                },
                                credsPrefs = credsPrefs,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolEntryHeroCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_school_banner),
                contentDescription = "My School banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x22000000), Color(0xDD00241A))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_icon_foreground),
                    contentDescription = "My School icon",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        text = "My School",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Shared Primary School Register & Attendance",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE0F2E9)
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Phone + Password • Shared School ID",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "• First-time users set a Password + Confirm Password and verify a 4- or 6-digit OTP on their phone number.\n• Returning logins use your phone number & password without creating duplicate accounts.\n• Multiple teachers can add/edit simultaneously; edits await Admin confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun SchoolEntryFormCard(
    mode: EntryMode,
    onModeChange: (EntryMode) -> Unit,
    joinSchoolId: String,
    onJoinSchoolIdChange: (String) -> Unit,
    recentSchools: List<String>,
    createSchoolId: String,
    onCreateSchoolIdChange: (String) -> Unit,
    onRegenerateId: () -> Unit,
    schoolName: String,
    onSchoolNameChange: (String) -> Unit,
    emisCode: String,
    onEmisCodeChange: (String) -> Unit,
    upazilaDistrict: String,
    onUpazilaDistrictChange: (String) -> Unit,
    teacherName: String,
    onTeacherNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    otpDigits: Int,
    onOtpDigitsChange: (Int) -> Unit,
    generatedOtp: String?,
    otpInput: String,
    onOtpInputChange: (String) -> Unit,
    otpVerified: Boolean,
    isForgotPasswordFlow: Boolean,
    onToggleForgotPassword: () -> Unit,
    onSendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    statusBanner: String?,
    isBusy: Boolean,
    errorMessage: String?,
    onSubmitJoin: () -> Unit,
    onSubmitCreate: () -> Unit,
    credsPrefs: android.content.SharedPreferences,
    modifier: Modifier = Modifier
) {
    val activeSchoolId = if (mode == EntryMode.JOIN) joinSchoolId else createSchoolId
    val hasExistingAccountOnDevice = remember(activeSchoolId, phoneNumber, credsPrefs) {
        val cleanId = normalizeSchoolId(activeSchoolId)
        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
        cleanId.length >= 2 && normPhone.length >= 10 &&
            credsPrefs.getString("${accountKey(cleanId, normPhone)}_hash", null) != null
    }
    val requiresNewPasswordAndOtp = mode == EntryMode.CREATE || isForgotPasswordFlow || !hasExistingAccountOnDevice

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EntryMode.entries.forEachIndexed { idx, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { onModeChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = EntryMode.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (entry == EntryMode.JOIN) Icons.Rounded.Groups else Icons.Rounded.AddBusiness,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("entry_mode_${entry.name.lowercase()}")
                    ) {
                        Text(entry.label, maxLines = 1)
                    }
                }
            }

            if (mode == EntryMode.JOIN) {
                OutlinedTextField(
                    value = joinSchoolId,
                    onValueChange = onJoinSchoolIdChange,
                    label = { Text("Unique School ID") },
                    placeholder = { Text("e.g. 482915") },
                    leadingIcon = { Icon(Icons.Rounded.School, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_join_school_id_input")
                )

                if (recentSchools.isNotEmpty()) {
                    Text(
                        text = "Recent Schools on This Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSchools.forEach { code ->
                            AssistChip(
                                onClick = { onJoinSchoolIdChange(code) },
                                label = {
                                    Text(
                                        text = code,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = createSchoolId,
                    onValueChange = onCreateSchoolIdChange,
                    label = { Text("Unique School ID (Share with colleagues)") },
                    supportingText = {
                        Text(
                            text = "Generated unique 6-digit number or enter your own ID (e.g. EMIS code).",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.School, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = onRegenerateId,
                            modifier = Modifier.testTag("entry_regenerate_id_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Generate new school number")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_create_school_id_input")
                )

                OutlinedTextField(
                    value = schoolName,
                    onValueChange = onSchoolNameChange,
                    label = { Text("School Name") },
                    placeholder = { Text("e.g. Purbo Para Govt. Primary School") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_school_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = emisCode,
                        onValueChange = onEmisCodeChange,
                        label = { Text("EMIS Code") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = upazilaDistrict,
                        onValueChange = onUpazilaDistrictChange,
                        label = { Text("Upazila / District") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }

            OutlinedTextField(
                value = teacherName,
                onValueChange = onTeacherNameChange,
                label = {
                    Text(
                        if (mode == EntryMode.CREATE) "Your Name (School Admin / Head Teacher)"
                        else "Your Teacher Name"
                    )
                },
                placeholder = { Text("e.g. Md. Rafiqul Islam") },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_teacher_name_input")
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = { Text("Phone Number") },
                placeholder = { Text("e.g. 017XXXXXXXX") },
                leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_phone_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = {
                    Text(
                        when {
                            isForgotPasswordFlow -> "New Password"
                            requiresNewPasswordAndOtp -> "Create Password"
                            else -> "Password"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_password_input")
            )

            if (requiresNewPasswordAndOtp) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = {
                        Text(
                            if (isForgotPasswordFlow) "Confirm New Password"
                            else "Confirm Password"
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_confirm_password_input")
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Phone OTP Verification",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = otpDigits == 4,
                                    onClick = { onOtpDigitsChange(4) },
                                    label = { Text("4-Digit") }
                                )
                                FilterChip(
                                    selected = otpDigits == 6,
                                    onClick = { onOtpDigitsChange(6) },
                                    label = { Text("6-Digit") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = onOtpInputChange,
                                label = { Text("$otpDigits-Digit OTP") },
                                leadingIcon = { Icon(Icons.Rounded.Sms, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("entry_otp_input")
                            )
                            OutlinedButton(
                                onClick = onSendOtp,
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text(if (generatedOtp == null) "Send OTP" else "Resend")
                            }
                        }

                        if (generatedOtp != null && !otpVerified) {
                            Button(
                                onClick = onVerifyOtp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Confirm OTP")
                            }
                        } else if (otpVerified) {
                            Text(
                                text = "OTP Verified",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (mode == EntryMode.JOIN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasExistingAccountOnDevice && !isForgotPasswordFlow) {
                            "Account found on device — enter password."
                        } else if (isForgotPasswordFlow) {
                            "Resetting password via OTP."
                        } else {
                            "First time here — set password & verify OTP."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onToggleForgotPassword,
                        modifier = Modifier.testTag("entry_forgot_password_button")
                    ) {
                        Text(if (isForgotPasswordFlow) "Back to Login" else "Forgot Password?")
                    }
                }
            }

            if (!statusBanner.isNullOrBlank() && errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            val canSubmit = if (mode == EntryMode.JOIN) {
                !isBusy && normalizeSchoolId(joinSchoolId).length >= 2 &&
                    teacherName.trim().isNotEmpty() && TeacherProfile.normalizePhone(phoneNumber).length >= 10
            } else {
                !isBusy && normalizeSchoolId(createSchoolId).length >= 2 &&
                    schoolName.trim().isNotEmpty() && teacherName.trim().isNotEmpty() &&
                    TeacherProfile.normalizePhone(phoneNumber).length >= 10
            }

            Button(
                onClick = if (mode == EntryMode.JOIN) onSubmitJoin else onSubmitCreate,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("entry_submit_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting…")
                } else {
                    Text(
                        text = when {
                            mode == EntryMode.CREATE -> "Create School & Start as Admin"
                            isForgotPasswordFlow -> "Set New Password & Enter"
                            hasExistingAccountOnDevice -> "Sign In to School"
                            else -> "Create Account & Enter School"
                        }
                    )
                }
            }
        }
    }
}