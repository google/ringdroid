 ***************************************************************************
 *
 * Copyright (C) 2008 - 2011 Google Inc.
 *
 * Ringdroid is licensed under the Apache License, Version 2.0.
 * You may not use this source code except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ***************************************************************************
 *
 *                                Ringdroid
 *
 *                     https://github.com/google/ringdroid/
 *                           ringdroid@google.com
 *
 ***************************************************************************

A sound editor and ringtone creator for the Android operating system.

Questions, comments, feedback?  Email ringdroid@google.com

### Build instructions when using Android Studio:

Import the project using "Import project (Eclipse ADT, Gradle, etc.)"
or File -> New -> Import Project...
Select build.gradle (the one in the root directory, not app/build.gradle).
Run the app (using the Run icon, or Run -> Run 'app'). This will build and install Ringdroid on the
connected device.

### Build instructions when using a terminal:

You need to have an environment variable JAVA_HOME pointing to your Java SDK.
You also need to have an environment variable ANDROID_HOME pointing to your Android SDK.
Then run:

  ./gradlew build

This will download gradle if you don't have it already.
The APKs are generated in ./app/build/outputs/apk/.

To install the debug version just run:

  adb install ./app/build/outputs/apk/app-debug.apk

### Release mode

http://developer.android.com/tools/publishing/app-signing.html

On Android Studio:
Build -> Generate Signed APK...
Select the app module, enter the path to the keystore and the passwords, and then choose the
destination folder before clicking on Finish.

You can also directly modify the app/build.gradle to include a signing config as explained on
http://developer.android.com/tools/publishing/app-signing.html#release-mode

# Initial key generated with:
# keytool -genkey -keystore ringdroid.keystore -alias ringdroid -validity 10000
