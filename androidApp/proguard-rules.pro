# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.cvforge.**$$serializer { *; }
-keepclassmembers class com.cvforge.** { *** Companion; }
-keepclasseswithmembers class com.cvforge.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
