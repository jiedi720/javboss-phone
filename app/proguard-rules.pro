# release 开启 R8（isMinifyEnabled = true）+ 资源压缩，未引用的图标/类会被剥掉。
# 下面是最小必要保留集，删任何一条前先实测登录、列表、详情、播放四条路径。

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ---------- kotlinx.serialization ----------
# DTO 走 @Serializable，Companion / serializer() / $$serializer 不能被裁
-keep class com.javboss.phone.data.api.dto.** { *; }
-keep @kotlinx.serialization.Serializable class com.javboss.phone.** { *; }
-keepclassmembers class com.javboss.phone.** {
    *** Companion;
}
-keepclasseswithmembers class com.javboss.phone.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.javboss.phone.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Retrofit / OkHttp ----------
# Retrofit 靠运行时读取方法上的 @GET/@POST + 注解里的路径字符串来发请求。
# 实测仅靠 AAR 自带 consumer rules 会被裁掉注解与路径字符串（dex 里搜不到 auth/status），
# 所以这里显式保留接口本体、注解类与注解属性。
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep interface com.javboss.phone.data.api.JavBossApi { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepclasseswithmembers,allowshrinking class * {
    @retrofit2.http.* <methods>;
}

# ---------- libVLC (JNI) ----------
# 通过 JNI 反向调用 Java 回调，方法名不能被混淆
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**

# ---------- 本地代理 NanoHTTPD ----------
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ---------- 调试信息 ----------
# 保留行号便于定位崩溃（体积影响很小）
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
