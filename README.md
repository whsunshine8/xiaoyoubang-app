# 🎓 校友帮 (CampusHelper) — 官方 Android 原生客户端

> **高校学籍档案代办 · 免下载在线扫码电子手签 · 类似旺旺/闲鱼的端到端即时沟通 (IM) 闭环系统**

---

## 🌟 核心特性与架构亮点

### 1. 💬 订单内置端到端即时沟通 (IM)
- **拒绝私聊与脱单跳单**：学生与校园官方认证代办员直接在订单沟通室交流，无需互加微信或 QQ，杜绝私下转账与跑单纠纷。
- **现场窗口拍照直传**：代办员到达教务处/档案馆窗口后，可一键调用相机拍照并等比压缩秒传，学生实时核对盖章与密封情况。
- **快捷话术胶囊**：内置代办常用场景话术（`🏫 我已到达教务处窗口`、`📸 现场窗口照片请核对`、`📦 档案已密封寄出` 等）。
- **未读消息红点与双端同步**：支持实时未读数提醒与历史存证仲裁。

### 2. ✍️ 免下载在线扫码电子手签与防伪合成
- **全流程无纸化**：学生在电脑端或手机端核对委托书与申请表声明，无需下载 Word/PDF 模板打印。
- **跨端扫码签名**：手机扫码即在 Canvas 画板亲笔签名，自动生成带高精度防伪时间戳水印的正式凭证；
- **受托人动态回填**：接单后系统自动结合认证代办员实名信息合成正式法律效力授权书。

### 3. 🏫 多校区精准联动
- 覆盖全国高校与多校区分院，支持精准匹配常驻代办员，提高办理时效。

---

## 📱 客户端快速下载

- 🚀 **国内高速专线直链**：[https://www.cnkiedu.cn/download/CampusHelper_v1.0.0.apk](https://www.cnkiedu.cn/download/CampusHelper_v1.0.0.apk)
- 🌐 **平台官方网站**：[https://www.cnkiedu.cn](https://www.cnkiedu.cn) / [https://www.whsunshine.link](https://www.whsunshine.link)

---

## 🛠️ 本地编译与构建指南

### 环境要求
- **JDK**: OpenJDK 17 或 21
- **Android SDK**: `compileSdk 34`, `targetSdk 34`, `minSdk 24`
- **Gradle**: 8.5+

### 构建 Release 安装包
```bash
# 克隆仓库
git clone https://github.com/whsunshine8/xiaoyoubang-app.git
cd xiaoyoubang-app

# 编译 Release APK
./gradlew assembleRelease

# 使用 Android SDK 签名 (支持 V1 + V2 + V3 方案)
zipalign -v -p 4 app/build/outputs/apk/release/app-release-unsigned.apk CampusHelper_v1.0.0_aligned.apk
apksigner sign --ks campushelper.keystore --ks-key-alias campushelper CampusHelper_v1.0.0_aligned.apk
```

---

## 📄 开源许可证
本项目遵循 MIT 协议开源。
