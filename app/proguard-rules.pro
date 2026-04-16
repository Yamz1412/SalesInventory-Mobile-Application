# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==========================================
# iText PDF Security Rules
# ==========================================
# Ignore missing optional classes from iText PDF
-dontwarn com.itextpdf.bouncycastle.**
-dontwarn com.itextpdf.bouncycastlefips.**
-dontwarn sharpen.config.**
-dontwarn com.itextpdf.bouncycastleconnector.**

# General rule to keep iText classes safe from being overly scrambled
-keep class com.itextpdf.** { *; }

# ==========================================
# NEW: iText Desktop Graphics & Jackson Rules
# ==========================================
# Ignore missing Jackson JSON classes referenced by iText
-dontwarn com.fasterxml.jackson.**

# Ignore missing Java AWT and ImageIO desktop classes (Android uses Bitmaps instead)
-dontwarn java.awt.**
-dontwarn javax.imageio.**