package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily

data class UserGuideSection(
    val number: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val tip: String? = null
)

object UserGuideData {
    fun sectionsFor(language: AppLanguage): List<UserGuideSection> =
        if (language == AppLanguage.BN) banglaSections() else englishSections()

    fun banglaSections(): List<UserGuideSection> = listOf(
        UserGuideSection(
            number = "১",
            title = "শুরু ও স্কুল আইডি সেটআপ (Admin ও Teacher)",
            summary = "ক্লাউড সার্ভারে স্কুলের সকল তথ্য স্বয়ংক্রিয়ভাবে সংরক্ষিত ও সিঙ্ক হয়।",
            steps = listOf(
                "নতুন স্কুল শুরু করতে 'Create School' ট্যাবে স্কুলের নাম, আপনার নাম, মোবাইল নম্বর ও পাসওয়ার্ড দিয়ে প্রবেশ করুন। অ্যাপ একটি ইউনিক School ID তৈরি করবে — আপনি হবেন অ্যাডমিন (Admin)।",
                "অন্যান্য শিক্ষকদের যুক্ত করতে তাদের আপনার School ID দিন। তারা 'Join Existing School' ট্যাবে School ID, নিজ নাম, মোবাইল নম্বর ও পাসওয়ার্ড দিয়ে যুক্ত হবেন।",
                "সব ডাটা নিরাপদে ক্লাউড সার্ভারে (Firebase Firestore) সংরক্ষিত থাকে, তাই ফোন পরিবর্তন করলেও School ID দিয়ে পুনরায় সব তথ্য পাওয়া যাবে।"
            ),
            tip = "Settings থেকে প্রয়োজনে পাসওয়ার্ড যাচাই করে Admin ক্ষমতা অন্য শিক্ষককে হস্তান্তর (Transfer Admin) করা যায়।"
        ),
        UserGuideSection(
            number = "২",
            title = "অ্যাপের ভাষা পরিবর্তন (বাংলা ও English)",
            summary = "পুরো অ্যাপ ও ফলাফলের শিট যেকোনো সময় বাংলা বা ইংরেজিতে ব্যবহার করুন।",
            steps = listOf(
                "লগইন স্ক্রিন, উপরের টপ বারের 'EN / বাংলা' বাটন, অথবা School → Settings-এর ভাষা অংশ থেকে 'বাংলা' বা 'English' নির্বাচন করুন।",
                "মেনু, ফর্ম, মার্কশিট টেবিল, PDF এবং WhatsApp ফলাফল নির্বাচিত ভাষায় স্বয়ংক্রিয়ভাবে পরিবর্তিত হবে।"
            )
        ),
        UserGuideSection(
            number = "৩",
            title = "শিক্ষার্থী ভর্তি, বাংলা নাম, ছবি ও কন্টাক্টস",
            summary = "শিক্ষার্থীর বাংলা নাম বাধ্যতামূলক এবং ইংরেজি নাম ঐচ্ছিক।",
            steps = listOf(
                "হোম বা শিক্ষার্থী ট্যাবের '+ নতুন শিক্ষার্থী' বাটনে চাপুন।",
                "শিক্ষার্থীর বাংলা নাম (Student Name - Bangla) অবশ্যই লিখুন; ইংরেজি নাম (English) ঐচ্ছিক—প্রয়োজনে লিখতে পারেন।",
                "পাসপোর্ট ছবি যোগ করতে প্রোফাইল বা এডিটরের 'Camera' বাটনে সরাসরি ছবি তুলুন অথবা 'Gallery' থেকে বেছে নিন (অটো ৩:৪ পাসপোর্ট সাইজে ক্রপ হবে)।",
                "ফোন নম্বর ফিল্ডের পাশের 'Contacts' আইকনে চাপ দিয়ে ফোনের কন্টাক্ট লিস্ট থেকে সরাসরি অভিভাবকের নম্বর যুক্ত করুন।"
            ),
            tip = "প্রতিটি ফিল্ডের নিচে নোট/মন্তব্য যোগ করা যায়, যেমন জন্ম নিবন্ধন বা বিশেষ তথ্য।"
        ),
        UserGuideSection(
            number = "৪",
            title = "শ্রেণি হাজিরা খাতা (Daily, Weekly, Monthly ও Yearly)",
            summary = "প্রতিদিন শ্রেণিভিত্তিক দ্রুত উপস্থিতি ও অনুপস্থিতি মার্ক করুন।",
            steps = listOf(
                "নিচের 'হাজিরা (Attendance)' ট্যাবে গিয়ে শ্রেণি ও তারিখ নির্বাচন করুন।",
                "প্রতি শিক্ষার্থীর পাশে Present (P), Absent (A), অথবা Leave (L) ট্যাপ করে 'Save Attendance' চাপুন।",
                "একই স্ক্রিন থেকে Weekly, Monthly ও Yearly ট্যাবে গিয়ে হাজিরার শতকরা হার (%) দেখতে পারবেন।"
            )
        ),
        UserGuideSection(
            number = "৫",
            title = "শ্রেণিভিত্তিক বিষয়ের নম্বর শিট ও এক্সেল ইম্পোর্ট",
            summary = "বিষয়ভিত্তিক শিক্ষকরা নির্দিষ্ট শ্রেণি ও বিষয়ের নম্বর একসাথে জমা দেবেন।",
            steps = listOf(
                "হোম স্ক্রিনের 'পরীক্ষার নম্বর শিট (Exam Marks Sheet)' বা শিক্ষার্থী ট্যাব → ব্যাচ এন্ট্রি → 'পরীক্ষার নম্বর' খুলুন।",
                "প্রথমে শ্রেণি (যেমন ২য় শ্রেণি), পরীক্ষা (১ম সাময়িক / ২য় সাময়িক / বার্ষিক) এবং আপনার নির্দিষ্ট বিষয় (যেমন বাংলা) নির্বাচন করুন।",
                "সকল শিক্ষার্থীর নম্বর লিখে সেভ করুন, অথবা 'এক্সেল/সিএসভি থেকে নম্বর নিন' বাটনে .xlsx / .csv ফাইল থেকে অটো-ম্যাচ করে ইম্পোর্ট করুন।"
            ),
            tip = "প্রতিটি বিষয় আলাদাভাবে সেভ হয়—তাই এক শিক্ষকের নম্বর অন্য শিক্ষকের বিষয়ের নম্বর কখনো মুছে ফেলবে না।"
        ),
        UserGuideSection(
            number = "৬",
            title = "বাংলাদেশ পাস/ফেল নিয়ম, GPA ও অটো মেধা স্থান",
            summary = "সঠিক বাংলাদেশ প্রাথমিক মূল্যায়ন পদ্ধতি অনুসরণ করা হয়েছে।",
            steps = listOf(
                "১০০ নম্বরের বিষয়ে পাস নম্বর ৩৩ (৩৩-এর নিচে ফেল, ৩৩–১০০ পাস)।",
                "৫০ নম্বরের বিষয়ে পাস নম্বর ১৭ (১৭-এর নিচে ফেল, ১৭–৫০ পাস)। অন্যান্য পূর্ণমানের ক্ষেত্রে ৩৩% হিসেবে পাস নম্বর নির্ধারিত হয়।",
                "বাংলাদেশ ৫.০০ GPA স্কেল (A+ = 5.00, A = 4.00, A- = 3.50, B = 3.00, C = 2.00, D = 1.00, F = 0.00) স্বয়ংক্রিয়ভাবে গণনা হয়।",
                "মেধা স্থান (1st, 2nd, 3rd...) শুধুমাত্র 'বার্ষিক পরীক্ষা (Final / Annual Exam)'-এ সকল বিষয়ে পাস করা শিক্ষার্থীদের জন্য নির্ধারিত হয়—১ম ও ২য় সাময়িকে নয়।"
            )
        ),
        UserGuideSection(
            number = "৭",
            title = "শ্রেণি ফলাফল, মার্কশিট ও অভিভাবককে পাঠানো",
            summary = "শ্রেণিভিত্তিক ফলাফল দেখুন এবং শিক্ষার্থীর প্রোফাইল থেকে অভিভাবককে মার্কশিট পাঠান।",
            steps = listOf(
                "ব্যাচ এন্ট্রি → 'শ্রেণি ফলাফল (Class Results)' থেকে পুরো শ্রেণির পাস/ফেল, পাসের হার, GPA ও মেধা তালিকা দেখুন এবং Class Result PDF বা CSV এক্সপোর্ট করুন।",
                "যেকোনো শিক্ষার্থীর প্রোফাইল খুলে 'মার্কশিট (৩)' ট্যাবে যান। উপরে মাঝখানে পরীক্ষার নাম, নিচে শিক্ষার্থীর নাম-শ্রেণি-রোল ও বোর্ডারযুক্ত মার্কশিট টেবিল দেখা যাবে।",
                "নিচের 'অভিভাবককে ফলাফল পাঠান' অংশ থেকে সরাসরি WhatsApp মেসেজ, Result PDF, ছবি (PNG) অথবা Standard Share দিয়ে অভিভাবকের কাছে পাঠান।"
            )
        ),
        UserGuideSection(
            number = "৮",
            title = "প্রমোশন ও রোল অবস্থান (বছর শেষে উত্তীর্ণ)",
            summary = "বার্ষিক ফলাফলের ভিত্তিতে অটো প্রমোশন ও রোল নম্বর সাজান।",
            steps = listOf(
                "Settings বা মেনু থেকে 'প্রমোশন ও রোল অবস্থান' খুলুন এবং শ্রেণি নির্বাচন করুন।",
                "'বার্ষিক ফলাফল অনুযায়ী অটো মেধা ও প্রমোশন' বাটনে চাপলে পাস করা শিক্ষার্থীরা মেধা অনুযায়ী রোলসহ 'Promoted' এবং ফেল শিক্ষার্থীরা 'Not Promoted' হবে।",
                "বছর শেষে Admin 'Year-End Promotion' চালালে উত্তীর্ণরা পরবর্তী শ্রেণিতে (৫ম শ্রেণি 'Passed Out') ও নতুন শিক্ষাবর্ষে স্থানান্তরিত হবে।"
            )
        ),
        UserGuideSection(
            number = "৯",
            title = "অ্যাডমিন অনুমোদন, শিক্ষক চ্যাট ও অভিভাবক নোটিশ",
            summary = "স্কুলের সকল শিক্ষকের কাজের সমন্বয় ও অভিভাবক যোগাযোগ।",
            steps = listOf(
                "সহকারী শিক্ষক কোনো শিক্ষার্থী বা নম্বর পরিবর্তন পাঠালে তা 'বিদ্যালয় → অনুমোদন (Approvals)' ট্যাবে জমা হয়। Admin এক ক্লিকে 'Approve All' বা আলাদাভাবে অনুমোদন করতে পারবেন।",
                "'চ্যাট ও নোটিশ (Connect)' ট্যাবে শিক্ষকরা নিজেদের মধ্যে কথা বলতে পারেন এবং ব্যক্তিগত, শ্রেণিভিত্তিক বা পুরো স্কুলের অভিভাবকদের WhatsApp/SMS/Share নোটিশ পাঠাতে পারেন।"
            )
        ),
        UserGuideSection(
            number = "১০",
            title = "এক্সেল ব্যাকআপ ও যেকোনো .xlsx / .csv রিস্টোর",
            summary = "যেকোনো এক্সেল ফাইল থেকে নিরাপদ ব্যাকআপ ও ডাটা পুনরুদ্ধার।",
            steps = listOf(
                "Settings বা উপরের (⋮) মেনু থেকে 'এক্সেল ব্যাকআপ (.xlsx)' বেছে নিয়ে ফোনে বা Google Drive-এ ব্যাকআপ সংরক্ষণ করুন।",
                "'যেকোনো এক্সেল (.xlsx) থেকে রিস্টোর' বাটনে চাপ দিয়ে আপনার যেকোনো .xlsx বা .csv ফাইল নির্বাচন করুন।",
                "অ্যাপের Column Mapping ডায়ালগ থেকে ওয়ার্কশিট ট্যাব, হেডার সারি এবং কোন কলাম কোন তথ্য (নাম, রোল, শ্রেণি, ফোন বা বিষয়ের নম্বর) তা মিলিয়ে 'রিস্টোর / ইম্পোর্ট করুন' চাপুন।"
            )
        )
    )

