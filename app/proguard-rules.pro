# Add project specific ProGuard rules here.

# Preserve line numbers and source file names
-keepattributes SourceFile,LineNumberTable

# Disable obfuscation to keep the code readable
-dontobfuscate

# Disable optimization (optional, based on your preference)
# -dontoptimize

# Keep specific classes, methods, or fields if necessary
#-keep class com.example.MyClass { *; }
#-keep class * implements com.example.MyInterface
#-keep class * extends com.example.MySuperClass

# Add specific rules for third-party libraries here

# # You can specify any path and filename.
# -printconfiguration /tmp/full-r8-config.txt
