# V16 Pass 5: missing drawable build failure

GitHub log reports 3 missing R.drawable symbols at MainActivity.java:153. All 3 JPG assets are present in this complete ZIP under app/src/main/res/drawable-nodpi/.

Likely cause: GitHub repository did not receive all new theme JPGs. Upload the entire ZIP project preserving directories (especially all six master_*.jpg). Workflow now checks existence of all six JPG files and fails early with explicit errors.

No Android SDK/Gradle executable was available in the local environment, so an APK build was NOT performed.
