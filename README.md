<p align="center">
  <img src="art/logo.svg" width="108" height="108" alt="Fleench Logo" />
</p>

<h1 align="center">Fleench</h1>

<p align="center">
  <strong>Instant, in-context explanations for anything on your Android screen.</strong>
</p>

<p align="center">
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android%209.0%2B-blue?style=flat-square" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square" alt="Language" /></a>
  <a href="https://github.com/ogboNoble001/fleench"><img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square" alt="Build" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-slate?style=flat-square" alt="License" /></a>
</p>

---

## What is Fleench?

Have you ever been reading a tweet, a Reddit thread, an article, or a fast-moving group chat and stumbled across a word, acronym, internet slang, or complicated phrase you did not quite understand?

Normally, you have to leave what you are doing, switch apps, open a web browser, type it out, and hope you find a definition that matches how the word was actually used.

Fleench removes all of that friction.

It places a tiny, subtle floating bubble resting quietly on the edge of your screen. Whenever you see something unfamiliar, simply drag the bubble over the text. A precise crosshair snaps directly to the word or sentence. When you release your finger, a beautiful frosted-glass card slides up with a concise, direct explanation grounded in the exact context of what you are reading :)

---

## How It Works

1. **Drag**: Pull the floating bubble resting on the edge of your screen toward any word or phrase.
2. **Snap**: A precision crosshair snaps cleanly onto words. Drag across adjacent words to select an entire sentence across lines.
3. **Understand**: Lift your thumb. A clean explanation card slides up with definitions, pronunciation, and context-aware meaning.
4. **Ask Anything**: Need more clarity? Type directly into the follow-up chat at the bottom of the card to ask the AI questions about what you just read.

---

## Highlights

- **True In-Context Understanding**: Fleench does not just spit out textbook definitions. It reads the surrounding conversation so it can tell you what a word actually means in that specific situation.
- **Slang and Culture Aware**: From standard vocabulary to fast-evolving internet terms, slang, portmanteaus, and acronyms, Fleench understands modern language and never refuses to explain.
- **Multi-Line Selection**: Dragging smoothly selects sentences even when they wrap onto the next line.
- **Jitter-Free Keyboard Chat**: Ask follow-up questions with a full on-screen keyboard that slides the card upward smoothly without blocking your view.
- **Non-Intrusive Edge Resting**: When you are not using it, Fleench hugs the side bezel as a minimal edge tab so your screen stays completely clear. You can customize its resting side, vertical position, and peek width in settings.
- **Offline Typography**: Uses custom Outfit fonts bundled directly inside the APK for crisp readability without external font downloads.

---

## Privacy by Design

Fleench relies on Android's native Accessibility Service framework for one reason: Android does not allow an app to inspect text in other apps without Accessibility permission.

- Fleench only inspects text when you actively touch the bubble and point at a word.
- It never monitors background typing or passwords.
- It never logs or sells your personal activity. Your reading stays yours ;)

---

## Getting Started

### Prerequisites

- An Android device running Android 9.0 (Pie / API 28) or higher.
- Ready to go out of the box — zero account creation or setup required.

### Installation

1. Download the latest `Fleench-debug.apk` from the [Releases](https://github.com/ogboNoble001/fleench/releases) section.
2. Open the APK on your Android device and install it.
3. Open the **Fleench** app.
4. Customize your bubble size, resting side, or peek width if desired.
5. Tap the toggle to enable the Fleench Accessibility Service.
   *(On Android 13+, if the system restricts the service, go to Settings &gt; Apps &gt; Fleench &gt; tap the three dots in the top-right &gt; choose "Allow restricted settings", then turn the service on).*

---

## Building from Source

To compile the APK yourself using Android Studio or the command line:

```bash
# Clone the repository
git clone https://github.com/ogboNoble001/fleench.git
cd fleench/WordExplainer/WordExplainer

# Build debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Technical Architecture

- **Platform**: 100% Kotlin, targeting Android 14 (API 34) with backward compatibility to Android 9 (API 28).
- **Overlay Window**: Custom `TYPE_ACCESSIBILITY_OVERLAY` managed through `WindowManager` with zero third-party UI dependencies.
- **Touch & Hit-Testing**: Y-weighted Euclidean distance snapped selection engine with reading-order node filtering.
- **Layout Animation**: Hardware-accelerated `WindowInsetsAnimation.Callback` synchronized with the Android soft input method (IME) for smooth keyboard handling.
- **AI Engine**: Tiered inference pipeline with sub-second response times and automated fallback.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
