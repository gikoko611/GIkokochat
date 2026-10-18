<!-- README.md --><div align="center"><img src="https://capsule-render.vercel.app/api?type=waving&color=0:080808,50:3D2B13,100:D4AF37&height=220&section=header&text=G.I%20KO%20KO%20CHAT&fontSize=42&fontColor=FFD700&fontAlignY=38&desc=YOUR%20AI%20%E2%80%A2%20YOUR%20WAY&descAlignY=58&descSize=16&animation=fadeIn" width="100%" alt="G.I KO KO CHAT — Your AI, Your Way"/>G.I KO KO CHAT

✨ Your AI. Your Way. Your Intelligence.

A modern AI chat experience with a Midnight Gold interface, customizable AI providers, and an Android-first vision.

<p>
<a href="https://github.com/gikoko611/GIkokochat"><img src="https://img.shields.io/badge/Project-G.I%20KO%20KO%20CHAT-D4AF37?style=for-the-badge&logo=github&logoColor=white" alt="Project"/></a>
<a href="https://github.com/gikoko611/GIkokochat/stargazers"><img src="https://img.shields.io/github/stars/gikoko611/GIkokochat?style=for-the-badge&color=D4AF37&logo=starship&logoColor=white" alt="Stars"/></a>
<a href="https://github.com/gikoko611/GIkokochat/network/members"><img src="https://img.shields.io/github/forks/gikoko611/GIkokochat?style=for-the-badge&color=8B7355&logo=github" alt="Forks"/></a>
<a href="https://github.com/gikoko611/GIkokochat/issues"><img src="https://img.shields.io/github/issues/gikoko611/GIkokochat?style=for-the-badge&color=gold" alt="Issues"/></a>
</p><p>
<a href="#-features">Features</a> •
<a href="#-preview">Preview</a> •
<a href="#-installation">Installation</a> •
<a href="#-ai-provider-setup">AI Setup</a> •
<a href="#-roadmap">Roadmap</a>
</p></div>---

📖 About the Project

G.I KO KO CHAT is an AI chat project focused on a clean, premium-looking interface and flexible AI provider configuration.

The project includes a lightweight web prototype and a native Android development project. Its long-term goal is to make AI tools easier to access and customize from mobile devices.

Whether you're experimenting with AI models, connecting compatible API providers, or exploring Android app development, G.I KO KO CHAT provides a foundation to build upon.

«Project status: Early development. Some features are interface prototypes or planned improvements, not yet fully implemented.»

✨ Features

<table>
<tr>
<td width="50%">🧠 Flexible AI Providers

Configure compatible AI API endpoints, API keys, and model IDs.

</td>
<td width="50%">🎨 Midnight Gold UI

A dark interface with elegant gold accents and a premium visual identity.

</td>
</tr>
<tr>
<td width="50%">💬 Chat Interface

A conversational UI designed for interacting with AI models.

</td>
<td width="50%">🔀 Model Selection

Choose a configured model from the available provider settings.

</td>
</tr>
<tr>
<td width="50%">📎 Attachment Interface

Select images, PDFs, documents, audio, video, and other files in the web prototype.

</td>
<td width="50%">📱 Android Development

A native Android project built with Kotlin and Jetpack Compose.

</td>
</tr>
</table>Additional project goals

- ⚙️ Custom system prompts and preferences
- 🔑 Provider and API key management
- 🌗 Theme customization
- 🗂️ Improved chat history and persistence
- 📄 Document-aware AI conversations
- 🚀 A more complete mobile AI experience

Items described as goals or interface features may require further development before they work end to end.

---

🖼️ Preview

<div align="center"><img src="https://capsule-render.vercel.app/api?type=rect&color=0:111111,100:33260D&height=100&section=header&text=MIDNIGHT%20GOLD&fontSize=28&fontColor=FFD700&fontAlignY=55" width="100%" alt="Midnight Gold design concept"/>Dark interface. Golden identity. AI without unnecessary complexity.

</div>«Screenshots coming soon: Add genuine screenshots of the running web app and Android app here when available. The banner above is decorative branding, not an application screenshot.»

To add your screenshots later, upload them to a folder such as "docs/images/" and insert Markdown image links:

![G.I KO KO CHAT — Home Screen](docs/images/home-screen.png)

![G.I KO KO CHAT — Chat Screen](docs/images/chat-screen.png)

---

🛠️ Technology Stack

Component| Technology
Web prototype| HTML, CSS, JavaScript
Android application| Kotlin
Android UI| Jetpack Compose
Android build system| Gradle
AI integration| OpenAI-compatible Chat Completions API
Local web server| Python HTTP server
Source control| Git & GitHub
Automated Android builds| GitHub Actions workflow

The web prototype and Android application are separate parts of the project. Features available in one should not be assumed to exist in the other.

---

📥 Installation

Option 1 — Run the Web Prototype on Android

You can run the web prototype using Termux without installing Node.js or npm packages.

Requirements

- Android phone
- Termux
- Python 3

Step 1: Update Termux

pkg update && pkg upgrade

Step 2: Install Python and Git

