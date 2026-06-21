# 👗 AI Android Outfit Maker

An Android app built as a learning project to explore **vibe-coding** with Claude, with no prior mobile development experience.

## ✨ Features

- 📷 **Photo capture** — take a photo while wearing the garment or pick from gallery
- ✂️ **Smart garment isolation** — automatically isolates the garment from the photo using OpenCV GrabCut + Remove.bg, no flat lay required
- 🤖 **AI outfit generation** — clothing analysis and styled outfit suggestions powered by Gemini, with style selector (casual chic, rock, bohème, Y2K...)
- 👗 **Virtual wardrobe** — save isolated garment images locally, organized by category
- 🗑️ **Wardrobe management** — add, categorize and delete items
- 🔄 **Generate outfits from wardrobe** — create outfit suggestions directly from saved items, no photo needed

## 🛠️ Tech Stack

- **Language** : Kotlin
- **UI** : Jetpack Compose
- **Navigation** : Navigation Compose
- **Networking** : Ktor
- **Image loading** : Coil 3
- **Computer Vision** : OpenCV (GrabCut segmentation)
- **Local storage** : SharedPreferences + kotlinx.serialization
- **Outfit generation AI** : Google Gemini 2.5 Flash API
- **Background removal** : Remove.bg API

## 🤖 Vibe-coding with Claude

This project was entirely built through vibe-coding, with Claude guiding every step: project setup, navigation, API integration, computer vision, error handling, API key security.

## 🚀 Getting Started

1. Clone the repo
2. Create a `local.properties` file at the root with:

```bash
GEMINI_API_KEY=your_gemini_key
REMOVE_BG_API_KEY=your_remove_bg_key
REPLICATE_API_KEY=your_replicate_key
```

3. Get a Gemini API key at [Google AI Studio](https://aistudio.google.com/apikey)
4. Get a Remove.bg API key at [Remove.bg](https://www.remove.bg/api)
5. Run the app on an Android device (API 26+)

## 📱 Screenshots

*Coming soon*

## ⚠️ Known limitations

- Garment isolation works best with good lighting and contrasted backgrounds
- Remove.bg free tier is limited to 50 images/month
- White or very light garments may require better lighting conditions