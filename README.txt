 ***************************************************************************
 *
 * Copyright (C) 2008, 2009 Google Inc.
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
 *                     http://code.google.com/p/ringdroid/
 *                           ringdroid@google.com
 *
 ***************************************************************************

A sound editor and ringtone creator for the Android operating system.

Questions, comments, feedback?  Email ringdroid@google.com

Build instructions:

You will need Apache Ant installed.

Download the Android SDK and put the path to the sdk "tools" directory
in your path.  Then run:

rm build.xml
rm -rf bin/ 
android update project -n ringdroid -t 2 -p .

Then, to build:
  ant debug

To install the debug version:
  adb install -r bin/ringdroid-debug.apk

### Release mode

http://code.google.com/android/intro/develop-and-debug.html

ant release
cp bin/ringdroid-unsigned.apk bin/ringdroid-signed.apk
jarsigner -keystore ~/ringdroid.keystore bin/ringdroid-signed.apk ringdroid
zipalign -f 4 bin/ringdroid-signed.apk bin/Ringdroid.apk

# Initial key generated with:
# keytool -genkey -keystore ringdroid.keystore -alias ringdroid -validity 10000