pkg install python git

Step 3: Clone the repository

git clone https://github.com/gikoko611/GIkokochat.git

Step 4: Open the project directory

cd GIkokochat

Step 5: Start the local server

python -m http.server 8080

Step 6: Open the application

Visit the following address in your phone browser:

http://127.0.0.1:8080

To stop the server, press "CTRL + C" in Termux.

«Run the server from the directory containing "index.html". Do not expose the local server publicly without appropriate security controls.»

Option 2 — Build the Native Android Project

The repository also includes an Android project.

1. Clone or download the repository.
2. Open the project in a compatible Android development environment.
3. Allow Gradle to synchronize.
4. Check the Android SDK and build configuration.
5. Build and test the application.

The exact Android build requirements depend on the project's current Gradle configuration. The Android starter is still under development; a complete production-ready application is not guaranteed.

---

🔌 AI Provider Setup

The web prototype is designed to work with AI providers that expose a compatible Chat Completions API.

Configuration steps

1. Launch the web prototype.
2. Open API Keys.
3. Select Add Provider.
4. Enter the provider name.
5. Enter the provider's API base URL.
6. Add your API key and model ID.
7. Select the configured provider and test a text message.

Example configuration

Setting| Example
Provider name| My AI Provider
API base URL| Your provider's documented API base URL
API key| Your own API key
Model ID| A model ID supported by that provider

The web prototype uses an OpenAI-compatible Chat Completions request format. Providers that use different endpoints or request formats may require additional adapters.

Important: Successful requests depend on valid credentials, model availability, provider policies, network connectivity, quotas, and browser CORS support.

Never publish your API keys in screenshots, source code, Git commits, or public configuration files.

---

🔐 Security & Privacy

Security is an important part of building a reliable AI application.

The current web prototype has limitations that must be understood:

- API keys are stored in browser "localStorage" and are not encrypted by the prototype.
- Browser storage is not a secure secret vault.
- Direct API calls may expose credentials to the browser environment and can fail because of provider CORS restrictions.
- Selecting a file does not mean its contents are automatically extracted and processed by an AI model.
- A production implementation should use an authenticated backend proxy, appropriate access controls, and secure secret handling.
- The Android application should use Android Keystore-backed encryption for sensitive credentials where appropriate.

Do not use the current prototype to store sensitive credentials on shared or untrusted devices.

---

🚧 Current Limitations

G.I KO KO CHAT is an evolving project, and not every feature is fully implemented.

Feature| Current status
Web UI and chat interface| Prototype available
Compatible AI provider configuration| Prototype implementation
Model selection| UI/configuration support
Browser settings persistence| Basic local storage
Encrypted web API key storage| Not implemented
PDF and document content extraction| Not implemented
Image and vision processing| Not implemented
Audio and video AI processing| Not implemented
Resumable file uploads| Not implemented
Production backend security| Requires further development
Pro subscriptions and billing| Not implemented

This table distinguishes the existing prototype from the intended future product. Feature availability may change as development progresses.

---

🗺️ Roadmap

The following items describe development goals, not promises of completed functionality.

- [x] Create the initial web interface
- [x] Add a configurable AI provider interface
- [x] Add a basic model selector
- [x] Establish a native Android project
- [ ] Improve chat state and history persistence
- [ ] Add secure Android API key storage
- [ ] Implement backend-mediated AI requests
- [ ] Add PDF and document text extraction
- [ ] Implement image/vision support
- [ ] Improve error handling and request status
- [ ] Add real application screenshots and documentation
- [ ] Improve testing, accessibility, and release automation

---

🤝 Contributing

Contributions, bug reports, ideas, and improvements are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes.
4. Test your changes.
5. Open a Pull Request with a clear explanation.

For bug reports and feature requests, visit:

"GitHub Issues" (https://github.com/gikoko611/GIkokochat/issues)

Please do not include API keys, passwords, private user data, or other secrets in issues or pull requests.

---

👨‍💻 Developer

<div align="center"><img src="https://github.com/gikoko611.png" width="100" height="100" alt="G.I KO KO developer profile" style="border-radius:50%;"/>G.I KO KO

AI • Android • Open Source

""GitHub" (https://img.shields.io/badge/GitHub-gikoko611-181717?style=for-the-badge&logo=github)" (https://github.com/gikoko611)

"Explore G.I KO KO CHAT" (https://github.com/gikoko611/GIkokochat)

</div>---

📜 License

Please check the repository's license file before redistributing, modifying, or using this project in another product.

If no license has been added, the project should not be assumed to have an open-source license merely because its repository is public.

---

<div align="center">✨ G.I KO KO CHAT

Your AI. Your Way. Your Intelligence.

Built with ambition. Evolving with every improvement.

<a href="https://github.com/gikoko611/GIkokochat">View Repository</a> ·
<a href="https://github.com/gikoko611/GIkokochat/issues">Report a Bug</a> ·
<a href="https://github.com/gikoko611">Follow Developer</a>

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:080808,50:3D2B13,100:D4AF37&height=100&section=footer" width="100%" alt="Gold footer banner"/></div>
