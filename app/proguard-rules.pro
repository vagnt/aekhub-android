# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class gr.aekhub.app.**$$serializer { *; }
-keepclassmembers class gr.aekhub.app.** { *** Companion; }
-keepclasseswithmembers class gr.aekhub.app.** { kotlinx.serialization.KSerializer serializer(...); }
# jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
# okhttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