    fun englishSections(): List<UserGuideSection> = listOf(
        UserGuideSection(
            number = "1",
            title = "Getting Started & School ID (Admin & Teachers)",
            summary = "All school records sync automatically to your cloud database.",
            steps = listOf(
                "To start a new school, use the 'Create School' tab with your School Name, Teacher Name, Phone Number, and Password. The app assigns a unique School ID and makes you Admin.",
                "Share your School ID with colleagues. Other teachers join via 'Join Existing School' using the School ID, their name, phone number, and password.",
                "Because all data is stored safely in Firebase Firestore, switching devices keeps your school register intact."
            ),
            tip = "Admin control can be transferred safely to another verified teacher in School → Settings."
        ),
        UserGuideSection(
            number = "2",
            title = "Bilingual Language Switch (English & বাংলা)",
            summary = "Switch all menus, labels, and result sheets anytime.",
            steps = listOf(
                "Tap the 'EN / বাংলা' chip on the login screen, top app bar, or in School → Settings.",
                "Navigation, forms, marksheet tables, exported PDFs, and WhatsApp summaries adapt immediately."
            )
        ),
        UserGuideSection(
            number = "3",
            title = "Student Enrollment, Mandatory Bangla Name, Camera & Contacts",
            summary = "Bangla name is required; English name is optional.",
            steps = listOf(
                "Tap '+ New Student' from Home or Students.",
                "Enter Student Name (Bangla / বাংলা নাম) as the required field. Student Name (English) is optional.",
                "Tap 'Camera' to capture a passport photo directly (auto-cropped 3:4) or 'Gallery' to pick an existing photo.",
                "Tap the Contacts icon beside Phone Number to pick a guardian's number directly from your phone's Contacts app."
            )
        ),
        UserGuideSection(
            number = "4",
            title = "Class Attendance Sheet",
            summary = "Track daily attendance plus weekly, monthly, and yearly percentages.",
            steps = listOf(
                "Open the Attendance tab, select the Class and Date, mark Present (P) / Absent (A) / Leave (L), and tap Save.",
                "Switch to Weekly, Monthly, or Yearly sub-tabs inside Attendance for roll-up analytics."
            )
        ),
        UserGuideSection(
            number = "5",
            title = "Class-Wise Subject Marks Sheet & Excel Import",
            summary = "Subject teachers submit marks per class & subject without overwriting others.",
            steps = listOf(
                "Open 'Exam Marks Sheet' from Home (or Students → Batch Entry → Exam Marks).",
                "Select Class (e.g., Class 2), Exam (1st Term / 2nd Term / Final Exam), and your Subject (e.g., Bangla).",
                "Type marks or tap 'Import Marks (.xlsx / .csv)' to auto-match by Roll Number or Student Name, then save."
            )
        ),
        UserGuideSection(
            number = "6",
            title = "Bangladesh Pass/Fail Rules, GPA & Final Merit Rank",
            summary = "Configured to Bangladesh primary school assessment standards.",
            steps = listOf(
                "100 full marks → 33 pass mark (below 33 is Fail, 33–100 is Pass).",
                "50 full marks → 17 pass mark (below 17 is Fail, 17–50 is Pass); general subjects use 33% rounded up.",
                "Bangladesh 5.00 GPA scale (A+ down to F) is computed automatically.",
                "Auto Merit Ranking (1st, 2nd, 3rd…) is calculated strictly for the Final / Annual Examination among students who pass all subjects—never on 1st or 2nd Term."
            )
        ),
        UserGuideSection(
            number = "7",
            title = "Class Results & Student Marksheet Sharing",
            summary = "View class result sheets and send formatted marksheets to guardians.",
            steps = listOf(
                "Open 'Class Results' from Home or Batch Entry to inspect class totals, pass rates, merit ranks (Final Exam), and export Class Result PDF/CSV.",
                "Open any Student Profile → 'Marksheet (3)' tab to view the bordered result table with centered Exam Name, Student Name/Class/Roll below it, and subject rows.",
                "Use WhatsApp, Result PDF, Image (PNG), or Share buttons to send the active exam result directly to the guardian."
            )
        ),
        UserGuideSection(
            number = "8",
            title = "Promotion & Roll Manager",
            summary = "Promote passing students and organize next-year roll positions.",
            steps = listOf(
                "Open 'Promotion & Roll Positions' from Settings or the top menu.",
                "Tap 'Auto Merit & Promotion (Final Exam)' to assign Promoted/Not Promoted status and rank-ordered next-year rolls automatically.",
                "At year-end, Admin can run 'Year-End Promotion' to roll students into the next class and academic year (Class 5 promoted students are marked Passed Out)."
            )
        ),
        UserGuideSection(
            number = "9",
            title = "Admin Approvals, Teacher Chat & Parent Notices",
            summary = "Review teacher submissions and communicate with staff and parents.",
            steps = listOf(
                "Changes submitted by non-Admin teachers queue in School → Approvals for Admin review (single or Approve All).",
                "Use School → Chat & Notices for staff messaging and sending formatted notices to individual guardians, a class, or the whole school."
            )
        ),
        UserGuideSection(
            number = "10",
            title = "Excel Backup & Universal .xlsx / .csv Restore",
            summary = "Back up the entire school or import data from any Excel spreadsheet.",
            steps = listOf(
                "Use 'Backup Excel (.xlsx)' in Settings or the top menu to save a full workbook to device storage or Google Drive.",
                "Use 'Restore from Any Excel (.xlsx)' to open any .xlsx or .csv file, pick the sheet tab and header row, map columns to student fields or exam marks, and import cleanly."
            )
        )
    )
}

