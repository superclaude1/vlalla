# Room generates its own R8 rules. Keep JSON-backed DTO field names and worker constructors.
-keepattributes *Annotation*
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn org.conscrypt.**
