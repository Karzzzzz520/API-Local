# API Proxy - 本地 API Key 代理转换工具

![Platform](https://img.shields.io/badge/platform-Android-brightgreen)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![License](https://img.shields.io/badge/license-MIT-orange)

在手机上运行本地 HTTP 代理服务，将 API 请求转发到 OpenAI / Gemini / Claude / DeepSeek 等服务商，**自动注入 API Key**，让你在其他 App 中只需配置本地地址即可调用云端 API。

---

## ✨ 功能

- 🔄 **本地 HTTP 代理服务器** — 运行在手机上的轻量级代理
- 🔑 **自动注入 API Key** — 无需在各个 App 单独配置 Key
- 📡 **支持 SSE 流式响应** — 兼容流式 AI 对话
- 🎨 **Material You 风格** — 适配 Android 12+ 动态取色
- ⚙️ **预设 4 大服务商** — OpenAI / Gemini / Claude / DeepSeek，开箱即用
- ➕ **自定义服务商** — 支持任意 API 兼容的服务商
- 📋 **实时转发日志** — 监控请求状态
- 🖼️ **内置服务商图标** — OpenAIGPT、Gemini、Claude、DeepSeek 专属 Logo

## 📥 下载

从 [GitHub Releases](https://github.com/Karzzzzz520/API-Local/releases) 下载最新 APK 直接安装。

## 🚀 快速开始

### 1. 添加服务商

打开 App，点击右下角 ➕ 按钮添加 API 服务商：

| 服务商 | 预设 Base URL |
|--------|--------------|
| OpenAI | `https://api.openai.com` |
| Gemini | `https://generativelanguage.googleapis.com` |
| Claude | `https://api.anthropic.com` |
| DeepSeek | `https://api.deepseek.com` |

### 2. 填入 API Key

在服务商编辑页面粘贴你的 API Key，点击保存。

### 3. 开启代理

点击顶部开关启动代理服务。每个已启用的服务商会生成一个本地端点：

```
http://localhost:8080/openai
http://localhost:8080/gemini
http://localhost:8080/claude
http://localhost:8080/deepseek
```

### 4. 在其他 App 中使用

将其他 App 的 API Base URL 设置为 `http://localhost:8080`，即可通过本代理转发请求。

## 🏗️ 技术栈

- **语言**: Java
- **网络**: OkHttp + NanoHTTPD
- **UI**: Material 3 (Material You) + RecyclerView
- **构建**: Gradle + AGP
- **最低支持**: Android 8.0 (API 26)
- **目标 SDK**: Android 16 (API 36)

## 🛠️ 本地构建

```bash
# Clone 仓库
git clone https://github.com/Karzzzzz520/API-Local.git
cd API-Local

# 使用 Gradle 构建 Release APK
./gradlew assembleRelease
```

编译后的 APK 位于 `build/outputs/apk/release/`。

## 📜 更新日志

### v1.2.1
- 🖼️ 更新内置服务商图标，更清晰美观
- 🐛 修复若干 UI 细节

### v1.2.0
- 🔄 用 NanoHTTPD 替换手写 ServerSocket，HTTP 解析更稳定
- 🔑 正确转发 API 请求并注入 Key
- 📡 支持 SSE 流式响应
- 🎯 路径路由：`/openai/` `/gemini/` `/claude/` `/deepseek/`
- 🏠 默认路由到第一个有 Key 的 provider
- 🔐 Kars.jks 正式签名

### v1.0.0
- 🔄 首个版本，基础 HTTP 代理服务器
- 🔑 自动注入 API Key
- ⚙️ 预设 OpenAI/Gemini/Claude/DeepSeek 配置
- 📋 实时转发日志

## 📄 许可证

本项目基于 MIT 许可证开源。
