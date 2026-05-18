# Add project specific ProGuard rules here.
# Keep Room entities so column names survive R8.
-keep class io.github.hitoshiichikawa.keynest.data.entity.** { *; }

# Keep AutofillService entry points (Android binds via class name)
-keep public class io.github.hitoshiichikawa.keynest.autofill.KeyNestAutofillService { *; }
-keep public class io.github.hitoshiichikawa.keynest.autofill.unlock.AutofillUnlockActivity { *; }

# Keep Application class
-keep public class io.github.hitoshiichikawa.keynest.KeyNestApp { *; }
