# Add project specific ProGuard rules here.
# Keep Room entities so column names survive R8.
-keep class com.example.keynest.data.entity.** { *; }

# Keep AutofillService entry points (Android binds via class name)
-keep public class com.example.keynest.autofill.KeyNestAutofillService { *; }
-keep public class com.example.keynest.autofill.unlock.AutofillUnlockActivity { *; }

# Keep Application class
-keep public class com.example.keynest.KeyNestApp { *; }
