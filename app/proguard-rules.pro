# Gson 通过反射读取运行配置字段名，压缩时保留该模型的 JSON 结构。
-keep class com.androidservice.data.BinaryConfig { *; }
-keepattributes Signature

# Parcelable 在旧系统兼容读取时需要稳定的 CREATOR 字段。
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
