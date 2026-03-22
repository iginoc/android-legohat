# Legohat

Legohat is an Android application designed to securely manage, browse, and interact with files on a remote PC or Raspberry Pi via SSH/SFTP. It is specifically optimized for controlling LEGO® motors using the Raspberry Pi Build HAT.

## Features

- **Secure SSH/SFTP Connection**: Connect to any remote device using IP address, username, and password.
- **Remote File Management**:
    - **Browsing**: List and view files in a terminal-style list.
    - **Editing**: Built-in text editor to modify remote scripts directly from the app.
    - **Execution**: Run Python scripts (using `python -u`) or binaries with real-time shell output.
- **Real-time Terminal Output**: A dedicated console view with automatic scrolling and word wrap to monitor your program's output.
- **Build HAT Remote Control**: Automatic full-screen remote control interface when running `remote.py`, featuring:
    - Independent control for 4 motors (A, B, C, D).
    - Mapped keys for movement: `q/a`, `w/s`, `e/d`, `r/f`.
    - **Quick Stop**: Dedicated "OFF" button (key `z`) to halt operations.
    - Interactive xterm-compatible terminal emulation.
- **Encrypted Storage & Favorites**: 
    - Securely stores credentials using Android's `EncryptedSharedPreferences` (AES-256-GCM).
    - **Favorites Tab**: Quickly switch between previously used servers with one tap.
- **Modern UI**: Adaptive navigation and Material 3 design.

## Hardware Integration

This app is designed to work seamlessly with the **Raspberry Pi Build HAT**, which allows you to control LEGO® Technic™ motors and sensors from your Raspberry Pi.

- **Learn more about the Build HAT**: [https://www.raspberrypi.com/products/build-hat/](https://www.raspberrypi.com/products/build-hat/)

## Prerequisites

- Android 13 (API 33) or higher.
- A remote device (e.g., Raspberry Pi) with SSH/SFTP server enabled.
- For motor control: Raspberry Pi Build HAT and compatible LEGO motors.

## Tech Stack

- **Kotlin** & **Jetpack Compose**.
- **Material 3**: Design system.
- **JSch**: SSH2 protocol implementation with PTY support.
- **Android Security Crypto**: For hardware-backed credential encryption.
- **Coroutines**: For asynchronous networking.

## Installation

1. Clone this repository.
2. Open the project in Android Studio.
3. Build and run the app on your Android device.

## Configuration

1. Go to the **Profile** tab.
2. Enter your device's IP, username, password, and the path to your scripts.
3. Connect once to save the server to your **Favorites**.
4. To use the remote control, ensure your script is named `remote.py` and uses raw input/tty for keyboard commands.

## License

This project is for educational and personal use. LEGO® is a trademark of the LEGO Group.
