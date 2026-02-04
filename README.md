<p align="center">
  <img src="metadata/en-US/images/icon.png" alt="FairScan icon" width="120" />
</p>

<h1 align="center">FairScan</h1>

<p align="center">
  An Android app to scan your documents —
<br/><b>simple</b> and <b>respectful</b>.
</p>

<p align="center">
  <a href="https://github.com/pynicolas/FairScan/releases"><img src="https://img.shields.io/github/v/release/pynicolas/FairScan?logo=github" /></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/pynicolas/FairScan?color=blue"></a>
</p>
<h3 align="center">
  <b>Get it on:</b>
  <a href="https://f-droid.org/en/packages/org.fairscan.app/">F-Droid</a> ·
  <a href="https://play.google.com/store/apps/details?id=org.fairscan.app">Google Play</a> ·
  <a href="https://github.com/pynicolas/FairScan/releases">GitHub</a>
</h3>

---

FairScan is an Android app to **scan documents quickly, easily and privately**.

It's designed to be **simple**: users get a clean, shareable PDF in seconds, with no manual adjustments.<br>
And **respectful**: open source, minimal permissions, no tracking, no ads.

- Website: https://fairscan.org  
- Blog: https://fairscan.org/blog/

---

## Screenshots

| Scan | Preview | Save & Share |
|------|---------|--------------|
| ![](metadata/en-US/images/phoneScreenshots/1.jpg) | ![](metadata/en-US/images/phoneScreenshots/2.jpg) | ![](metadata/en-US/images/phoneScreenshots/3.jpg) |

---

## Features

- **Clear, distraction-free interface**
- **Easy flow**: scan, review if needed, save or share
- **Automatic document detection** using a custom segmentation model
- **Automatic perspective correction**
- **Automatic image enhancement**
- **Fast PDF generation** with no manual adjustments
- **Fully offline** – the app has *no* internet permission
- **Minimal permissions**
- **Open source**, GPLv3

---

## Compatibility

FairScan works on any device that:
- runs **Android 8.0+**
- has a camera

---

## Experimental: Scan to PDF via intent

FairScan can be invoked by other Android applications to perform a document scan and return a generated PDF.

This feature is **experimental** and intended for developers who want to rely on FairScan as a
simple, privacy-respecting scanning tool.
The intent contract and behavior may change between versions, and backward compatibility
is not guaranteed at this stage.

Intent action: `org.fairscan.app.action.SCAN_TO_PDF`

This is an **implicit intent** that launches FairScan in a dedicated external mode.

When started via this intent:

- FairScan opens directly in scan mode
- the user scans one or more pages
- FairScan generates a single PDF
- the resulting PDF is returned to the calling application as a URI with a limited lifetime
- the calling application should immediately copy the content of the URI as FairScan deletes it later

See an example app: [fairscan-intent-sample](https://github.com/pynicolas/fairscan-intent-sample)

---

## Technical details

FairScan uses:

- [Jetpack Compose](https://developer.android.com/compose) for the UI
- [CameraX](https://developer.android.com/media/camera/camerax) for image capture
- [LiteRT](https://ai.google.dev/edge/litert) to run the custom segmentation model for automatic document detection
- [OpenCV](https://opencv.org/) for perspective correction and image enhancement
- [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) for PDF generation

---

## The segmentation model

FairScan uses a custom-trained image segmentation model to detect documents:<br>
https://github.com/pynicolas/fairscan-segmentation-model

It's based on a fully public dataset that is available here:<br>
https://github.com/pynicolas/fairscan-dataset

The build system automatically downloads the model using  
[`download-tflite.gradle.kts`](app/download-tflite.gradle.kts).

Related blog posts:
- [*Making document detection more reliable*](https://fairscan.org/blog/automatic-document-detection/)
- [*Building a public dataset for FairScan*](https://fairscan.org/blog/building_a_public_dataset/)

---

## Build

To build an APK:

```bash
./gradlew clean check assembleRelease
```
To build an Android App Bundle:
```bash
./gradlew clean check :app:bundleRelease
```

## License
This project is licensed under the GNU GPLv3. See [LICENSE](LICENSE) for details.
