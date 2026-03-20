# Legohat

Legohat is an Android application designed to securely manage and browse files on a remote PC via SSH/SFTP. It features a modern Jetpack Compose UI with adaptive navigation and encrypted credential storage.

## Features

- **Secure SSH/SFTP Connection**: Connect to any remote PC using IP address, username, and password.
- **Remote File Browsing**: Specify a remote path to list and view files in a scrollable, terminal-style list.
- **Encrypted Storage**: All credentials (IP, username, password, and path) are stored securely using Android's `EncryptedSharedPreferences` (AES-256-GCM).
- **Modern UI**: Built with Jetpack Compose and Material 3, including an adaptive navigation suite.
- **Persistence**: Configuration is automatically saved upon successful connection and reloaded when the app starts.

## Prerequisites

- Android 13 (API 33) or higher.
- A remote PC with an SSH/SFTP server enabled.
- Both devices must be on the same network (unless using a public IP/VPN).

## Tech Stack

- **Kotlin**: Primary programming language.
- **Jetpack Compose**: For the declarative UI.
- **Material 3**: Design system.
- **JSch**: Java library for SSH2 protocol.
- **Android Security Crypto**: For encrypted data storage.
- **Coroutines**: For asynchronous network operations.

## Installation

1. Clone this repository.
2. Open the project in Android Studio (Ladybug or newer recommended).
3. Build and run the app on an emulator or physical device.

## Configuration

Go to the **Profile** tab to enter your SSH details:
- **PC IP Address**: The local or public IP of your target machine.
- **Username**: Your SSH username.
- **Password**: Your SSH password.
- **Remote Path**: The directory you want to list (e.g., `/home/username/`).

## License

This project is for educational/personal use. Please ensure you have permission to connect to the remote devices you configure.
