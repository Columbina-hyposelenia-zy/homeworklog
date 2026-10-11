# HomeworkLog

离线 Android 作业记录应用，支持截止提醒、科目管理、图片附件和完成历史。

当前版本为 **0.3.1（24）**，包名 `com.zy.homeworklog`。最低支持 Android 10（API 29），编译和目标版本为 Android 14（API 34）。

## 开发与构建

使用 Android Studio 打开本目录，配置 JDK 21（最低 17）和 Android SDK 34，然后同步 Gradle。首次同步需要下载依赖。本机 SDK 路径写在 `local.properties` 中，不提交到仓库。

技术栈：Kotlin 2.0.20、Jetpack Compose、Material 3、Room 2.6.1、Navigation Compose 和 AlarmManager。构建工具为 AGP 8.5.2、Gradle Wrapper 8.9。

Windows 下在本目录执行：

```powershell
.\gradlew.bat :app:assembleRelease
```

macOS / Linux 使用 `bash ./gradlew :app:assembleRelease`。APK 输出到 `app/build/outputs/apk/release/app-release.apk`。版本号在 `gradle.properties` 中设置。

Release 构建开启 R8 和资源裁剪，目前使用本机调试密钥签名。覆盖安装需要使用相同密钥，可通过 `-PdebugKeystorePath=密钥绝对路径` 指定。正式发布前应配置自己的发布签名；源码不包含密钥。

## 项目结构

源码位于 `app/src/main/java/com/zy/homeworklog/`：

| 目录 | 内容 |
| --- | --- |
| `data` | Room 表、数据库迁移、数据访问、草稿和附件管理 |
| `ui` | Compose 页面、卡片、图片查看器、滚轮和手势 |
| `reminder` | 提醒调度、通知和系统广播处理 |
| `recurring` | 周期作业生成 |
| `util` | 时间格式化和星期计算 |

资源位于 `app/src/main/res/`，JVM 测试位于 `app/src/test/`，Android 附件测试位于 `app/src/androidTest/`。

## 功能与规则

- 作业按截止时间排序，支持新增、编辑、完成和删除。科目可设置名称和颜色，作业类型分纸质与电子。
- 纯文字记录最多同时展开三条，超过后收起最早的一条。含图记录展开时收起其他记录；展开纯文字记录也会收起含图记录。
- 相册或拍照导入的图片保留原始字节，副本存放在应用私有目录。每条最多九张，单张上限 32MiB。查看器支持滑动、双指缩放和双击放大。
- 编辑时移除图片只修改待保存清单，保存成功后再清理旧附件；取消编辑保留原记录。完成历史保留附件，删除记录时清理不再被引用的图片。
- 添加页保留一份草稿，返回后仍可恢复；保存成功时清空，距最后保存超过 24 小时后失效。编辑已有记录不使用这份草稿。异常退出遗留的无引用图片超过 24 小时后回收。
- 未完成作业逾期满十天自动清理，已完成历史不参与清理。
- 提醒使用全局提前量或单条指定时刻。只安排最近一次闹钟，处理后重排下一次；数据变化、开机、覆盖安装及系统时间变化也会重排。逾期作业不再提醒，仅编辑正文或图片不会重复提醒。
- 周期规则每周最多生成一条实例，截止时间为从当天起最近一个所选星期的次日零点；未选有效星期时使用下周一零点。有未完成实例时跳过本周，之后完成也不在本周补生成。新实例不复制图片。目前不支持指定结束日期或跳过某一周。
- 支持深浅色切换。应用不申请网络权限，数据保存在本机私有目录，关闭系统应用数据备份。

## 测试

```powershell
.\gradlew.bat :app:testReleaseUnitTest
.\gradlew.bat :app:lintRelease
```

JVM 测试覆盖草稿、日期、星期计算、展开策略和提醒时刻。`AttachmentSmokeInstrumentation` 使用独立内存数据库、文件目录和偏好设置检查附件流程，需要连接设备运行；它直接继承 `android.app.Instrumentation`，不生成标准测试报告。

Android 13 及以上需要通知权限；未授权时不安排提醒。相册、拍照、图片手势、后台提醒和升级后数据保留仍需真机回归。

## 许可

[MIT](LICENSE)
