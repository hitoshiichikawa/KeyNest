# Add project specific ProGuard rules here.
# Keep Room entities so column names survive R8.
-keep class inc.goodanswers.keynest.data.entity.** { *; }

# Keep AutofillService entry points (Android binds via class name)
-keep public class inc.goodanswers.keynest.autofill.KeyNestAutofillService { *; }
-keep public class inc.goodanswers.keynest.autofill.unlock.AutofillUnlockActivity { *; }

# Keep Application class
-keep public class inc.goodanswers.keynest.KeyNestApp { *; }
