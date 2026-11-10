# DocImageSaver

An Android app that collects images shared from messaging and social apps into the device gallery album **Pictures/Document Images**.

## Core promise

DocImageSaver copies incoming image bytes directly to Android MediaStore. It does not decode, resize, recompress, or convert images. The saved file therefore preserves the exact bytes supplied by the sharing app. If that app already compressed an image before sharing, DocImageSaver cannot recover the earlier original.

## Current status

Initial Android implementation in progress. The first milestone supports Android Sharesheet `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for image content and writes files to the public `Pictures/Document Images` collection.

## Build

Open this project in Android Studio (JDK 17, Android SDK 35) or use the GitHub Actions workflow to build a debug APK.
