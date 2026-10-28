幻迹 GPS (HuanJi-GPS) 
基于 Android 原生 SDK 与百度地图开发的高保活、高精度虚拟定位工具。
核心特性
双引擎高德/百度底图支持：高精度选点与无缝坐标系纠错转换（WGS-84 / GCJ-02 / BD-09）。
双轨运行架构：
常规模式：免 Root 依赖，基于 Android 官方开发者模拟接口，适配主流机型。
Pro 极客模式：集成 Shizuku / ADB 底层提权探针，支持深度系统调用。
智能权限测谎雷达：内置 `unsafeCheckOp` 底层安全检测，精准识别模拟权限与后台拦截状态。
前台高保活服务：自定义常驻通知栏守护引擎，防止系统后台无故查杀。
常用位置收藏库：基于本地 SQLite 轻量数据库，支持快速保存、读取、一键直达常去坐标。
技术栈
开发语言：Java / Android SDK
地图内核：Baidu Maps SDK
提权与框架：Shizuku API / Android IPC Binder
数据持久化：SQLite Database
构建工具：Gradle (Kotlin DSL)
免责声明 (Disclaimer)
本项目仅供 Android 系统底层架构学习、位置服务开发测试及极客技术研究使用，请勿用于任何商业反作弊对抗或非法用途。
