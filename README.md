# 👗 AI Android Outfit Maker

An Android app built as a learning project to explore **vibe-coding** with Claude, with no prior mobile development experience.

## ✨ Features

- 📷 **Photo capture** — take a photo or pick from gallery
- 🤖 **AI outfit generation** — clothing analysis and outfit suggestions powered by Gemini
- 🖼️ **Clean image generation** — generates a neat product-style photo of the garment on a white background (via Replicate)
- 👗 **Virtual wardrobe** — save clothing items locally
- 🗑️ **Wardrobe management** — add and delete items

## 🛠️ Tech Stack

- **Language** : Kotlin
- **UI** : Jetpack Compose
- **Navigation** : Navigation Compose
- **Networking** : Ktor
- **Image loading** : Coil 3
- **Local storage** : SharedPreferences + kotlinx.serialization
- **Outfit generation AI** : Google Gemini 2.5 Flash API
- **Image generation AI** : Replicate (Stable Diffusion XL)

## 🤖 Vibe-coding with Claude

This project was entirely built through vibe-coding, with Claude guiding every step: project setup, navigation, API integration, error handling, API key security.

## 🚀 Getting Started

1. Clone the repo
2. Create a `local.properties` file at the root with:

```bash
GEMINI_API_KEY=your_gemini_key
REPLICATE_API_KEY=your_replicate_key
```

3. Get a Gemini API key at [Google AI Studio](https://aistudio.google.com/apikey)
4. Get a Replicate API key at [Replicate](https://replicate.com)
5. Run the app on an Android device (API 26+)

## 📱 Screenshots

*Coming soon*

