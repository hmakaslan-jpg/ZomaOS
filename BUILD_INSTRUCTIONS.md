# ZomaOS V15.7 test APK

This is source code, NOT an APK. The APK has not been compiled or device-tested here.

1. Upload the project contents (not the outer enclosing directory) to the root of your GitHub repository.
2. Open GitHub Actions -> Build ZomaOS V15.7 Test APK -> Run workflow.
3. Wait for the job to succeed; download artifact `ZomaOS_V15_7_Test_APK`.
4. Unzip it to get `ZomaOS_V15_7_Test.apk`.
5. Keep your known-working old APK as a backup before testing.

The workflow uses Java 17, Gradle 8.9 and Android Gradle Plugin 8.6.1.

If the workflow fails, share the failed build log. A source ZIP integrity check does not validate Android compilation.
