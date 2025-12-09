# Bapel Slimefun Addon 🚀

![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric-Modloader-CAA698?style=for-the-badge&logo=fabric&logoColor=white)
![Slimefun](https://img.shields.io/badge/Slimefun-Addon-brightgreen?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Active_Development-blue?style=for-the-badge)

> **High-performance automation extension for Slimefun 4.**
> Dirancang dengan fokus pada efisiensi server dan optimasi logika *ticking*.

---

## 📖 Overview

**Bapel Slimefun** adalah addon kustom untuk server Minecraft (Fabric/Paper) yang memperluas fungsionalitas Slimefun. Proyek ini dibangun untuk mengatasi batasan otomatisasi standar dengan memperkenalkan mesin dan item baru yang lebih cerdas dan hemat sumber daya (TPS-friendly).

Fokus utama pengembangan adalah:
1.  **Optimasi:** Menggunakan algoritma efisien untuk meminimalkan dampak pada *main thread* server.
2.  **Modularitas:** Kode disusun agar mudah diperbarui (maintainable) dan dikembangkan.
3.  **Integrasi:** Memanfaatkan API Slimefun dan Mixin untuk integrasi yang mulus.

## ✨ Key Features

* **Custom Slimefun Machines:** Mesin otomatisasi baru dengan GUI interaktif.
* **Performance First:** Logika penanganan event dan ticking yang dioptimalkan.
* **Mixin Implementation:** Modifikasi mendalam pada behavior vanilla/Slimefun untuk fitur tingkat lanjut.
* **Configurable:** Hampir semua aspek dapat diatur melalui file konfigurasi.

## 🛠️ Technical Stack

Proyek ini dibangun menggunakan teknologi berikut:

| Komponen | Deskripsi |
| :--- | :--- |
| **Language** | Java 21 |
| **Build Tool** | Gradle (Kotlin DSL) |
| **Platform** | Fabric / PaperMC |
| **Dependency** | Slimefun4 API, Dough API |

## 🚀 Installation & Setup

1.  Pastikan server Anda menjalankan **Java 21**.
2.  Install **Slimefun4** versi terbaru.
3.  Download rilis terbaru `bapel-slimefun.jar` dari halaman [Releases](#).
4.  Masukkan ke folder `/plugins` atau `/mods`.
5.  Restart server.

## 💻 Development (Building from Source)

Jika Anda ingin berkontribusi atau memodifikasi kode:

```bash
# Clone repository
git clone [https://github.com/username/bapel-slimefun.git](https://github.com/username/bapel-slimefun.git)

# Masuk ke direktori
cd bapel-slimefun

# Build dengan Gradle
./gradlew build
