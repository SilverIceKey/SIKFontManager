# SIKFontManager

[![](https://jitpack.io/v/SilverIceKey/SIKFontManager.svg)](https://jitpack.io/#SilverIceKey/SIKFontManager)

**SIKFontManager** 是一个用于 Android 应用的全局字体管理器。它允许你在应用程序中动态设置和更新所有继承自 `TextView` 的控件的字体，无需修改现有代码。

## 特性

- **全局字体设置**：通过简单的配置，可以在应用程序中全局设置字体。
- **动态字体更新**：在应用运行时动态更改字体，所有活动（`Activity`）会立即更新。
- **低侵入性**：无需修改现有的 `TextView`，通过生命周期回调自动应用字体。
- **防止内存泄漏**：使用弱引用管理 `Activity`，确保不会引起内存泄漏。

## 安装

将 `SIKFontManager` 添加到你的项目中：

1. **引入依赖**：

   在你的项目中引入 `SIKFontManager`。目前假设你将其作为模块或库引入，如果是发布到Maven或其他仓库，请根据实际情况调整。

   ```groovy
   implementation 'com.github.SilverIceKey:SIKFontManager:Tag'
   ```

2. **在`AndroidManifest.xml`中配置字体**：

   在应用的 `AndroidManifest.xml` 中添加以下 `meta-data` 标签来配置字体来源和类型：

   ```xml
   <application
       android:name=".YourApplication"
       ... >
       <meta-data
           android:name="fontSource"
           android:value="fonts/custom_font.ttf" /> <!-- 字体路径 -->
       <meta-data
           android:name="fontType"
           android:value="assets" /> <!-- 字体来源类型，可以是 'assets', 'res', 'file' 等 -->
   </application>
   ```

3. **在`Application`类中初始化**：  

   在你的 `Application` 类的 `onCreate` 方法中初始化 `SIKFontManager`：

   ```kotlin
   class YourApplication : Application() {
           override fun onCreate() {
           super.onCreate()
           SIKFontManager.init(this)
       }
   }
   ```

## 使用

`SIKFontManager` 会自动应用你在 `AndroidManifest.xml` 中配置的字体到所有继承自 `TextView` 的控件。如果你想动态更新字体，可以调用 `setDefaultFont` 方法：

```kotlin
SIKFontManager.setDefaultFont(this, "fonts/new_font.ttf", "assets")
```

## 注意事项

- **字体类型**：确保在 `fontType` 中指定的类型与 `fontSource` 的实际存放位置匹配。
- **弱引用管理**：`SIKFontManager` 使用弱引用来管理 `Activity`，以防止内存泄漏。