@Composable
fun UserGuideDialog(
    onDismiss: () -> Unit,
    onSavePdf: (AppLanguage) -> Unit,
    onSharePdf: (AppLanguage) -> Unit
) {
    val currentLanguage = LocalAppLanguage.current
    val strings = LocalAppStrings.current
    var guideLang by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    val sections = remember(guideLang) { UserGuideData.sectionsFor(guideLang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = if (guideLang == AppLanguage.BN) "অ্যাপ ব্যবহার নির্দেশিকা (PDF)" else "App User Guide (PDF)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = guideLang == AppLanguage.BN,
                        onClick = { guideLang = AppLanguage.BN },
                        label = { Text("বাংলা গাইড") },
                        modifier = Modifier.testTag("user_guide_lang_bn")
                    )
                    FilterChip(
                        selected = guideLang == AppLanguage.EN,
                        onClick = { guideLang = AppLanguage.EN },
                        label = { Text("English Guide") },
                        modifier = Modifier.testTag("user_guide_lang_en")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSavePdf(guideLang) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("user_guide_save_pdf_btn")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (guideLang == AppLanguage.BN) "PDF সেভ করুন" else "Save PDF",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { onSharePdf(guideLang) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("user_guide_share_pdf_btn")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (guideLang == AppLanguage.BN) "PDF শেয়ার" else "Share PDF",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sections, key = { it.number }) { sec ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = sec.number,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Text(
                                        text = sec.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = sec.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                sec.steps.forEach { step ->
                                    Text(
                                        text = "• $step",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!sec.tip.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lightbulb,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = sec.tip,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (guideLang == AppLanguage.BN) "বন্ধ করুন" else "Close")
            }
        }
    )
}
