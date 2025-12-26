# SIKFontManager

[![](https://jitpack.io/v/SilverIceKey/SIKFontManager.svg)](https://jitpack.io/#SilverIceKey/SIKFontManager)

**SIKFontManager** 是一个用于 Android 应用的全局字体管理器。它允许你在应用程序中动态设置和更新所有继承自 `TextView` 的控件的字体，同时提供 Jetpack Compose 支持。

## 特性

- **全局字体设置**：通过简单的配置，可以在应用程序中全局设置字体。
- **动态字体更新**：在应用运行时动态更改字体，所有活动（`Activity`）会立即更新。
- **Jetpack Compose 支持**：通过 `ProvideFontManager` 包裹你的 Compose UI，
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

## 在传统 View 系统中使用

```FontManager``` 会自动应用你在 ```AndroidManifest.xml``` 中配置的字体到所有继承自 ```TextView``` 的控件。如果你想
动态更新字体，可以调用 ```setDefaultFont``` 方法：

```kotlin
// 动态更新字体
FontManager.setDefaultFont(context, "fonts/new_font.ttf", FontSourceTypeEnums.ASSETS)
```

## 在 Jetpack Compose 中使用

Compose 与传统视图不同，不再基于 ```TextView```，因此无法通过遍历视图树来替换字体。为了在 Compose 中
应用全局字体，库提供了 ```ProvideFontManager``` 组合函数。只需在 ```setContent``` 中用它包裹你的 UI，即可
将默认字体应用到作用域内所有 ```Text``` 组件：

```kotlin
setContent {
    // 包裹整个 Compose 层，使默认字体生效
    ProvideFontManager {
        // 其他 MaterialTheme 或 UI 内容
        MaterialTheme {
            // 你的 Compose UI
        }
    }
}
```


```ProvideFontManager``` 会从 ```FontManager``` 中获取默认 ```Typeface```，转换为 Compose 的 ```FontFamily```，并通过
```LocalTextStyle``` 覆盖当前文本样式，使所有未显式指定字体的 ```Text``` 组件使用该字体。

## 注意事项

确保在调用 ```ProvideFontManager``` 之前已经通过 ```FontManager.init(context)``` 或 ```setDefaultFont``` 设置了
默认字体，否则该组合函数将直接渲染内容而不会做任何替换。

当 Compose 中的 ```Text``` 显式指定 ```fontFamily``` 时，```ProvideFontManager``` 不会覆盖该字体。

- **字体类型**：确保在 `fontType` 中指定的类型与 `fontSource` 的实际存放位置匹配。
- **弱引用管理**：`SIKFontManager` 使用弱引用来管理 `Activity`，以防止内存泄漏。

