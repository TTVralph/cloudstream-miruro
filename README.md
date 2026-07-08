# Miruro Cloudstream Starter

This ZIP is a minimal Cloudstream extension starter project with the Gradle setup changed away from the failing dependency:

```kotlin
classpath("com.github.recloudstream:cloudstream-compiler:master-SNAPSHOT")
```

It uses:

```kotlin
classpath("com.github.recloudstream:gradle:-SNAPSHOT")
```

## How to use

1. Extract this ZIP.
2. Open the extracted folder in Android Studio.
3. Let Gradle sync.
4. Replace `MiruroProvider/src/main/kotlin/com/miruro/MiruroProvider.kt` with your actual provider code after Gradle syncs.

## Note

This is a starter/build-fix project only. It does not include scraping, stream resolving, or bypass code.
