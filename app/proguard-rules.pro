# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Fix OAuth Drive API failure for release builds
# ProGuard Configuration file
#
# See http://proguard.sourceforge.net/index.html#manual/usage.html

# Needed to keep generic types and @Key annotations accessed via reflection

-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# Needed by google-http-client-android when linking against an older platform version

-dontwarn com.google.api.client.extensions.android.**

# Needed by google-api-client-android when linking against an older platform version

-dontwarn com.google.api.client.googleapis.extensions.android.**

# Needed by google-play-services when linking against an older platform version

-dontwarn com.google.android.gms.**


# Missing classes detected by R8 - suppressing warnings for optional dependencies
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# Keep backup/restore related classes for debugging
-keep class com.pasich.mynotes.data.model.backup.** { *; }
-keep class com.pasich.mynotes.data.model.** { *; }
-keepclassmembers class com.pasich.mynotes.data.model.** {
    *;
}

# Keep JSON serialization classes
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Gson classes and related serialization
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep line numbers and source file for debugging
-keepattributes SourceFile,LineNumberTable

# Keep all debug and logging related code
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
# Gson maps these by field name, and only the classes under data.model are kept above.
# Everything below is parsed from data the app itself wrote earlier — note attachments, local
# backups, Google Keep imports — so a renamed field silently deserializes to null rather than
# failing loudly: attachments stop resolving and a restore produces empty notes.
-keep class com.pasich.mynotes.extendedEditor.models.** { *; }
-keep class com.pasich.mynotes.utils.backup.models.** { *; }
-keepclassmembers class com.pasich.mynotes.extendedEditor.models.** { *; }
-keepclassmembers class com.pasich.mynotes.utils.backup.models.** { *; }
