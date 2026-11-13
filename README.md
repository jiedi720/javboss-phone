<div align="center">

# JavBoss Phone

**JavBoss 的第三方 Android 客户端**

面向手机、平板与横屏窗口的自托管媒体库客户端

<p>
  <img src="https://img.shields.io/badge/Android-客户端-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/版本-v0.3.73--modified-orange" alt="Version">
  <img src="https://img.shields.io/badge/架构-arm64--v8a-blue" alt="ABI">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License">
</p>

[项目主页](https://github.com/jiedi720/javboss-phone) ·
[上游项目](https://github.com/zsdcxd/javboss-phone) ·
[JavBoss 服务端](https://github.com/Solr159/JavBoss)

</div>

> 本项目是独立维护的第三方客户端，与 JavBoss 官方无关。通过 JavBoss 自托管服务端的 REST API 获取数据，不提供服务端功能。

## 项目简介

本项目 fork 自 [zsdcxd/javboss-phone](https://github.com/zsdcxd/javboss-phone)，基于上游 v0.2.1 持续迭代。

| 项目 | 当前信息 |
| --- | --- |
| 当前版本 | **v0.3.73-modified**（versionCode 77） |
| Android 包名 | `com.javboss.modified` |
| 应用名称 | JavBoss Modified |
| 发布架构 | `arm64-v8a` |
| 许可证 | GPL-3.0 |

## 核心功能

| 模块 | 功能 |
| --- | --- |
| **媒体库** | 视频与 JAV 作品库、女优 / 片商 / 系列视角、收藏夹、搜索、排序与筛选 |
| **卡片与布局** | 等高网格、独立列数设置、封面裁切还原、观看状态与喜爱度评分 |
| **播放体验** | ExoPlayer / libVLC 双引擎、竖屏悬浮小窗、手势控制、播放时长同步 |
| **详情与图片** | 多版本文件管理、样品图、截图浏览、全屏缩放与保存 |
| **网络与登录** | 多服务器预设、记住密码、一键登录、家里 / 外网自动切换 |
| **平板适配** | 根据窗口宽度切换底部导航 / 左侧导航栏，支持横屏、分屏与自由窗口 |
| **隐私与稳定性** | 封面毛玻璃、锁屏打码、崩溃日志、自动化单元测试 |

## 快速开始

### 1. 准备服务端

先在 Windows 电脑运行 [JavBoss](https://github.com/Solr159/JavBoss)，并允许手机通过局域网访问：

- **JavBoss v1.9.0 及之后**：在「全局设置」中开启「允许局域网访问」，然后重启服务端。
- **更早版本**：启动时使用 `-addr 0.0.0.0:8655` 参数。

首次启动如出现 Windows 防火墙提示，请允许专用网络访问。

### 2. 连接手机

1. 确保手机与运行 JavBoss 的电脑连接同一局域网。
2. 在电脑上运行 `ipconfig`，找到电脑的 IPv4 地址。
3. 打开客户端，在登录页填写服务端地址，例如 `http://192.168.1.10:8655`。
4. 输入 JavBoss 网页端密码并登录。

> 端口以服务端 `config.toml` 中的 `port` 为准（默认 8655）。请为服务端设置强密码，不要将未受保护的服务直接暴露到公网。

## 构建

```bash
# Debug
gradlew.bat assembleDebug

# Release（需要 keystore.properties 和 keystore/release.jks）
gradlew.bat assembleRelease
```

Release APK 输出目录：`app/build/outputs/apk/release/`。

当前 Release 仅构建 `arm64-v8a`，启用 R8 混淆与资源压缩。包名为 `com.javboss.modified`，与原版可共存安装；由于包名不同，旧版应用的数据不会自动迁移，首次安装需要重新登录和配置。

## 文档

- [服务器地址与预设规则](docs/server-address-and-presets.md)
- [JavBoss 服务端项目](https://github.com/Solr159/JavBoss)
- [上游 Android 客户端](https://github.com/zsdcxd/javboss-phone)

<details>
<summary><strong>查看完整功能说明</strong>（展开后包含各页面与交互细节）</summary>

## 功能

- **登录**：服务器地址 + 密码，会话 Cookie 本地持久化（服务端会话 14 天有效，
  禁用系统备份）；底部导航"退出"可主动登出
  （通知服务端销毁会话并清空本地 Cookie）

  - 地址支持两类：**IP 地址**（`192.168.1.10:8655`，可省略协议头，自动补 `http://`）
    与**完整 URL**（如 `https://app-demo.example.com/`，支持 https 与路径前缀）
  - **预设地址**：★ 按钮把当前地址存为命名预设，登录成功的地址自动收录；
    登录页列出预设可直接点选，点"管理"可重命名/删除；同址自动去重，最近使用排最前，上限 20 条
  - **一键登录**：默认「记住密码」，登录成功后密码随预设一起存本地，之后点预设直接登录，
    不用再输密码；存过密码的预设右侧带 🔒 标记
  - **按网络自动切换登录**：设置页打开「自动切换登录」后，从两条已存密码的预设里指定
    「家里（局域网）」和「外面（外网）」——首次开启会按地址自动认（私网 IP = 家里，域名 = 外面），
    认错了在设置页改，登录页的预设行也会标出「家里 / 外面」。
    App 冷启动、从后台切回前台、网络变化（连上家里 WiFi / 切到蜂窝）时，用一个**独立短超时客户端**
    探测「家里」地址的 `/auth/status`：拿到任意 HTTP 响应就认为在家，连不上则认为在外面。
    判定后用目标预设里保存的密码**重新登录**（Cookie 是 origin 绑定的，只改地址不重登会因跨源
    发不出会话而失效），成功后清掉返回栈重建各页面并给出 Toast；
    探测或登录失败会**回滚到切换前的会话**，绝不会因为一次自动切换把用户踢回登录页。
    正在播放视频时不切换，退出播放页后补检。探测地址默认就是「家里」预设的地址
    （即「能连上局域网服务器就算在家」），也可在设置页另填一个地址；
    「立即检测」可手动跑一次并看到结果。用户主动登出后不再在后台自动登录
  - **封面裁剪还原**：作品卡按服务端 `cover_crop_position`（Web 端手动调过的那一项）
    设置裁剪锚点；女优卡按 `cover_crop_left` + 竖版 100:143.1、片商/系列卡按横版 800:538 居中，
    与 Web 端实现保持一致，宽幅包装图不再切在中缝
  - 校验与归一化规则、预设去重/命名/持久化细节见
    [docs/server-address-and-presets.md](docs/server-address-and-presets.md)
- **导航**：三个内容 tab（视频 / **JAV** / 收藏夹）+ 设置页（JAV 这个名字跟服务端 Web 端一致，页内视角 chip 也叫 JAV）；
  **按当前应用窗口宽度自动切换导航形式**：窄窗口（手机竖屏 / 窄分屏）用底部 `NavigationBar`，
  达到 Medium / Expanded 阈值的窗口（平板、宽分屏、手机横屏）改用左侧 `NavigationRail`，
  窗口缩放实时切换且当前 tab 与页面状态保留（详见 v0.3.42 段）；
  **横屏 / Rail 模式下，视频与 JAV 页原顶栏的视角 / 排序 / 搜索功能并入这条原左侧栏**
  （紧凑工具项，不新增第二条工具条、不加宽 rail；详见 v0.3.50 段），
  设置页「左侧栏紧凑模式」可让主 tab 与工具项只留图标并压到 32dp 一屏放下
  （详见 v0.3.51 / v0.3.52 段），
  Compact 手机竖屏仍用页面顶栏，底部导航栏 **82dp**、图标上方留 18dp 冗余、
  栏顶正好盖住一页显示不下的封面（详见 v0.3.53 / v0.3.54 / v0.3.55 段）；
  **冷启动默认进 JAV**，可在设置页「启动显示界面」改成 视频 / JAV（只在下次启动生效，当前会话不跳转）
  页面转场重写过：Navigation Compose 默认是 700ms 淡入淡出（手感明显慢半拍），
  这里统一改成 200ms「1/12 屏宽轻推 + 淡入淡出」，进/出详情立刻跟手；播放页转屏只用 150ms 淡入淡出
  - **视频**：原始视频列表，16:9 缩略图**等高行网格**，每行卡片数与 JAV
    **各自独立设定**（v0.3.56 起不再联动、不再恒少一列，详见 v0.3.56 段），卡片信息栏**第一行是标题**
    （单行省略、回退文件名时去掉扩展名），不再显示「播放 N 次」文字行；观看 / 播放时间统一由
    封面**右上角的「已观看」图标**显示（与 JAV 卡片同一套，点击浮出累计时长）；
    **第二行显示所在文件夹路径**（`path` 去掉文件名，如 `twitter@kuzu_v0`，根目录显示 `.`），
    点它按 `directory_id` + `subdirectory` 筛出该文件夹下的全部视频，顶栏出现「目录：…」提示条
    可一键清除；对齐 Web 端视频卡片第二行与 `setVideoDirectoryFilter` 的跳转；
    封面**左下角显示时长**、**右下角是与 JAV 卡片同一颗播放钮**（点它直接起播，不进详情）、
    **左上角显示服务端标签**（视频自身 `tags` 与作品 `jav.tags` 合并去重，最多 2 个 + 溢出计数，
    叠在封面上）；
    缩略图**同样受「封面毛玻璃」/ 锁屏自动打码控制**；无限加载、下拉刷新；
    **顶栏与 JAV 界面对齐**：左边保留本页标题，右边是与 JAV 同一套紧凑图标按钮
    「排序 / 筛选」（点开底部菜单列出 8 个排序值）与「搜索」（点开才展开输入框，收起时清空），
    不再使用常驻搜索框与下拉排序框。
    **只列普通视频**：服务端 `config.video_hide_jav` 为 true 时（默认）请求带 `hide_jav=1`，
    已关联 JAV 的视频只在「作品」里出现，与 Web 端同一个开关
  - **JAV**：JAV 作品库，四个卡片墙视角（JAV/女优/片商/系列），**等高行网格**（不是瀑布流：
    瀑布流各列独立堆叠，卡片高度差半个像素就会越差越多，出现「最后一列整体偏上、网格对不齐」）、
    每行卡片数按**竖屏 / 横屏分开设定**（v0.3.56 起 JAV / 视频各自独立、去掉自动档，
    详见 v0.3.56 段），仍按当前应用窗口实际可用宽度收敛（旋转 / 分屏 / 自由窗口缩放实时重算）、
    作品 / 女优 / 片商 / 系列 / 收藏夹按 JAV 列数，**视频用自己的列数**、
    无限加载、搜索（顶栏 🔍 展开）；**排序与筛选合并成顶栏同一个按钮**，打开的是同一个菜单
    （上半排序列表、下半筛选区：番号前缀 / 标签 / 仅单体 / 随机），不再有独立的漏斗按钮；
    顶栏四个视角按钮与右侧「排序 / 筛选」「搜索」两个图标都用紧凑尺寸，**窄屏也在一行完整显示、不再横滑截断**
  - **排序**（顶栏 ↕ 按钮，作品与女优两个视角各一套，维度与升降序取值照搬 Web 端，**选择会本地持久化**，
    下次打开 App 沿用上次的排序）：
    菜单样式也与 Web 端一致——**一行一个「维度: 方向」的纵向列表**（如「加入时间: 近→远」），
    右侧 ↑↓ 图标；未选中的维度显示自己的默认方向，点已选中的那行切换升 / 降序。
    收藏夹内页的排序也用**同一个底部功能菜单**（`ModalBottomSheet`，与 JAV 界面的排序/筛选同一个壳）
    - 作品：加入时间 / 番号 / 时长 / 发行时间 / 观看时长 / 喜爱度，再点一次已选维度切升降序
    - 女优：作品数量 / 加入时间 / 年龄 / 身高 / 胸围 / 臀围 / 腰围 / 罩杯
  - **女优卡**：竖版封面左上角「作品 N」角标，封面下方显示名字（主名 + 日文名/罗马名）
    与**固定两行**数据（生日+年龄 / 身高·三围·罩杯，缺的用「生日未录入」这类占位补齐，
    行数恒定才能让网格对齐）；移动端窄卡片只留数字/字母：身高去 cm、三围去中文前缀、
    罩杯只留字母、年龄去「岁」；别名不在卡片里显示
  - **点封面进详情**：无论一部作品挂了几个视频文件，点封面都**直接进作品详情**
    （详情页自己列出全部文件版本，并标出「当前」那条），不再弹文件选择框
  - **封面直接起播**：作品卡封面右下角有播放钮，点它不经过详情页直接进播放页；
    **只有这里**，当一部作品挂多个视频文件时才弹文件选择框（单文件则直接起播）
  - **已观看图标**：作品卡 / 视频卡封面右上角，只有服务端 `watched_ms > 0` 才显示青色时钟徽标，
    点它**在图标旁边浮出一行小字**「已观看：x 小时 y 分钟 z 秒」（不是大弹窗），
    再点徽标 / 点浮层即可收起，几秒后也会自动消失；浮层不拦截触摸，列表照常点击与滚动。
    文案与 Web 端 `watch-time-icons` 同一份服务端数据、同一套格式，
    没有观看记录的卡片右上角**什么都不显示**
  - **喜爱度评分**：封面左上角显示与 Web 端一致的爱心胶囊（实心心 + 分数），点它弹评分框——
    **一排 5 颗爱心**（不再是进度条样式的 Slider）：**左右拖动或点击**都能按 0.5 分一档改星级，
    半分用**同一颗心精确左右对半**（左半实心、右半空心，两层同心不错位），另有「清除评分」入口；
    保存后 `PUT /jav/items/{id}` 直接写回服务端，Web 端同步看到
    （排序/番号前缀/标签多选/仅单体/随机）；点女优/片商/系列卡片跳对应过滤作品列表
    - 未评分的作品显示描边空心的爱心（Web 端靠鼠标悬停才显形，手机没有悬停，不这么改就永远
      看不到、也没法给未评分的作品打分）
  - **收藏夹**：作品收藏夹列表（新建/重命名/删除），点进查看条目，长按移除；
    条目封面同样显示喜爱度爱心（收藏夹接口本身不带评分，用 `/jav?favorite_group_id=`
    拉一份完整作品建索引），点爱心可直接改并写回服务端
    - **收藏夹内页**（点进去的封面网格）右上角：排序（**与 JAV 界面完全对齐的 6 个维度**
      加入时间 / 番号 / 时长 / 发行时间 / 观看时长 / 喜爱度，另保留收藏夹自己的默认顺序与标题；
      再点已选维度切升降序，选择持久化）+ 搜索（按番号 / 标题过滤，搜不到给出「没有匹配…」提示）；
      排序所需的时长 / 发行 / 观看 / 喜爱度取自 `/jav?favorite_group_id=` 拉回的完整 JavDto，
      菜单是**与 JAV 界面一样的底部功能菜单**（不是弹窗）
    - 收藏夹**列表页**不设排序/搜索（文件夹就那几个，按服务端顺序列出即可）
- **设置页**：**每行卡片数**放在页面最上方（第一项），拆成 **JAV / 视频两组、
  每组竖屏 / 横屏两个下拉，共 4 个**：竖屏只给「2 列 / 3 列」（默认 3），
  横屏只给「5 列 / 6 列 / 7 列」（默认 5），**没有「自动」档**；
  JAV 与视频各读各的值、各写各的 DataStore key，**完全不联动**
  （不再「视频恒比 JAV 少一列」，详见 v0.3.56 段）；
  选择值就是**目标列数**（选 N 就排 N 列），仅当窗口按 88dp 硬下限也放不下时才自动收敛；
  改完各自网格立即生效；
  详见 v0.3.43 / v0.3.44 / v0.3.45 / v0.3.46 / v0.3.49 / v0.3.56 段）；
  其后依次是：当前服务器地址、退出登录（二次确认）；**自动切换登录**（总开关 + 「家里 / 外面」两条预设单选 +
  可改的探测地址 + 「立即检测」，详见上条）；**启动显示界面**（视频 / JAV，默认 JAV，
  写入 DataStore、下次冷启动生效）；
  **封面毛玻璃**开关（作品 / 女优 / 片商 / 系列封面以及**视频 tab 缩略图**统一改用毛玻璃模糊显示）；
  **锁屏自动打码开关**（默认开：App 使用中手机锁屏会给封面自动打码，解锁后保持，
  列表顶部出现「显示封面」入口；彻底退出 App 再打开则不自动打码。
  关掉后锁屏不再自动打码、已打的码立刻解除；「封面毛玻璃」常开不受影响，仍一直打码）；
  **关于**（版本 + versionCode + 打包时间 + 包名）放在页面最下面；**读取路径**照服务端「目录管理」设计：
  每行显示路径 + 已扫描/已刮削视频数 + 扫描状态，右上角开关可**启用 / 停用**
  （`PATCH /directories/{id}` body `{enabled}`，停用后不再扫描也不展示该目录内容），右下角扫描 / 删除；
  增删与扫描走 `/directories` 系列接口
- **详情页**：封面、番号、发行日期、时长、片商、系列、演员、标签（**服务端视频自身 tags 与作品
  jav.tags 合并去重展示**，没有 JAV 元数据的普通视频也能看到标签）、**服务端统计的观看时长**
  （`jav.watched_ms`，每个文件版本另显示各自「已看 xx」）；
  **点封面全屏查看完整封面**（`ContentScale.Fit`，不按 2:3 裁切）；
  底部新增**样品图像**（`POST /jav/items/{id}/sample-images` 拿数量，按数组下标取
  `.../{index}/thumbnail` / `.../{index}/detail`）与**视频截图**（`GET /videos/{id}/screenshots`）
  两区，缩略图点开进同一个全屏大图查看器；
  大图查看器支持**双指捏合缩放**（1x–5x，放大后单指拖动看细节）、**多张时左右拖拽切换**
  （也可点箭头）、**长按图片弹出「保存图片」菜单**（保存到本地 / 取消），确认后才下载到本机
  「下载/JavBoss」（API 29+ 走 MediaStore 免权限，
  低版本落到公共 Download 目录，无写权限则退回应用私有目录并提示完整路径）；
  并列出**该作品的每一个文件版本**（宽屏双栏时与封面、操作按钮同在**左栏**；按 `/videos/{id}` 的 `locations` 逐条展开，
  文件名 + 各自磁盘完整路径 + 体积/时长/已看，与服务端 Web 端口径一致，
  多版本不再显示成同一条主路径；当前播放的那条标「当前」）；**长按文件卡片**弹出操作面板：查看文件大小、复制完整路径、
  **重命名 / 删除文件**——每条自带准确的 `location_id`，直接走
  `PATCH`/`DELETE /videos/{id}/locations/{locationId}`，
  服务端会同步改/删磁盘上的真实文件（删除有二次确认且不可撤销），成功后原地刷新详情页；
  片商/系列/演员/标签均可点击跳转到对应过滤作品列表；外部链接直达 JavDB；
  操作栏含播放、收藏（勾选收藏夹 + 新建收藏夹）、编辑（标题/发行日期/时长/片商/系列/演员/标签，支持新建标签，
  这里改的是**作品标签**）；标签区的编辑入口**按两套互不相通的标签体系分流**（详见下条）：
  **JAV 详情显示「编辑标签」**，读 / 新建 / 保存都走 JAV 自定义标签（`/jav/tags` +
  `PUT /jav/items/{id}` 的 `tag_ids`）；**普通视频详情显示「编辑视频标签」**，走视频标签
  （`/tags` + `/videos/tags/replace`）。空标签时同样能进入添加
- **播放**：统一播放页 + 双引擎（引擎只负责解码渲染，页面和控制条只有一套，
  自绘控制条点按显隐：播放/暂停、进度拖动、时间、全屏、缓冲指示；当前引擎标识只在全屏显示，
  竖屏控制条保持紧凑，把宽度让给进度条），
  **窄窗口（Compact）**进入时**先竖屏播放**：窗口宽度**恒等于手机屏宽**、16:9 横向铺满，
  不设左右留白 / 圆角 / 横向缩放，任何屏宽都一样；默认居中，顶部把手**只能上下拖动**
  （左右拖动与改变宽度都被禁止，拖动中宽度不变）、范围限制在屏幕内、位置在本页记住；
  竖屏背景是**与 JAV 界面完全同一张封面**：列表页把番号原样带到播放路由，
  播放页直接用同一个 `jav/{code}/cover`（并按列表卡相同的 `cover_crop_position` 基准裁切），
  **同一时刻只画这一张图**（不再叠视频缩略图产生重影），没番号的普通视频才退回 `videos/{id}/thumbnail`；
  整层做**适度毛玻璃**（API 31+ 16dp RenderEffect，低版本 28px 小解码放大）+ **半透明质感**
  （0.90 alpha 的封面，上覆上轻下重的半透明暗罩 0.12→0.32），前景播放器与控制条保持清晰可读；
  切视频会立刻换成新视频的封面（旧异步结果按 videoId 丢弃），背景同步更新；
  **横屏仍是纯黑舞台，非播放状态及其它界面背景不变**，
  不再是 #121212 主题底色 + 黑色窗口背景造成的整屏全黑；
  手机不强制横屏，点控制条右侧的全屏按钮才切横屏 + 沉浸模式，再点退出全屏回竖屏；
  **窗口达到 Expanded 宽度且为横向（宽 > 高）时**点封面右下角播放钮则直接全屏沉浸播放，
  不再强制进竖屏小窗（按当前窗口宽高判断，不按设备型号 / 是否平板；
  窄窗口、竖屏窗口行为不变）；
  左侧设置浮层可切换引擎/字幕/拖动灵敏度并显示
  解码状态，右侧可锁定控制；横滑调进度（灵敏度可选低/中/高）、左侧竖滑调
  亮度、右侧竖滑调音量；双击左/右三分之一屏快退/快进 10s、双击中间
  暂停/继续，长按 2x 加速（松开或移动手指恢复）
  - 首选 Media3 ExoPlayer：direct 直流优先（支持 Range 拖动；服务端按浏览器
    标准未列出 direct 源时客户端自拼 `/videos/{id}/stream` 补上），失败自动
    fallback 到 HLS 转码流
  - ExoPlayer 全部源失败自动切换 libVLC（内置完整 ffmpeg，wmv/rmvb/flv/avi
    等冷门格式软解兜底），从失败进度续播；libVLC 无法注入 Cookie，
    播放请求经 App 内本地代理（127.0.0.1）转发并附加会话 Cookie
  - **观看时长上报服务端**（与 Web 端同一套口径）：进播放页 `POST
    /videos/{id}/playback-sessions` 开一个会话，之后每 10 秒
    `PUT .../playback-sessions/{session_id}` 上报「本会话累计真实播放的毫秒数」
    （累计值、单调递增，不是增量）；只有真的在播（非暂停/非缓冲）的时间才计入，
    暂停立即补报一次、退出播放页补报最后一次；服务端回 410 说明会话已回收，
  自动重建并从新基线继续累计（不会重复记账）。所以手机上看的时长会实时进
  服务端的统计，详情页的「观看时长」和 Web 端一致
- **崩溃报告**：全局捕获未捕获异常，把时间 / 版本 / 设备 / ABI / 线程 / 完整堆栈写到应用私有目录
  （`filesDir/crash_logs/`，最多保留 20 份，不申请权限、不接第三方 SDK、不向服务器上传）；
  **下次冷启动弹窗**提示「检测到上次闪退」，点「发送到邮箱」一键调起手机邮件 App，
  收件人 `jiedi720@foxmail.com`、主题与完整堆栈正文已预填，由用户在邮件 App 里确认发送
  （Android 不允许 App 静默发信，也不内置邮箱账号密码）；发送后记录重命名为 `.txt.sent`
  不再重复弹窗，点「稍后」则下次启动继续提示
- **修复视频按文件名 A→Z 排序闪退**：服务端在该排序下会 JOIN 出同一 `id` 的多条记录，
  `refresh()` 现在和 `loadMore()` 一样先 `distinctBy { it.id }` 去重，
  避免 `LazyVerticalGrid` 的 `key = { it.id }` 撞重复 key 抛异常崩溃
- **修复「视频 tab 偶尔混进 JAV 文件、重启 App 才好」**：读取服务端 `GET /config` 的
  `video_hide_jav` 时若发生瞬时失败，旧逻辑用 `getOrDefault(false)` 把结果当成
  「服务端没开隐藏」并写进**进程内缓存**，之后所有 `/videos` 都不带 `hide_jav=1`，
  已关联 JAV 的视频就被列出来；缓存只在进程内，只有杀掉 App 重启才会重读配置，
  这就是「不知道什么时候冒出 JAV 文件、重启就好了」的根因。
  现在配置改成三态：只有成功解析到明确值才缓存，请求失败 / 字段缺失 / 值不认识一律返回
  「未知」且**不缓存**；`VideosViewModel` 在未知时每次刷新都重试配置，
  读不到明确值之前一律按隐藏 JAV 请求（保守发 `hide_jav=1`），既不会把 JAV 文件放出来，
  配置恢复后下一次刷新也会自动纠正，无需重启。同时把 `hideJav` 初值从 `false` 改成「未知」，
  消除启动时异步读配置完成前被并发刷新（标签变更信号 / 下拉刷新）撞出不带 `hide_jav`
  请求的窗口。解析规则由 `VideoHideJavTest` 锁死「未知绝不等于 false」
- **标签编辑（两套体系严格分开）**：服务端视频标签（`GET/POST /tags` → `video.tags`）
  与 JAV 自定义标签（`GET/POST /jav/tags` → `jav.tags`）是**互不相通的两套数据**，
  同一个勾选弹窗按 `TagPickerMode` 分流，绝不混用接口：
  - **普通视频详情**「编辑视频标签」：可选项 `GET /tags?hide_jav=<video_hide_jav>`（与 Web 端
    `Rr({hideJav})` 一致，开启时排除只挂在 JAV 上的标签）、新建 `POST /tags`、
    保存 `POST /videos/tags/replace {video_ids, tag_ids}` **全量替换**（空数组即清空），
    对齐 Web 端视频列表「编辑标签」
  - **JAV 详情**「编辑标签」：可选项 `GET /jav/tags`（Web 端 `javTagOptions`）、
    新建 `POST /jav/tags`、保存 `PUT /jav/items/{id}` 只传 `tag_ids`（Web 端
    `un(e, {tag_ids})` 同款），**不会碰到视频标签**
  - 两条路径都支持勾选 / 取消 / **新建标签**；当前已打上的标签即使不在可选项里也会补进列表，
    避免一打开就被误删。保存成功后原地刷新详情页，并通过进程内信号通知视频 tab 自动刷新，
    卡片左上角的标签同步更新
- **平板适配 + 锁屏打码开关（v0.3.40）**：
  - 「每行卡片数」新增**「自动」**档并把手选上限从 4 扩到 6：按 `smallestWidthDp ≥ 600dp`
    判平板、按当前方向判横竖屏，手机竖屏 2 / 手机横屏 3 / 平板竖屏 4 / 平板横屏 5 列；
    **视频 tab 也接入同一份列数设置**（此前硬编码 2 列），旋转屏幕时四个网格一起重算
  - 设置页新增**「锁屏自动打码」开关**（默认开，保持旧行为）；关掉后锁屏不再自动糊封面、
    已打的码立刻解除，「封面毛玻璃」常开仍照常生效
  - **平板横屏**点封面右下角播放钮直接进**全屏沉浸播放**，不再强制切竖屏小窗；
    手机与平板竖屏仍走原来的竖屏小窗 + 手动全屏流程
  - 纯逻辑抽到 `ScreenRules`（无 Android 依赖），由 `ScreenRulesTest` 锁死平板阈值、
    自动列数、手选钳制与「只有平板横屏直接全屏」规则
- **横屏列数翻倍 + 多版本路径修正（v0.3.41）**：
  - 「每行卡片数」改为**竖屏基准值、横屏一律翻倍**：手机自动 2→4、平板自动 4→8，
    手选的 2..6 也按同规则翻倍（横屏上限 8 列）。四个网格（视频 / 作品墙 / JAV 过滤页 /
    收藏夹内页）共用 `ScreenRules.resolveColumns()`，旋转时一起重算；
    `ScreenRulesTest` 补齐「横屏严格等于竖屏两倍（未触顶）」与上限断言
  - **修复详情页多版本路径显示成同一个**：`/jav` 内嵌的 videos 对同一个 `video_id`
    的多条记录 `id` 完全相同（只有 `location_id` 不同），旧实现按 `v.id == current.id`
    判断「当前那条」，结果把**所有版本**都替换成 `/videos/{id}` 的单条主路径
    （例如 SNIS-557 两个版本都显示 `G:\UnScrapy\...`）。
    现改为对齐 Web 端：以 `/videos/{id}` 的 `locations` 为权威数据源逐条展开，
    每条用自己的 `relative_path` + `directory_path`（兼容内嵌 `directory` / `directory_ref`）
    + `location_id`，`/jav` 内嵌 videos 只按 `location_id` 补充该文件各自的统计信息；
    目录缺失时用同路径的内嵌条目 / 顶层目录兜底，避免整条被丢弃。
    展开后每条自带准确 `location_id`，重命名 / 删除不再需要按路径反查
  - 逻辑抽到纯函数 `expandFileVersions()`，由 `FileVersionsTest` 锁死多目录展开、
    目录对象兼容、无效 location 过滤、目录兜底、按 `location_id` 匹配统计与旧 bug 回归
- **窗口自适应 / 原生平板布局（v0.3.42）**：面向 Xiaomi Pad 7 Pro 这类平板的
  横屏、竖屏、分屏、自由窗口与不同 DPI / 窗口尺寸。核心原则：**只依据「当前应用窗口
  实际可用宽度（dp）」分级**，不看设备型号、不看是否平板、不看 `smallestScreenWidthDp`、
  不看物理分辨率；旋转 / 分屏 / 自由窗口缩放会实时重测并自动切换布局，
  且保留当前页面与 tab 状态（`configChanges` 已含 orientation/screenSize/screenLayout/
  smallestScreenSize，Activity 不重建）。v0.3.40 的「smallestWidth 判平板」与 v0.3.41 的
  「横屏列数翻倍」规则由本轮统一窗口分级取代，旧 `ScreenRules` 仅保留兼容旧调用点与旧单测。
  - 纯逻辑 `ScreenRules.WindowSizeRules`：`width < 600` = Compact / `600..839` = Medium /
    `>= 840` = Expanded（对齐 Material WindowSizeClass 阈值，但只吃窗口宽度一个入参）；
    统一给出页面最大内容宽度、左右边距、网格间距、卡片最小宽度、动态列数，以及
    对话框 / BottomSheet / 搜索框 / 登录表单 / 设置页 / 大图查看器 / 播放区的最大宽度
  - `Common.kt` 新增 `AdaptiveRoot`（`BoxWithConstraints` 测出当前窗口宽高，经
    `LocalWindowWidth` / `LocalWindowHeight` 下发）+ 一组 `Modifier` 限宽扩展；
    `MainActivity` 在 `JavBossTheme` 内包一层，整 App 都能读到当前窗口尺寸
  - **导航**：`MainScreen` Compact 用底部 `NavigationBar`，Medium / Expanded 用 64dp
    左侧 `NavigationRail`（Expanded 才常驻文字标签）；tab 用 `rememberSaveable` 保留，
    正文用 `adaptiveContentWidth()` 限宽居中
  - **四个网格**（视频 / 作品墙 / JAV 过滤页 / 收藏夹内页）：列数由
    `gridColumns(窗口宽, Rail 占位, 卡片最小宽)` 按「可用宽度 ÷ 卡片最小宽度」算，
    **不再固定 8 列**；竖版卡 150dp / 16:9 视频卡 200dp 分别取最小宽；超宽自由窗口
    按正文限宽上限收敛列数，避免卡片被压得比最小宽度还窄；边距 / 间距按窗口分级
    （Compact 8/8、Medium 16/12、Expanded 24~32/16）；搜索框与排序 BottomSheet 在宽窗口限宽
  - **详情页**：Expanded（≥900dp）真正双栏——左 300dp 封面 + 基本信息、右栏标签 /
    样品图像 / 视频截图 / 文件版本，两栏各自独立滚动；Compact / Medium 保留原单栏
  - **设置 / 登录**：整体限宽居中（登录 480dp；设置 Medium 720dp / Expanded 960dp），
    设置页三个弹窗与登录页预设命名弹窗在宽窗口限宽，不再拉伸到整屏
  - **弹窗与大图**：`AlertDialog` / `ModalBottomSheet` / `ImageViewerDialog` 在 Medium /
    Expanded 限宽；大图查看器翻页阈值按限宽后的容器宽算，不按整屏宽
  - **播放器**：首次是否直接全屏改为 `startFullscreen(窗口宽, 窗口高)`——只有窗口达到
    Expanded 宽度**且**宽 > 高才直接全屏，不再用 isTablet / 设备型号判断；
    **非全屏时只有 Compact 才锁 `SCREEN_ORIENTATION_PORTRAIT`**，Medium / Expanded /
    分屏 / 自由窗口改为 `UNSPECIFIED`，不再强拧竖屏；VLC 播放核心不动，
    仅宽窗口给播放区限宽、把控制条按钮放大到 48dp 触控目标
  - **触控 / 输入**：`CompactIconButton` / `CoverPlayButton` / 排序项等在宽窗口补到 48dp
    触控目标；登录 / 设置 / 弹窗沿用 Compose 默认键盘 Tab 导航与鼠标点击，输入框保留系统
    软键盘适配。零新增 Gradle 依赖，离线构建能力不变
  - `WindowSizeRulesTest`(29) 锁死分级阈值、动态列数、超宽窗口收敛、全屏判定与
    「不依赖设备型号」；全量单测 **97/97**
- **视频网格恒比 JAV 少一列（v0.3.43）**：视频是 16:9 横版封面、JAV 是 2:3 竖版封面，
  同一窗口里横版卡更宽，每行少放一个才舒服。此前视频页另用 200dp 最小卡宽独立算列数，
  取整或设置页手选列数上限钳制时会出现**跟 JAV 一样多**或一下少两列，差值不稳定。
  现改为显式规则：`WindowSizeRules.videoGridColumns(javColumns) = max(javColumns - 1, 1)`，
  即**以同一窗口下 JAV 列数为基准减一**，共用同一个窗口宽度与同一份手选上限；
  极窄窗口 JAV 只剩 1 列时视频保底 1 列。四个竖版网格（作品墙 / JAV 过滤页 / 收藏夹内页）
  列数不变，视频页改用 `Common.rememberVideoGridColumns()`。
  `WindowSizeRulesTest` 补 3 例锁死「恒少一列 / 下限 1 / 手选上限不失差值」，
  全量单测 **100/100**。
- **手选列数真正生效 + 详情页文件路径移到左栏（v0.3.44）**：
  - 修复「横屏 JAV 最多 5 列、设置里 5 / 6 列形同虚设」：此前 `gridColumns()` 把设置页
    手选值只当**上限**（`dynamic.coerceAtMost(choice)`），平板横屏 959dp 自动档按 150dp
    最小卡宽只算出 5 列，于是选 5 列等于没选、选 6 列也被钳成 5。现改为**手选值就是目标列数**：
    自动档仍按窗口宽度动态算（保持 150dp 观感），手选档按 100dp 硬下限单独算窗口能容纳的
    最大列数，取「用户选的列数」与它的较小值——选 6 排 6、选 7 排 7，只有窗口真放不下才收敛；
    设置页选项从 2..6 扩到 **2..8**，`SettingsManager` / `SettingsViewModel` 的钳制上限改用
    `WindowSizeRules.GRID_MAX_COLUMNS`（旧 `ScreenRules.COLUMNS_MAX = 6` 保留给旧规则与旧单测，不动）。
    视频页继续在 JAV 列数基础上恒少一列。
  - **详情页文件版本 / 完整路径移到左栏**：宽屏双栏时左栏为
    「封面 + 基本信息 + 播放 / 收藏 / 编辑 + 文件版本与完整路径」，右栏为
    「片商系列 / 演员 / 标签 / 外链 / 样品图像 / 视频截图」；单栏下顺序仍是
    「封面 → 信息 → 操作 → 文件 → 片商系列 …」，手机布局不变。
  - 封面下方不再显示「点击查看完整封面」文字，**点击封面进入完整封面大图的功能保留**。
  - `WindowSizeRulesTest` 更新手选列数用例并补 1 例，锁死
    `959dp 自动 5 列 / 手选 6 排 6 / 手选 7 排 7 / 1200dp 手选 8 列可达`；
    全量单测 **101/101**。
- **设置页卡片数置顶 + 选项一行显示（v0.3.45）**：把「每行卡片数」从「显示」分组抽出，
  放到设置页最上方（「设置」标题之后、账号之前），成为第一个可见设置项；
  9 个选项（自动 / 2..8 列）由原先硬编码 `chunked(3)` 的**三行**改为单个 `Row` **一行显示**，
  宽窗口（平板 / 横屏）一次看全，窄窗口通过横向滚动查看，不再换行。
  其余设置项顺序、手机布局与业务逻辑均不变；全量单测 **101/101**。
- **手选 8 列真正排 8 列 + 左侧栏收窄 + 主题色换橙 + 文案精简（v0.3.46）**：
  - 修复「选 8 列却只显示 7 列」：此前手选档按 `MANUAL_CARD_MIN_WIDTH_DP = 100`
    加 16dp 间距算 `maxFitting`，959dp 平板横屏（扣掉侧栏与边距后约 831dp 可用）
    只算出 7 列，手选 8 被收敛成 7。现把硬下限降到 **88dp**，
    同窗口 `maxFitting = 8`，选 8 就排 8 列（每卡约 92dp）；
    侧栏同时收窄又多留出 16dp 可用宽度，窄窗口仍会按硬下限收敛。
  - `RAIL_WIDTH_DP` 从 **80dp 收窄到 64dp**，左侧 `NavigationRail` 更紧凑，
    网格可用宽度相应增加（该常量同时用于 `rememberReservedWidthDp()`，两处自动一致）。
  - 视频页仍按 v0.3.43 规则恒比 JAV 少一列（JAV 8 列时视频 7 列），未改动。
  - **主题色 `#E57373` → `#FF9000`**：`Theme.kt` 的 `primary` 改为橙色，
    并把 Material3 默认紫粉系的 `secondary` / `tertiary` 及各自容器 / 前景
    一并换成接近橙色的暖色系，界面里不再残留粉紫色文字；
    `background` / `surface` / `surfaceVariant` / `error` 等语义色保持原样；
    启动器图标里的粉色三角 `#E94560` 同步改为 `#FF9000`。
  - **文案与顺排精简**：详情页删「长按文件卡片：查看大小、复制路径、重命名或删除」
    提示文字，并把「演员」移到「片商 / 系列」上方；设置页删「每行卡片数」长说明、
    「启动显示界面」说明、「读取路径」目录管理说明三行文字，
    「读取路径」开关上方不再显示「启用 / 停用」文字（开关本身已足够表意）；
    宽窗口（平板 / 横屏）下「启动显示界面」4 个选项排成一行，窄窗口仍一行一个。
  - `WindowSizeRulesTest` 更新 959dp 手选 8 列断言，并在同一用例里补
    「按 64dp 侧栏占位仍可达 8 列」的断言；全量单测 **101/101**。
- **包名改为 `com.javboss.modified`（v0.3.47）**：
  - `applicationId` 由 `com.javboss.phone.preset` 改为 `com.javboss.modified`，
    去掉已名不副实的 `phone` / `preset`，与桌面名「JavBoss Modified」一致；
    源码 `namespace` 仍保持 `com.javboss.phone` **不动**（与 applicationId 互相独立，
    `proguard-rules.pro` 里 `com.javboss.phone.**` 的 keep 规则无需改，40+ 源文件零改动）。
  - 只改 `build.gradle.kts` 的 applicationId / 版本标识 + 文档；业务逻辑、服务端接口、
    签名证书（`CN=JavBoss Preset Build`）全部不变。
  - **⚠️ 安装影响**：Android 按「包名 + 签名」判断覆盖，新包名 = 全新应用，
    与 v0.3.46 及更早的 `preset` 包**共存、不能覆盖升级**。
    `/data/data/<包名>/` 下 3 个 DataStore（登录预设含两条地址 + 密码、会话 Cookie、
    设置项：列数 / 锁屏打码 / 自动切换 / 启动页）与 `filesDir/crash_logs/`
    **不会迁移**，需卸载旧包、重新登录并重新配置一次。
    同签名也无法跨包读数据（Android 按 UID 隔离），没有无痛迁移方案。
  - 设置页「关于 → 包名」用 `BuildConfig.APPLICATION_ID` 动态读取，自动显示新包名。
  - 全量单测 **101/101**。
- **设置页精简与播放按钮归位（v0.3.48）**：
  - 「退出登录」文字由旧浅红语义色改为**新主题橙**（`colorScheme.primary`），
    退出确认弹窗的「退出」同步换色；按钮从整行高的 `TextButton` 换成紧凑文本按钮，
    账号卡片留白明显收窄。
  - 「启动显示界面」只保留 **视频 / JAV** 两个选项，不再把「收藏夹」「设置」
    作为冷启动页；`LaunchTab.startupOptions` 为唯一数据源，
    `SettingsManager.restore()` 会把旧版本存过的收藏夹 / 设置值回落到 JAV，
    避免单选框出现「一个都没选中」。
  - 删除「锁屏自动打码」下方那段长说明文字（关掉后…彻底退出 App 再打开不继承）。
  - 「每行卡片数」由 9 个独立 `FilterChip` 改为 **下拉列表**
    （`ExposedDropdownMenuBox`），选项仍是「自动 / 2..8 列」，语义不变。
  - 修复封面右下角播放按钮在宽窗口往中间偏：旧实现把 30dp 视觉圆钮居中放进
    48dp 触控区，离右边 / 下边变成 15dp；现 48dp 触控区整体贴右下角、
    视觉圆钮按 `BottomEnd` 对齐，边距回到 **6dp**，与右上角「已观看」图标一致。
  - 新增 `LaunchTabTest` 3 例；全量单测 **104/104**。
- **JAV 列数按竖屏 / 横屏分开设定（v0.3.49）**：
  - 用户反馈「竖屏选了 6 列也只显示 3 列」是无效选项，故把设置页「每行卡片数」
    拆成**两个独立下拉**：竖屏「自动（3 列）/ 2 列 / 3 列」、
    横屏「自动（6 列）/ 5 列 / 6 列」。各自只列该方向真正排得下的列数。
  - 「自动」不再按可用宽度动态增减，而是**固定取竖屏 3 列、横屏 6 列**（正好翻倍），
    只在窗口按 88dp 卡片硬下限也放不下时才由 `gridColumns()` 收敛；
    手选值就是目标列数（选 N 排 N 列）。方向仍只由当前窗口宽高判断
    （宽 > 高 = 横屏），旋转 / 分屏 / 自由窗口实时切换。
  - `WindowSizeRules` 新增 `autoColumns(landscape)` / `resolveColumnChoice()` /
    `migrateColumnChoice()` / `isValidColumnChoice()` 与两个方向候选常量；
    `SettingsManager` 用 `portrait_columns` / `landscape_columns` 两个 DataStore key
    分别持久化，旧 `grid_columns`（0 / 2..8）在 `restore()` 里按方向就近迁移、
    超出范围钳到边界，不再写入旧 key；`SettingsViewModel` 与 `SettingsScreen` 同步拆成两套。
  - `Common.kt` 的 `rememberGridColumns()` 按当前窗口方向取对应设定值；
    **视频页仍恒比 JAV 少一列**（竖屏 3→2、横屏 6→5）。
  - 两个下拉的内置 label 改为「竖屏」「横屏」，顺带消掉 v0.3.48 里
    「下拉 label 与上方分组标题都写每行卡片数」的重复文字。
  - 新增 `ColumnChoiceTest` 9 例（自动档 3/6、候选集互不越界、
    合法值按方向隔离、旧值迁移、自动与手选都真的排到目标列数）；
    全量单测 **113/113**。
- **横屏顶栏功能并入原左侧栏（v0.3.50）**：
  - 用户要求「以最新的为主、无视标签高度与顶部间隙那两条旧反馈」：横屏（Rail）时把
    JAV 页左上四个视角按钮 + 右上「排序 / 筛选」「搜索」，以及视频页右上
    「排序 / 筛选」「搜索」全部移到**原有左侧 NavigationRail**；视频和 JAV 一样处理。
    **不在内容区左侧新增竖向工具条、不加宽 rail**（仍 64dp），工具项紧凑排布。
  - 实现：新增 `ui/components/PageTools.kt` 的 `PageToolsState` + `rememberPageToolsState()`，
    把 4 个开关（JAV/视频各「搜索框展开」「排序弹层」）提升到 `MainScreen`；
    `WorksViewModel` / `VideosViewModel` 同样提到 `MainScreen`（仍是同一个
    `ViewModelStoreOwner` 的实例），rail 里可直接读当前视角、切视角、打开排序前
    加载筛选项、收起搜索时清空关键词。
  - `MainScreen` 的 rail 在主 tab 之后加一条分隔线，再放当前页的紧凑工具项
    （图标 + 一行短标签、44dp 高）：视频 = 排序 / 搜索；JAV = JAV / 女优 / 片商 / 系列
    四个视角（选中态高亮）+ 排序（片商 / 系列视角仍无排序入口）+ 搜索。
    功能区单独可滚动，手机横屏这类矮窗口里 6 个工具项也够得到，不会挤掉主 tab。
  - `WorksScreen` / `VideosScreen` 的顶栏 `Row` 改为 `if (!toolsInRail)` 才画；
    搜索框展开、`ModalBottomSheet` 排序菜单仍留在内容区（入口在栏上，结果在内容区），
    两处读写同一份状态，旋转 / 分屏 / 自由窗口切换导航形式时搜索词与工具开关不重置。
    切换主 tab 时 `closeSheets()` 关掉瞬时排序弹层，避免切回来莫名弹出菜单。
  - Compact 手机竖屏完全不变：底部 `NavigationBar` + 页面原横向顶栏；
    收藏夹 / 设置页不新增工具项。
  - 新增 `PageToolsStateTest` 4 例（初始全关、两页开关互不干扰、
    `closeSheets()` 只关弹层不动搜索框、关后可再打开）；全量单测 **117/117**。
- **去掉顶部多余留白 + 左侧栏紧凑模式（v0.3.51）**：
  - 用户截图两处红框：横屏 / Rail 模式下**左侧栏主 tab 上方的空白**（`NavigationRail`
    默认 `windowInsets` 在 Scaffold 已处理状态栏后又叠了一遍）与**内容区第一行封面上方的
    空白**（网格 `contentPadding` 统一四周留白，宽窗口下显得松）。
  - 收紧：`NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0))` 清掉重复 inset；
    `WorksScreen` / `VideosScreen` 在 `toolsInRail` 时把网格 `contentPadding.top` 改为 `0.dp`
    （左右 / 底部仍保留自适应留白）。**Compact 手机竖屏完全不变**，仍四周统一留白。
  - 设置页「显示」分组最上方新增 **左侧栏紧凑模式** 开关（`rail_compact_mode`，默认关），
    开启后原左侧栏的**主 tab 与页面工具项都只画图标、不画文字**；
    纯规则 `WindowSizeRules.railShowLabel(widthDp, compactMode)`：紧凑模式一律隐藏文字，
    关闭时沿用「Expanded 才常驻文字」的旧宽度规则。
  - 设置改动经 `SettingsManager` 的 `StateFlow` + `rememberRailCompactMode()` 即时生效，
    不用重启 App；`RailToolButton` 紧凑模式收到 40dp（普通 44dp），
    但 `label` 仍作为 `contentDescription` 保留，语义树 / 无障碍仍可读。
  - 不新增第二条工具条、不加宽 64dp rail；新增 `RailCompactModeTest` 4 例
    （各宽度下紧凑模式都隐藏文字、普通模式保留宽度规则、840dp 边界、极大宽度不回退）；
    全量单测 **121/121**。
- **紧凑模式真正压缩按钮高度（v0.3.52）**：
  - 用户反馈：v0.3.51 只是把文字隐藏，按钮仍占原高度、图标停在原位，
    **文字腾出的垂直空间没被回收**，下面的工具项依旧要滚动 / 翻页，算不上紧凑。
  - 修正：紧凑模式下**主 tab 也改用同款极简图标按钮**（M3 `NavigationRailItem`
    隐藏 label 后仍占约 60dp，正是「图标位置不变」的根因），与工具项统一压到 **32dp**、
    去掉 2dp 垂直 padding、两条分隔线 padding 收到 2dp；
    纯规则 `WindowSizeRules.railItemHeightDp(showLabel, compactMode)`。
  - 效果：主 tab 约 60dp → 32dp、工具项 44dp → 32dp，图标整体上移填补文字空间；
    横屏 640dp 窗口里 4 个主 tab + 6 个工具项约 330dp，一屏放得下，不必滚动。
  - **非紧凑模式完全不变**：Expanded 44dp、Medium（宽度规则隐藏文字）40dp；
    紧凑模式只影响 Rail，Compact 手机竖屏仍是底部导航 + 顶部工具条。
  - 新增 `RailCompactModeTest` 3 例（紧凑主 tab / 工具项同为 32dp、普通模式保持 44/40dp、
    紧凑一定更矮且 10 项 < 640dp）；全量单测 **124/124**。
- **设置页两下拉并排 + 竖屏底部栏收紧（v0.3.53）**：
  - 「每行卡片数」的 **竖屏 / 横屏** 两个下拉从上下两行改为**一左一右并排**
    （`Row` + 各 `weight(1f)`），选项文字很短，省掉一行高度。
  - 并排后每个下拉只有半屏宽，`OutlinedTextField` 里改显示短值 **`自动` / `N 列`**，
    完整的 `自动（3 列）` / `自动（6 列）` 说明仍保留在下拉选项里，信息不丢；
    `ColumnChoiceDropdown` 增加 `modifier` 参数承接 `weight`。
  - Compact 竖屏底部 `NavigationBar` 从 M3 默认 80dp **收紧到 64dp**
    （`WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP`，`>= 48dp` 触控目标），
    四个 tab 的图标与文字仍完整；只作用于 `NavigationBar`，Rail / 横屏 / 紧凑模式不受影响。
  - 新增 `NavBarHeightTest` 2 例（比 M3 默认矮、仍满足触控目标）；全量单测 **126/126**。
- **底部栏上方补冗余（v0.3.54）**：
  - 用户反馈 v0.3.53 的 64dp 底部栏图标紧贴栏顶、上方几乎没留白，显得被裁切。
  - 修正：栏高 64dp → **72dp**（仍比 M3 默认 80dp 矮 8dp），但新增的 8dp
    通过 `NavigationBarItem(modifier = Modifier.padding(top = 8.dp))`
    **全部加在图标上方**；内容区仍是 `72 - 8 = 64dp`，与 v0.3.53 完全一致，
    因此图标、文字、下方留白的绝对位置都不变，只在上方补出冗余。
  - `WindowSizeRules` 新增 `NAV_BAR_TOP_PADDING_DP = 8`；`NavBarHeightTest` 新增 1 例
    断言内容区回到 64dp；全量单测 **127/127**。
- **底部栏栏顶补到红线（v0.3.55）**：
  - 用户反馈 72dp 仍「少了点」，理想是栏顶正好达到红框标出的红线，
    **正好覆盖住一页显示不下的那行封面的上边缘**（不让半截封面露在栏上方）。
  - 按用户截图定量标定：红线 y≈245、当时栏顶 y≈280，差 35px；
    M3 选中态 indicator（64×32dp）在图上为 223×111px → 该设备约 **3.48 px/dp**，
    35px ≈ **10dp**。
  - 修正：栏高 72dp → **82dp**、`NAV_BAR_TOP_PADDING_DP` 8 → **18dp**，
    新增的 10dp 仍全部作为 `NavigationBarItem` 顶部 padding；
    `82 - 18 = 64dp` 内容区不变，因此图标 / 文字 / 下方留白绝对位置仍与
    v0.3.53 / v0.3.54 逐像素一致，只是栏顶再上移 10dp 到红线。
    栏高虽高于 M3 默认 80dp，但多的部分全是上方留白，图标文字区仍紧凑。
  - `NavBarHeightTest` 第 1 / 2 例改按「内容区 = 栏高 - padding」判定，
    第 3 例断言内容区恒 64dp 且顶部 padding > 8；全量单测 **127/127**（14 suites）。

- **视频 / JAV 列数独立设定 + 去掉自动档（v0.3.56）**：
  - 用户反馈「每行卡片数 可以分别给视频和 JAV 单独设定，不要联动了」：此前视频列数
    由 `videoGridColumns(jav) = jav - 1` 恒比 JAV 少一列，改 JAV 会连带改视频。
    现改为 **JAV / 视频各一套「竖屏 / 横屏」列数**，共 4 个独立设置项，
    各读各的 DataStore key、各写各的 Flow，互不牵连。
  - 下拉**去掉「自动」档**：竖屏候选 `2 / 3`、横屏候选 `5 / 6 / 7`，
    框里与选项里都直接是「N 列」；**初始默认竖屏 3、横屏 5**（JAV 与视频都如此）。
    选择值本身就是目标列数，`gridColumns()` 不再有 `0 = 自动` 这一层解析。
  - 设置页「每行卡片数」由原来一行两个下拉改为 **JAV / 视频两行、每行竖屏 + 横屏
    并排两个下拉**，行间用分隔线，共 4 个下拉；改完立即生效（StateFlow）。
  - 数据迁移：JAV 沿用 `portrait_columns` / `landscape_columns` 两个 key，
    旧值 0（自动）或越界值经 `normalizeColumnChoice()` 回落到默认 3 / 5；
    更老的单个 `grid_columns` 仍保留一次性「就近取合法值」迁移。
    视频新增 `video_portrait_columns` / `video_landscape_columns` 两个 key，
    缺失就用默认，**绝不从 JAV 取值**（初始化也不联动）。
  - 纯逻辑：`WindowSizeRules` 删掉 `AUTO_*` / `autoColumns(landscape)` /
    `resolveColumnChoice()` / `videoGridColumns()`，新增
    `DEFAULT_PORTRAIT_COLUMNS = 3` / `DEFAULT_LANDSCAPE_COLUMNS = 5` /
    `defaultColumns()` / `normalizeColumnChoice()`；
    `isValidColumnChoice()` 不再接受 0。
    `Common.rememberVideoGridColumns()` 改为读视频自己的两套 Flow，
    与 `rememberGridColumns()`（JAV）平行，各自调 `gridColumns()`。
  - 单测：`ColumnChoiceTest` 重写为 10 例（默认 3/5、候选无自动档、
    合法值按方向隔离、normalize 回落、旧值迁移、每个候选都真排 N 列、
    JAV / 视频独立可多可少可相等、手机竖屏默认 3 列、横屏 7 列可达）；
    `WindowSizeRulesTest` 删除 3 例「视频恒少一列」旧断言，改 1 例
    「极窄窗口下独立选择各自收敛」。

- **禁止后台播放 + 修复竖屏切横屏反复回竖屏（v0.3.57）**：
  - 用户反馈两处：① 切到别的 App / 按 Home 后仍有声音（后台播放）；
    ② 竖屏小窗点全屏切横屏后，方向反复被拧回竖屏。
  - 后台播放根因：`UnifiedPlayerStage` 只在 `DisposableEffect(activeEngine)` 的
    `onDispose` 里释放引擎，完全没监听 Activity 生命周期；`MainActivity.onStop()`
    只做锁屏打码判定。现新增 `LifecycleEventEffect(Lifecycle.Event.ON_STOP)`：
    Activity 真正不可见时立刻 `setPlaying(false)`，并同步 `onPlaybackState(false)`
    让观看时长上报协程立即补报最后一次；切回前台**不自动续播**，用户点播放按钮再继续。
    只监听 `ON_STOP`（真正不可见），不监听 `ON_PAUSE`，避免下拉通知栏 / 系统弹窗
    这类轻微失焦误打断播放。用项目已有的 `lifecycle-runtime-compose 2.8.7`，零新增依赖。
  - 方向抖环根因：原 `LaunchedEffect(fullscreen, windowWidthDp)` 把**旋转后的当前窗口宽度**
    放进 key。竖屏手机点全屏 → `SENSOR_LANDSCAPE` → 转横屏后宽度变大、
    `isCompact()` 变 false → 方向被改成 `UNSPECIFIED` → 系统按姿态拧回竖屏 →
    宽度变小又设回横屏，循环往复。
  - 修正：进播放页时用 `remember` **冻结一次** Compact 判定（`compactAtEntry`），
    方向 effect 的 key 改为 `(fullscreen, compactAtEntry)`，刻意不含窗口宽高；
    系统栏 hide/show 拆成独立的 `LaunchedEffect(fullscreen, windowWidthDp, windowHeightDp)`，
    旋转后重新应用沉浸状态但不碰方向。方向决策抽成 `WindowSizeRules.playerOrientation()`
    纯函数 + `PlayerOrientation` 枚举（不依赖 `ActivityInfo`，可在 JVM 单测里锁死）。
  - 单测：新增 `PlayerOrientationTest` 6 例（竖屏锁 PORTRAIT / 全屏锁 SENSOR_LANDSCAPE /
    退出全屏回 PORTRAIT / 宽窗口一律 UNSPECIFIED / **复现抖环场景：冻结值仍锁横屏，
    而用旋转后宽度重算会得到 UNSPECIFIED** / 分级阈值）；全量单测 132 例 / 15 suites。

- **修复 401 登录态失效时闪退（v0.3.58）**：
  - 实机崩溃报告：`s3.b: 登录状态已失效` 在 **OkHttp Dispatcher 线程**未被捕获，
    冒泡到全局 `UncaughtExceptionHandler`（`CrashReporter`）导致闪退；
    用 `mapping.txt` 反混淆后确认抛出点是 `ApiClient.kt:286` 的 401 application interceptor
    （R8 把它内联进了 `CallServerInterceptor`，所以堆栈首帧看着像库代码）。
  - 根因：`AuthExpiredException` 继承的是 `Exception` 而非 `IOException`。
    OkHttp 4.12.0 的 `RealCall.AsyncCall.run()` 只把 `IOException` 交给 `onFailure`；
    遇到其它 `Throwable` 会先 `cancel()`、包装成 `IOException("canceled due to ...")` 回调，
    然后 **`throw t` 把原异常原样重新抛到 Dispatcher 线程**，无人捕获即闪退。
  - 连带失效：Retrofit 协程拿到的是包装后的 `IOException`（原异常降级为 `suppressed`），
    全项目 30+ 处 `catch (e: AuthExpiredException)` 一次都不会命中，`sessionExpired`
    不置位，原本设计好的「401 自动跳登录页」也一起失效；
    `AuthRepository.isAuthenticated()` 的 `runCatching` 同样拦不住（异常根本没进协程）。
  - 修正：`AuthExpiredException` 改为 `IOException` 子类（零新增依赖）。401 现在走
    `onFailure` → Retrofit 在协程里恢复**原始**异常 → 现有所有 `catch` 生效 →
    `sessionExpired = true` → 各页面 `LaunchedEffect` 跳回登录页，既不闪退也不留死状态。
    ViewModel / LoginViewModel / AuthRepository 全部不用改。
  - 单测：新增 `AuthExpiredExceptionTest` 2 例（断言是 `IOException`、文案保持可读）；
    全量单测 134 例 / 16 suites。
  - 注：该缺陷由上游首个版本 `6998baf`（2026-07-23）引入，
    并非 v0.3.57 的播放器修复引入。

- **横屏详情页操作栏对齐 + 补齐服务端收藏图标（v0.3.59）**：
  - 用户反馈两处：① 横屏（Expanded 双栏）详情页「播放 / 收藏 / 编辑」第三颗「编辑」
    按钮文字看不见、图标尺寸和前两颗不一样，竖屏正常；② 女优 / 作品封面右上角
    缺服务端的收藏图标。
  - 横屏按钮根因：双栏左栏固定 `300.dp`，三颗 M3 `Button` 按自然宽度
    （默认左右各 24dp 内边距 + 24dp 图标 + 文字）总宽超出，`Row` 把最后一颗挤扁，
    于是文字被裁、图标被压缩变形。现在给 `DetailHeader` 加 `compactActions`，
    **仅双栏下**三颗 `weight(1f)` 等宽、内边距收到 6dp、图标统一 20dp；
    竖屏单栏沿用原来的自然宽度排版，逐像素不变。
  - 收藏图标：Web 端 JAV / 女优 / 片商 / 系列四类卡封面右上角都有同一颗圆形爱心，
    判定字段是服务端 `favorite_count > 0`（实心琥珀色 `bg-amber-400 / text-amber-950`），
    未收藏时靠 `group-hover` 才显形。客户端 `IdolDto` 之前根本没声明 `favorite_count`
    （`ignoreUnknownKeys` 静默丢弃），`WorkCard` 也没用上已有的 `JavDto.favoriteCount`。
    现在 `IdolDto` 补字段，两张卡共用 `FavoriteBadge`：`favoriteCount > 0` 才画实心爱心，
    为 0 时整块不画（手机没有 hover、客户端也没有「加入收藏夹」入口，
    画一颗空心图标只是视觉噪音）；作品卡同时有「已观看」徽标时收藏徽标向左错开 30dp，
    两颗圆钮不重叠。判定抽成纯规则 `CardBadgeRules.showFavoriteBadge()`，可 JVM 单测。
  - 单测：新增 `FavoriteBadgeTest` 3 例（正数显示 / 0 隐藏 / 负值当未收藏）；
    全量单测 137 例 / 17 suites。
  - 注：v0.3.59 把图标理解成「圆形爱心」、靠 `favorite_count > 0` 决定画不画、
    并用「左移 30dp」避让已观看徽标，这三点在 **v0.3.60** 按用户纠正全部改掉
    （五角星 / 常显 / 已观看徽标下移）。

- **收藏星标可改收藏夹 + 女优收藏夹 + 角标位置修正（v0.3.60）**：
  - 用户反馈 4 项，逐条登记销项：① 女优 / 作品封面右上角的收藏图标点击时
    要弹悬浮窗修改收藏夹（服务端已有「选择收藏夹」）；② 收藏夹页除作品外
    还要显示女优收藏夹；③ 收藏星标固定在右上角，已观看图标改挂到它的**竖直正下方**，
    避免两颗图标位置冲突；④ 纠正 v0.3.59 的样式：服务端是**黑色黄底五角星**
    （`StarRounded`），不是褐色黄底爱心。
  - 服务端契约（`/tmp/wb.js` 反查）：`fr(e)` 把 `jav / idol / studio / series`
    映射成同名 kind → 端点 `/jav/{kind}-favorite-groups`；实体归属端点
    `/jav/{entity}/{id}/favorite-groups`（`jav→items`、`idol→idols`），
    `PUT` body `{ group_ids }`，`GET` 返回 `{ selected_group_ids }`。
    `ApiClient` 新增 8 个按 kind / entity 参数化的端点；`FavoriteRepository`
    改为通用（默认 `jav` / `items`，旧调用全部不用改）；新增 `FavoriteTarget`
    把「这张卡片要改哪个实体的收藏夹」显式化。
  - 星标：`FavoriteBadge` 改为**常显**（手机没有 hover，空心星就是「加入收藏夹」入口），
    `favorite_count > 0` 画黄底黑色实心五角星，否则半透明黑底白色描边五角星；
    位置固定 `TopEnd + 6dp`，不再因已观看徽标左移。点击弹 `FavoritePickerPopup`
    （居中 `Popup`，避免窄窗口里锚定右上角向左溢出）：`GET` 全部收藏夹 +
    `GET` 当前已选 → checkbox 勾选 → `PUT` 全量替换，支持直接新建收藏夹；
    保存成功后用 `onSaved(selected.size)` 即时刷新星标，不必等列表重拉。
    作品卡（作品墙 / 过滤列表 / 收藏夹条目）与女优卡（女优列表 / 收藏夹条目）
    都接上了 `favoriteTarget`。
  - 观看图标：`WatchedBadge(belowFavorite = true)` 下移到
    `6 + 24 + 6 = 36dp`，与星标同轴、两颗圆钮不重叠；视频卡没有星标仍用 6dp。
    几何规则抽成 `CardBadgeRules.watchedBadgeTopDp()`，可 JVM 单测。
  - 收藏夹页：`FavoritesUiState` 增加 `kind`（作品 / 女优），顶部两个 `FilterChip`
    切换，`FavoritesViewModel` 的增删改查全部带 kind；路由
    `favgroup/{id}?name=&kind=` 透传到 `FavGroupScreen`，女优收藏夹用 `IdolCard`
    渲染、点进去按 `idolIds` 打开该女优的作品列表，作品收藏夹仍用 `WorkCard`。
    `FavoriteItemDto` 补 `cover_code`，女优卡封面优先用它（对齐 Web 端女优卡），
    服务端没给时退回 `sample_code` 的作品封面。
  - 单测：新增 `FavoriteTargetTest` 3 例（jav / idol 的 kind、entity 映射与 id 透传），
    `FavoriteBadgeTest` 补 2 例（星标常显语义、观看徽标下移 36dp 几何规则）；
    全量单测 142 例 / 18 suites。

- **女优收藏夹内页 / 详情页封面 / 标签配色（v0.3.61）**：
  - 用户反馈 4 项，逐条登记销项：① 女优收藏夹进入具体收藏夹后，右上角排序
    仍是作品维度，要换成女优版本；② 女优收藏夹封面下方信息栏缺年龄 / 身高 /
    三围，要和主界面一致；③ 「封面毛玻璃」开启时，详情页主封面与样品图像
    也要一起打码；④ 详情页标签按服务端分色（固有刮削标签橘色、自定义标签
    蓝绿色），并在封面左下角番号正上方堆叠显示自定义标签（一行一个、左对齐、
    多个从下到上）。
  - 女优排序：新增 `FavIdolSortBase` / `FavIdolSortRules`（默认顺序 + 作品数量、
    加入时间、年龄、身高、胸围、臀围、腰围、罩杯，文案与方向对齐 Web 端 `Pe`），
    `FavGroupUiState` 按 `kind` 分派作品 / 女优两套排序，排序状态与作品收藏夹
    分开持久化（`fav_idol_sort` / `fav_idol_sort_asc`）。年龄维度按生日反向比较
    （生日越晚年龄越小）。
  - 女优信息栏：收藏夹 items 只给名字 / 作品数，新增
    `/jav/idols?favorite_group_id=`（`ApiClient` / `VideoRepository` 补参数）拉完整
    `IdolDto` 按 id 建索引，女优卡改用与主界面同源的 `idolInfoRows()` 固定两行
    （生日+年龄 / 身高·三围·罩杯），封面裁剪 `cover_crop_left` 也一并补齐；
    同一份索引供 8 个排序维度取值。
  - 详情页毛玻璃：新增 `coverImageModelMasked()` 公共入口（不暴露 private 的
    `coverBlurSupported`），主封面与样品图像接上 `LocalCoverMask` +
    `coverFrost(masked)`，与列表卡片同源；视频截图与大图查看器保持原样。
  - 标签配色：`TagDto` 补 `provider`（`isCustom = provider == 3`，Web 端 `tt(e)`），
    纯规则 `TagColorRules` 判定自定义标签；详情页 `AssistChip` 用
    `assistChipColors` 分蓝绿 / 橘色，`WorkCard` 新增 `customTags` 参数在番号条
    正上方渲染蓝绿色胶囊列（`asReversed` 实现从下往上堆叠），作品墙 / 过滤列表 /
    收藏夹条目三处都接上数据源。
  - 单测：新增 `FavIdolSortTest` 7 例、`TagColorRulesTest` 5 例；
    全量单测 154 例 / 20 suites。

- **JAV / 视频标签管理与封面标签点击筛选（v0.3.62）**：
  - 用户反馈 4 项，逐条登记销项：① JAV 界面顶栏「系列」右边加「标签」按钮，
    显示服务端 JAV 标签功能，要和服务端一样能新增 / 编辑标签；② 视频界面上方
    也加「标签」按钮，走服务端视频标签功能；③ 视频封面左下角显示视频标签，
    样式与 JAV 一致；④ 点击封面左下角的标签按该标签筛选作品，语义同详情页
    点击标签。
  - 两套标签表严格分流：新增 `TagScope.JAV / VIDEO` 与纯规则 `TagManageRules`
    （`listPath` / `itemPath` / `filterTitle` / `videoTagQuery` / `validName`），
    `ApiClient` 补 `PATCH` / `DELETE /jav/tags/{id}` 与 `PATCH` / `DELETE /tags/{id}`，
    `VideoRepository` 对应补 4 个方法，`listVideos` 补 `@Query("tags")`。
  - `TagManageDialog` 复用一个弹层按 scope 分派 `GET` 列表 / `POST` 新建 /
    `PATCH` 重命名 / `DELETE` 删除，标签行按 `provider` 分蓝绿 / 橘色；
    点标签回调筛选。JAV 页打开 `TagScope.JAV`，视频页打开 `TagScope.VIDEO`。
  - 顶栏：`TagToolChip` 放在 JAV「系列」右边与视频页顶栏工具组；Rail 模式在
    原 64dp 左侧栏追加同功能图标按钮（`Icons.Filled.Sell`），`PageToolsState`
    新增 `worksTagsOpen` / `videosTagsOpen`，`closeSheets()` 一并关闭，旋转 /
    分屏切换不丢弹层状态。
  - 封面标签：`WorkCard.customTags` 与 `VideoCard.tags` 由 `List<String>` 改为
    `List<TagDto>`，共用 `CoverTagStack`（一行一个、左对齐、从下到上堆叠、
    自定义蓝绿 / 刮削橘色）。视频卡标签从左上角单条文本改为左下角堆叠胶囊
    （在时长胶囊上方），数据源为 `video.jav.tags + video.tags` 合并去重。
    作品墙 / 过滤列表 / 作品收藏夹 / 视频卡四处角标都可点击，统一走
    `onOpenJavList("标签：名称", "", tag.id, 0, 0)`，与详情页点击标签完全一致。
  - 视频标签管理里点标签走服务端 `/videos?tags=<name>` 按标签名筛视频：
    `VideosViewModel` 新增 `tagFilter` / `filterByTag()` / `clearTagFilter()`，
    页面顶部显示可清除的「标签：X」提示条。
  - 单测：新增 `TagManageRulesTest` 7 例（两套端点不混用、标题、tags 查询拼装、
    空名校验、`customTags` 保留 id / 去重）；全量单测 161 例 / 21 suites。

- **标签编辑界面按服务端分类分组显示（v0.3.63）**：
  - 用户反馈 1 项：标签的编辑界面要按照服务端的分组显示。
  - 反查 Web bundle 确认服务端分组契约：`GET /jav/tag-categories`（JAV）与
    `GET /tags/categories`（视频）各自返回**分类数组**，分类对象含
    `id / name / sort_order / is_default`；标签对象含 `category`（分类名字符串）
    与 `category_id`。Web 端按 `String(t?.category||"").trim()` 分组，空分类
    显示「默认分类」，分组顺序按分类的 `sort_order`。
  - `TagDto` 补 `category` / `category_id`，新增 `TagCategoryDto`；`ApiClient`
    补两套分类列表端点，`VideoRepository.listTagCategories(scope)` 按 scope 分派，
    `TagManageRules.categoryListPath()` 保证两张表不混用。
  - 新增纯规则 `TagGroupRules`（对齐 Web 端 `Xe` / `Ge` / `Ze` 三个 useMemo）：
    `sortTags()` count 降序 → 名字 → id；`orderedCategories()` 按
    `sort_order` / `id` 排序并补一个合成「默认分类」（`sort_order` 取未占用的
    最小非负整数）；`groupTags()` 按分类名分组、按服务端顺序排列、未知分类排最后。
  - `TagManageDialog` 改为分组标题 + 组内标签行的两级列表；分类列表请求失败时
    降级为「默认分类」+ 标签自带分类名分组，不阻断标签编辑。新建 / 重命名 /
    删除 / 点击筛选全部保留；分类增删改与批量调整标签分类属于 Web 端额外能力，
    本轮只做分组显示。
  - 单测：新增 `TagGroupRulesTest` 9 例（分类端点分流、标签排序、合成默认分类
    sort_order、同序按 id、空分类归默认、服务端顺序、未知分类排最后、默认分类
    占位、组内标签顺序）；全量单测 170 例 / 22 suites。

- **标签管理界面流式胶囊 + 封面标签按分类折叠（v0.3.64）**：
  - 用户反馈 3 项：① 标签界面上方「管理…」说明文字去掉；② 不要一个标签一行，
    改成服务端截图那样「分组标题 + 自动换行圆角胶囊」（标签名 + 数量，自定义绿 /
    刮削橘，标签太多时不方便一行一个管理）；③ 封面上同一分类（如「体位」）有
    多个自定义标签时只显示分类名胶囊，点击展开具体标签；只有一个时直接显示具体标签。
  - 反查 Web bundle 标签管理面板 `mq` 确认胶囊契约：`tt(e)` 判 provider=3，
    深色 / skeuo 主题下自定义 `bg-emerald-500 text-white`、刮削
    `bg-orange-500 text-white`，数量为 `rounded-full` 深色小徽标，胶囊点击语义是
    「筛选标签 name」。客户端据此新增 `CustomTagChipBg` / `ScrapedTagChipBg` /
    `TagChipCountBg` 三个实心色，封面角标仍沿用原浅色系，两者不混用。
  - `TagManageDialog` 删除顶部说明文字；分组内改 `FlowRow` 自动换行实心胶囊，
    短按＝按标签筛选（沿用 v0.3.62 语义），长按弹出「重命名 / 删除」菜单；
    重命名 / 删除确认条移到内容区顶部，不嵌套弹层、不占胶囊版面。
  - 新增纯规则 `CoverTagFoldRules` + `CoverTagItem`：只折叠自定义标签的非空
    `category`，同分类 >= 2 个 → 分类胶囊（`children` 保留全部具体标签），
    只有 1 个 → 退化为具体标签；空分类（默认分类）与刮削标签不折叠；
    保持首次出现顺序、过滤空名、按名去重。
  - `CoverTagStack` 用该规则渲染：分类胶囊显示「体位 ▾」，点击展开 / 收起
    （展开后变「体位 ▴」）并显示该分类下的具体标签；只有具体标签才回调按
    `TagDto` 筛选，分类胶囊本身不筛选。展开状态是纯本地 UI 状态，随组合销毁收起。
  - 单测：新增 `CoverTagFoldRulesTest` 7 例（同分类多标签折叠、单标签退化、
    空分类不折叠、刮削标签不折叠、空名 / 同名去重、混合顺序、配色标记）；
    全量单测 177 例 / 23 suites。

- **封面折叠数据链路修复 + 标签样式统一 + 服务端同款标签菜单（v0.3.65）**：
  - 用户复查指出 v0.3.64「封面上自定义标签按分类折叠」**实际没修正**，并追加
    4 项：① 标签样式统一为菜单里的彩底白字胶囊；② 标签菜单要像服务端一样有
    「编辑 / 分类管理 / 多选查找」；③ 右下角关闭改为右上角 ×，腾出下方空间
    显示更多标签；④ 标签菜单栏宽度再宽一些。
  - **根因（复查回溯）**：不是 `CoverTagFoldRules` 规则写错，而是规则读的
    `TagDto.category` 恒为空。反查 Web bundle `mq` 面板：分类分组用的是
    `GET /jav/tags` / `GET /tags` 返回的**全量标签表**；而 `/jav`、`/videos`
    列表接口内嵌的 `tags[]` 是简化对象（只有 id / name / provider / count），
    不带 `category`。只读探查服务端 SQLite 也确认 `jav_tag.category_id` /
    `jav_tag_category` 有分类数据，但列表接口不下发。属于「写了规则但没接通数据」。
  - **修复**：新增纯规则 `TagCategoryRules`（`indexOf` / `enrich` / `moveCategory`）；
    `VideoRepository` 缓存两套全量标签表的 `id -> category` 索引，在
    `searchJavs()` / `listVideos()` 把数据交给 UI 前用 `enrich()` 回填
    `JavDto.tags` / `VideoDto.tags` / `VideoDto.jav.tags`；分类增删改与批量
    归类后 `invalidateTagCategories()` 清缓存。这样 `CoverTagFoldRules` 才真正
    拿到非空分类，封面折叠生效。已带非空分类的不覆盖、索引查不到的不臆造。
  - **样式统一**：`Common.kt` 抽出共享 `TagChip`（实心彩底白字 + 数量深色徽标 +
    选中描边 + 长按 + 尾部按钮），封面角标 `CoverTagStack`、标签管理弹层、
    详情页标签区三处统一调用，不再各用一套浅色 / 深色 chip。
  - **服务端同款菜单**：`TagManageDialog` 顶部工具条加「分类管理 / 编辑·退出编辑 /
    多选查找·退出多选」（反查 Web bundle `mq` 文案与 `Qe.length!==0&&(i(Qe),t())`
    多选筛选语义）；新增 `CategoryManageDialog`（分类新建 / 重命名 / 删除 /
    上移下移排序）与 `AssignCategoryDialog`（批量归类，含默认分类 id=0）。
    `onApplyFilter` 改为 `List<TagDto>`：JAV 多选按 `/jav?tag_ids=1,2` 筛、
    视频多选按 `/videos?tags=A,B` 筛。新增两套分类管理端点（JAV
    `jav/tag-categories`、视频 `tags/categories`）与请求 DTO。
  - **布局**：关闭移到标题栏右上角 `IconButton(Close)`、`confirmButton = {}`，
    内容区高度 460 → 520dp，底部空间让给胶囊列表；`ScreenRules.tagDialogMaxWidth()`
    （Compact 不限 / Medium 720 / Expanded 900，均宽于普通 dialog 560/640）+
    `Modifier.adaptiveTagDialogWidth()` + `DialogProperties(usePlatformDefaultWidth = false)` 加宽。
  - 单测：新增 `TagCategoryRulesTest` 8 例（含「回填后折叠生效 / 未回填保持展开」
    这条断链回归）、`TagManageRulesTest` +3 例（分类端点分流、多选标题、tag_ids 查询）、
    `WindowSizeRulesTest` +1 例（标签弹层加宽）；全量单测 **189 例 / 24 suites**。

- **封面折叠收窄为只折叠「体位」+ 分类拖拽排序 + 工具条交互细化（v0.3.66）**：
  - 用户反馈 4 项：① 标签分类**不需要全部折叠**，只有「体位」分类折叠显示；
    ② 分类管理移动位置不要用上移/下移箭头一个个挪，要和服务端一样**拖拽**；
    ③ 新建标签输入框不要一直显示，改成和「分类管理 / 编辑 / 多选查找」一样的
    按钮，点击才展开；④ 工具条按钮字号加大到与分组标题差不多大。
  - `CoverTagFoldRules` 增加 `FOLD_CATEGORY = "体位"`：只折叠「体位」下的自定义
    标签（>=2 折成分类胶囊、1 个退化具体标签）；**其他分类（身材 / 主题等）与
    空分类、刮削标签一律不折叠**，各自逐个显示。单测改写混合顺序用例并新增
    「只有体位折叠、其他分类保持展开」显式回归（`CoverTagFoldRulesTest` 8 例）。
  - 分类管理改拖拽排序：每行左侧加三横线把手（`Icons.Filled.Menu`，与 Web 端
    截图一致），`detectDragGestures` 拖动、越过半行即换位（复用纯规则
    `TagCategoryRules.moveCategory`），拖动中仅本地视觉换位（`graphicsLayer`
    translationY + zIndex 置顶），**松手才提交一次** `PUT .../categories/order`；
    删除「上移 / 下移」两个箭头按钮。
  - 「新建标签」改为工具条按钮（`Icons.Filled.Add`），点击才展开输入框 + 新建，
    不再常驻占一行；工具条由 `Row` 改 `FlowRow`，4 个按钮在窄屏自动换行不溢出。
  - 工具条按钮字号 `labelSmall` → `labelLarge`（与分组标题同级），按钮高度
    32→36dp、图标 15→17dp。
  - 单测：`CoverTagFoldRulesTest` 7 → 8 例；全量单测 **190 例 / 24 suites**。

- **详情页标签行间隙（v0.3.67）**：
  - 用户反馈：详情页标签 `FlowRow` 只有水平间距、没有垂直间距，换行后上下两行标签
    边缘贴在一起。给标签 `FlowRow` 加 `verticalArrangement = Arrangement.spacedBy(8.dp)`
    （与水平间距一致），换行后行与行之间留出 8dp 行间隙。
  - 演员 / 片商·系列两处 `FlowRow` 使用同一模式，本次按用户点名的「标签」只改这一处；
    若需统一可一并加垂直间距。
  - 无新增逻辑，单测沿用 190 例 / 24 suites；构建通过。

- **横版详情左栏加宽 + 样品图翻页手感 / 动画（v0.3.68）**：
  - 用户反馈 3 项：① 横版详情页左侧栏区域扩大一些、右侧栏相应缩小；
    ② 样品图左右翻页时横版比竖版需要滑动更大的幅度，要减小横版幅度；
    ③ 给样品图翻页加动画特效。
  - 左栏宽度不再固定 300dp，改为 `WindowSizeRules.detailLeftPaneWidthDp()`：按窗口宽度取
    34%，钳制在 **340..460dp**（900dp → 340、1067dp → 363、1348dp 以上 → 460）。右栏是
    `weight(1f)`，左栏变宽后自动收窄。
  - 翻页阈值（v0.3.68 前按「容器宽度 × 18%」算，横版容器被限到 1100dp 时约需拖 198dp，
    竖版只要 65dp）：新增 `ImagePagerRules.swipeThresholdDp()`，按容器**短边** × 18% 计算并
    钳制在 **56..84dp**，竖屏手机仍是 64.8dp（手感不变），横屏手机/平板统一收到上限 84dp。
  - 翻页动画：拖动时图片跟手位移并按位移比例淡出、缩小（最多 50% / 8%）；松手越过阈值后
    「当前图 180ms 滑出 → 换图 → 新图从反方向 220ms 滑入」，未越过则 180ms 弹回原位。
    `awaitEachGesture` 是受限挂起作用域，动画统一由页面作用域驱动（`animate`），不会踩
    `Restricted suspending functions` 编译约束。
  - 样品图像由单行 `LazyRow` 改为 `FlowRow` **网格铺开**：一次全部显示、不再左右来回滑；
    去掉「点击可查看大图」提示文字（视频截图那条提示保持不动）。
  - 横版额外支持**点按屏幕左右两侧翻页**（左侧 30% 上一张 / 右侧 30% 下一张，中间 40%
    不翻页，首尾不越界）；竖版不启用，保持拖动 + 箭头。判定用
    `WindowSizeRules.isLandscape()`（窗口宽 > 高）与 `ImagePagerRules.tapTarget()`。
  - 修一次滑动翻两页：长距离滑动在模拟器 / 部分机型上会被拆成多段手势，每段都触发一次
    翻页判定。现在**翻页动画期间到达的手势整段忽略**；判定逻辑抽成
    `ImagePagerRules.resolveTarget()` 便于单测边界。
  - JAV 封面左下角番号加**半透明黑底胶囊**（`Color(0xB3000000)` + 3dp 圆角），背景图案
    复杂时也能看清；字号 `labelSmall`(11sp) → `labelMedium`(12sp)，底部渐变保留给上方标签。
  - 单测新增 `ImagePagerRulesTest` 15 例 + `WindowSizeRulesTest` 4 例（左栏宽度下限 / 上限 /
    单调性、横版判定）；全量单测 **209 例 / 25 suites**。

- **翻页动画提速 + 封面番号贴角（v0.3.69）**：
  - 用户反馈 2 项：① 样品图翻页动画速度再快一点；② 番号半透明黑底要**贴封面左下角**，
    不必与上方标签左对齐、也不必与封面边缘留空。
  - 翻页动画 `PAGE_OUT_MS` 180 → **110ms**、`PAGE_IN_MS` 220 → **140ms**（两段合计 250ms）；
    单测从「合计 ≤ 600ms」收紧为「合计 ≤ 300ms」，防止以后又被调慢。
  - 番号黑底去掉左 / 下内边距，改为**贴死封面左边与下边**（只留右上 4dp 圆角），
    与标签的 6dp 左缩进错开；上方仍留 8dp 渐变过渡，不与标签贴死。
  - 全量单测 **209 例 / 25 suites**。

- **女优作品评分 + 标签顺序 + 标签保存报错（v0.3.70）**：
  - 用户反馈 3 项：① 女优封面点进去的作品列表，封面左上角没有评分按钮；② 详情页标签里
    自定义标签排在最前；③ 作品「编辑标签」保存报错。
  - ① `JavListScreen`（女优 / 标签 / 片商 / 系列点进来的都是这个列表）补上 `RatingDialog`
    与 `onRatingClick`，`JavListViewModel.updateRating()` 走 `PUT /jav/items/{id}` 改
    `favorite_rating`（与作品页 / 收藏夹同一接口）。
  - ② 服务端 `jav.tags` 把用户自定义标签（provider=3）排在前面返回，客户端原样渲染。
    新增 `TagOrderRules.scrapedFirst()`：**刮削标签在前、自定义标签在后**，组内保持原顺序；
    详情页标签区改为按此顺序渲染。
  - ③ 根因是 `tag_ids` 混入了刮削标签 id：服务端 `PUT /jav/items/{id}` 要求 tag_ids 全部
    `is_user=true`（校验 `count == len(tag_ids)`，否则 `invalid tag_id` 整单失败）。
    编辑标签弹窗与编辑作品弹窗现在只呈递 / 提交 `TagOrderRules.userTags()`（provider=3 且 id>0）。
  - 顺带：详情页**番号上方新增星级显示**（`InlineRatingBar`，5 颗爱心 0.5 分一档），
    点击弹出同一个评分弹窗调整，保存后 `javMeta` 立即刷新。
  - 单测新增 `TagOrderRulesTest` 6 例；全量单测 **215 例 / 26 suites**。

- **标签顺序纠正为自定义在前 + 编辑标签改胶囊样式（v0.3.71）**：
  - v0.3.70 把顺序做反了（当时理解成「刮削在前」）。服务端 `GET /jav/tags` / `jav.tags` 本来就是
    刮削在前、自定义在后，所以 v0.3.70 的 `scrapedFirst()` 等于没变。本版改为
    `TagOrderRules.customFirst()`：**自定义标签（provider=3，绿色胶囊）排最前**，刮削（橙色）在后，
    组内保持服务端原顺序；详情页标签区按此渲染。
  - 「编辑标签」弹窗（详情页 → 编辑标签）改用统一的 `TagChip` **彩底白字胶囊**（原先 M3 FilterChip）：
    上半区「自定义标签」可勾选（选中带白描边），下半区「刮削标签（只读，不在此处编辑）」灰色不可点。
  - 保存时只提交 provider=3 且 id>0 的标签，避免服务端 `invalid tag_id` 整单失败。
  - 单测 `TagOrderRulesTest` 更新为 7 例（含只读分区）；全量单测 **216 例 / 26 suites**。

- **编辑标签恢复增删 + 弹层与标签菜单同宽（v0.3.72）**：
  - v0.3.71 把刮削标签做成只读，导致「编辑标签」里没法再增删大部分标签。本版恢复：
    **自定义与刮削两段都可勾选**——自定义提交 `tag_ids`（服务端要求 `is_user=true`），
    刮削提交 `scraped_tag_ids`（要求 `is_user=false`），两者分开提交、互不覆盖。
    `JavItemUpdateRequest` 新增 `scraped_tag_ids` 字段，`saveJavTags(javId, tagIds, scrapedTagIds)`。
  - 编辑标签弹层宽度从通用 `adaptiveDialogWidth` 改为 `adaptiveTagDialogWidth` +
    `DialogProperties(usePlatformDefaultWidth = false)`，与主页「标签管理」弹层同宽。
  - 弹层内两段都用彩底胶囊（自定义绿 / 刮削橙），选中带白描边；自定义仍排最前。
  - 单测沿用 216 例 / 26 suites。

- **主页长按封面 → 服务端「调整封面」（v0.3.73）**：
  - 原来主页 JAV 作品墙长按封面与短按一样是进详情（`WorkCard` 的 `onLongClick ?: onClick`）。
    本版把长按改为打开与 Web 端 `KE` 组件同款的「调整封面」弹窗：显示宽幅封面原图，
    白色裁切框标出竖版封面的可视区域，可在图上按位置拖动或用滑块调整
    `cover_crop_position`（0..1，默认 1＝靠右），带「重置 / 取消 / 保存」。
  - 契约：`PUT /jav/items/{id}` body `{cover_crop_position: r}`（与 Web 端同一接口）；
    `JavItemUpdateRequest` 新增该字段。保存成功后只把该字段写回本地列表，
    卡片立刻按新锚点重新裁切，失败则在弹窗内显示错误并允许重试。
  - 裁切框几何 / 拖动反解 / 百分比抽成 `CoverCropRules`，对齐 Web 端 `aT` / `oT`：
    `iT = 800*0.47/538`，`a = min(1, iT/i)`、`o = min(1, i/iT)`，
    `t = (x - a/2)/(1-a)`；原图横向没有裁切余地（`a >= 1`）时按 Web 端一样禁用调整。
  - 编辑预览用清晰原图（与「查看完整封面」大图一致），不套浏览态毛玻璃打码，
    否则开了打码就看不清要裁哪一块；图片加载失败时禁用保存并给出提示。
  - 作用范围是**主页 JAV 作品墙**：女优 / 标签 / 片商 / 系列点进去的列表与收藏夹
    仍保持原来的长按行为。
  - 单测新增 `CoverCropRulesTest` 13 例；全量单测 **229 例 / 27 suites**。

</details>

<details>
<summary><strong>查看与上游 v0.2.1 的完整差异</strong>（展开查看逐项对比）</summary>

## 与上游原版的改进点

对照基线：上游 `main` 分支 `95dba40`（**v0.2.1 / versionCode 3**，2026-08-08 后未再更新）。
本仓库从 `b812953`（2026-11-05）起增加 **63 个提交**，**88 个文件 / +15,282 / −789 行**
（新增 53 个文件，其中 26 个是新的 JVM 单测文件，合计 27 suites / 229 例）。
上游原有的三 tab 底部导航、ExoPlayer → libVLC 双引擎 + 本地鉴权代理、播放手势、
局域网接入说明**全部保留**。

### 工程与发布

| 项 | 上游 v0.2.1 | 本分支 |
| --- | --- | --- |
| 版本 | 0.2.1 / versionCode 3 | 0.3.72-modified / versionCode 76 |
| 产物 | 4 个 ABI 分包 + universal | **单一 arm64-v8a**（约 53MB） |
| 体积 | 无压缩 | R8 混淆 + 资源压缩，64.6MB → 52.8MB（dex 13.7 → 2.2MB） |
| 包名 / 桌面名 | `com.javboss.phone` | `com.javboss.modified` /「JavBoss Modified」，**与原版共存安装** |
| 单测 | 无 | **216 例 / 26 suites**，窗口分级、列数、方向、地址、标签、文件版本、401 异常类型、收藏角标 / 实体映射 / 角标位置、女优收藏夹排序、标签配色、标签管理端点分派与筛选、标签分类分组、封面标签分类折叠、标签分类回填等纯规则都有回归测试 |
| 崩溃诊断 | 无 | 全局捕获 → 私有目录日志 → 下次冷启动一键邮件发送完整堆栈 |
| 构建信息 | 无 | `BuildConfig.BUILD_TIME`，设置页「关于」显示版本 / versionCode / 包名 / 打包时间 |

### 登录与网络

| 改进点 | 说明 |
| --- | --- |
| 地址格式 | 上游只能省略协议补 `http://`；现同时支持 **IP:端口** 与 **完整 URL**（https、路径前缀） |
| 地址预设 | ★ 存命名预设、登录成功自动收录、点选即用、可重命名 / 删除、同址去重、最近使用排前、上限 20 |
| 一键登录 | 记住密码随预设存本地，存过的预设带 🔒，点一下直接登录 |
| 按网络自动切换 | 「家里 / 外面」两条预设 + 独立短超时客户端探测 `/auth/status`；冷启动 / 回前台 / 网络变化时自动重登，失败**回滚原会话**、播放中不切换、可「立即检测」 |

### 列表 · 网格 · 导航

| 上游 v0.2.1 | 本分支 |
| --- | --- |
| 两列**瀑布流**（最后一列偏上、网格对不齐） | `LazyVerticalGrid` + `GridCells.Fixed` **等高行网格**，四个网格统一 |
| 固定 2 列 | 按当前窗口宽度动态算；**JAV / 视频各自独立**、竖屏 / 横屏分开共 4 个下拉（2/3 与 5/6/7，默认 3/5，无「自动」档） |
| 「作品」tab、四视角、筛选弹层 | 改称 **JAV**（与 Web 命名一致）；排序 / 筛选合并为顶栏同一按钮，维度与升降序照搬 Web 纵向列表并本地持久化 |
| 视频卡只有缩略图 + 搜索 / 排序 | 标题单行（去扩展名）、左下时长、右下播放钮、左上标签、第二行文件夹路径可跳转、顶栏与 JAV 对齐 |
| 收藏夹内页无排序 / 搜索 | 补齐与 JAV 相同的 6 维度排序 + 搜索 + 同款底部菜单 |
| 无观看 / 评分信息 | 右上角「已观看」徽标（`watched_ms > 0` 才画）+ 时长小浮层；左上角 Web 同款**爱心评分**（5 颗、0.5 档、半心精确对半、可拖动 / 点击、写回服务端） |
| 点封面弹文件选择 | 点封面**直接进详情**，只有封面右下角播放钮在多版本时才选文件 |
| 封面裁切居中 | 按 `cover_crop_position` / `cover_crop_left` 还原 Web 端裁切锚点 |

### 平板与窗口自适应

| 改进点 | 说明 |
| --- | --- |
| 只有底部导航、只考虑手机 | 只按**当前应用窗口实际可用 dp 宽度**分级（Compact / Medium / Expanded），不看设备型号 / 是否平板 / `smallestScreenWidthDp`；Compact 底部栏、宽窗口左侧 `NavigationRail`，旋转 / 分屏 / 自由窗口实时切换且保留 tab 与状态 |
| 横屏顶栏挤在一行 | Rail 模式下 JAV / 视频的视角 / 排序 / 搜索**并入原左侧栏**（不新增第二条工具条、不加宽 64dp rail）；「紧凑模式」只留图标并压到 32dp 一屏放下 |
| 详情页单栏 | Expanded 真双栏：左封面 + 信息 + 文件版本、右标签 / 样品图 / 截图，各自独立滚动 |
| 弹窗 / 大图 / 设置 / 登录拉伸整屏 | Medium / Expanded 统一限宽；登录 480dp、设置 720 / 960dp |
| 播放器强制横屏、平板强制竖屏 | 按 `startFullscreen(窗口宽, 窗口高)` 判断，只有 Expanded 且宽 > 高才直接全屏；非全屏仅 Compact 锁竖屏 |
| 顶部 / 底部留白 | 清掉 Rail 重复 inset 与内容区顶部留白；Compact 底部栏 82dp + 图标上方 18dp，栏顶正好盖住露出的半截封面 |
| 主题 | `#E57373` 粉红 → `#FF9000` 橙，secondary / tertiary / 启动器图标同步换色 |

### 详情页 · 图片 · 服务端能力

| 改进点 | 说明 |
| --- | --- |
| 只有视频截图 | 新增**样品图像**（`POST /jav/items/{id}/sample-images`）与**点封面查看完整封面**（`ContentScale.Fit`，不按 2:3 裁切） |
| 无大图交互 | 全屏查看器支持双指 1x–5x 缩放、放大后拖动、多图左右切换、**长按弹菜单确认后保存到本地** |
| 多版本路径显示成同一条 | 按 `/videos/{id}` 的 `locations` 逐条展开，每条带准确 `location_id`；长按可**重命名 / 删除磁盘真实文件**（PATCH / DELETE） |
| 标签混用一套接口 | 视频标签（`/tags` + `/videos/tags/replace`）与 JAV 自定义标签（`/jav/tags` + `PUT /jav/items/{id}`）**严格分流** |
| 目录只读 | 设置页「读取路径」按服务端目录管理重做，开关可**启用 / 停用**（`PATCH /directories/{id}`） |
| 观看时长不同步 | 播放会话 `POST /videos/{id}/playback-sessions` + 每 10s 累计上报（只算真实播放、410 自动重建），手机看的时长实时进服务端统计 |

### 播放

| 上游 v0.2.1 | 本分支 |
| --- | --- |
| 进入即自动横屏 + 沉浸 | 窄窗口**先竖屏小窗**：宽度恒等于屏宽、16:9 横向铺满，默认居中、把手只允许上下拖动、位置本页记住；点全屏按钮才切横屏 |
| 竖屏背景整屏全黑 | 换成**与 JAV 列表卡同一张封面**（`jav/{code}/cover`）+ 适度毛玻璃 + 半透明，切视频同步更新；横屏仍是纯黑舞台 |
| 控制条占满宽度 | 竖屏控制条紧凑化，把宽度让给进度条；引擎标识只在全屏显示 |
| 切出 App 仍出声、竖屏切横屏方向抖动 | `ON_STOP` 立即暂停引擎（**禁止后台播放**，回前台不自动续播）；方向判定冻结进页时的窗口分级，不随旋转重判，抖环消失 |

### 隐私 · 设置 · 稳定性

| 改进点 | 说明 |
| --- | --- |
| 无封面隐私 | 「封面毛玻璃」开关；「锁屏自动打码」开关（默认开，解锁立即打码、毛玻璃而非马赛克，冷启动不继承） |
| 设置页 | 「每行卡片数」置顶、「关于」移底、启动显示界面（视频 / JAV）、退出登录改主题色并收紧高度 |
| 文件名 A→Z 排序闪退 | `refresh()` 与 `loadMore()` 一样先 `distinctBy { it.id }`，避免 `LazyVerticalGrid` 重复 key 崩溃 |
| 401 登录态失效时闪退 | `AuthExpiredException` 改为 `IOException` 子类：OkHttp `AsyncCall` 只把 `IOException` 交给 `onFailure`，原先抛 `Exception` 会被原样重新抛到 Dispatcher 线程触发全局崩溃、且 UI 层 `catch` 永远拿不到；现在 401 正常走 `onFailure` → 协程恢复原异常 → `sessionExpired` 置位后自动跳回登录页 |
| 视频 tab 偶发混入 JAV 文件、重启才好 | `/config` 改三态：失败 / 未知**不缓存**、每次刷新重试、保守发 `hide_jav=1` |
| 已观看浮层关不掉 / 大图长按直接下载 | 浮层补 `onDismissRequest` + 3 秒自动收起且不拦截触摸；长按改弹菜单确认后再保存 |

</details>

---

## License

GPL-3.0
