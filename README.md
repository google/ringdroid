![](https://github.com/google/ringdroid/wiki/images/header.png)

The version of Ringdroid on the Market now requires Android 1.6! If you have Android 1.5 and can't find Ringdroid on the Market, download [Ringdroid 2.3](http://ringdroid.googlecode.com/files/Ringdroid-2.3.apk), which will work great on your phone.

**26 July 2010: Ringdroid 2.4 fixes problems with assigning a ringtone to a contact on Eclair and improves accessibility with improved keyboard access and content descriptions. The minimum Android version is now 1.6.**

**19 January 2010: Ringdroid 2.3 fixes incompatibilities with the Droid Eris.**

**1 January 2010: Ringdroid 2.2 fixes several previously reported problems and improves support for phones running Donut or Eclair and phones with high-resolution screens.**

**24 August 2009: Ringdroid 2.1 plays through the main audio channel rather than the ringtone channel, and fixes a problem with some AAC files.**

**16 July 2009: Ringdroid 2.0 adds AAC support (iTunes music files), lets you assign ringtones directly to a contact, and includes more descriptive error messages when something goes wrong.**

**26 April 2009: Ringdroid 1.1 adds compatibility with Android 1.5 (Cupcake) and phones with a soft keyboard, and makes it possible to delete sounds from within Ringdroid by long-pressing on a sound in the main list view.**

Ringdroid is an [Android](https://developers.google.com/android) application for recording and editing sounds, and creating ringtones, directly on the handset.

If you have an Android phone, just click on "Market" and search for "Ringdroid" to install it, or open the "Barcode Scanner" app and point to the [QR code](http://phandroid.com/2009/07/29/qr-code-faq-and-fun/) at the top of this page.

  * [FAQ](https://github.com/google/ringdroid/wiki/FAQ)
  * [User Guide](https://github.com/google/ringdroid/wiki/Using-Ringdroid)
  * [Screenshots](https://github.com/google/ringdroid/wiki/Screenshots)
  * [Survey](http://spreadsheets.google.com/viewform?key=pjClfOcMDHuckMdw44cfgNA)

For developers:

  * [Developers](https://github.com/google/ringdroid/Developers)
  * [Building](https://github.com/google/ringdroid/Building)
  * [Emulator](https://github.com/google/ringdroid/Emulator)

See also:

  * [Watch this video](http://www.phonedog.com/cell-phone-videos/t-mobile-g1-review-phone-and-ringtones.aspx) of Noah Kravitz of phonedog.com creating a ringtone using Ringdroid!
  * [How to put Ringtones on Droid HD](http://www.youtube.com/watch?v=AnAZ829lDVo)

#### Features

* Open an existing audio file
* View a scrollable waveform representation of the audio file at 5 zoom levels
* Set starting and ending points for a clip within the audio file, using an optional touch interface
* Play the selected portion of the audio, including an indicator cursor and autoscrolling of the waveform
* Play anywhere else by tapping the screen
* Save the clipped audio as a new audio file and mark it as Music, Ringtone, Alarm, or Notification.
* Record a new audio clip to edit
* Delete audio (with confirmation alert)
* Launches automatically in response to the GET_CONTENT intent with a mime type of audio/ if any other application wants to pick an audio file - for example the "Rings Extended" application.
* Assign a ringtone directly to a contact.

#### File formats
Supported file formats right now include:

* MP3
* AAC/MP4 (including unprotected iTunes music)
* WAV
* 3GPP/AMR (this is the format used when you record sounds directly on the handset)

![](https://github.com/google/ringdroid/wiki/images/ringdroid_screenshot_2_small.png)
