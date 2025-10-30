

A Discord bot that performs daily random member muting with reward announcements. Originally featured a complex voting system, now simplified to focus on core functionality while maintaining the daily random mute feature.
Features

    Daily Random Mute: Automatically mutes one random member daily at 00:00 UTC+8

    Reward Announcement: Announces the "lucky" member who can claim fixed rewards

    New Member Welcome: Sends automatic private messages to new members

    Admin Controls: Simple commands for bot management and testing

    Multi-Language Support: Python and Java implementations available

Installation & Setup
Python Version
bash

# Install dependencies
pip install discord.py pytz

# Configure environment
# Edit the configuration constants in the script:
# - EXCLUDED_USER_ID
# - EXEMPT_ROLE_ID  
# - ANNOUNCE_CHANNEL_ID
# - TOKEN

# Run the bot
python bot.py

Java Version
bash

# Requirements: Java 11+, Maven/Gradle
# Add JDA dependency to your build file

# Configure constants in Bot.java:
# - EXCLUDED_USER_ID
# - EXEMPT_ROLE_ID
# - ANNOUNCE_CHANNEL_ID
# - TOKEN

# Compile and run

Commands

    /start - Enable daily mute system (Admin only)

    /test - Test announcement (Specific user only)

    !test_announcement - Test announcement via message

    !stop - Shutdown bot

Configuration

    Modify XXX.json files for data persistence

    Set appropriate channel and role IDs

    Configure reward amounts and messages

Contributing

This project is open source. Feel free to submit issues and pull requests for both Python and Java versions.
                





一個 Discord 機器人，每天隨機禁言成員並發送獎勵公告。原本包含複雜的投票系統，現已簡化為核心功能，同時保留每日隨機禁言特色。
功能特色

    每日隨機禁言: 每天 UTC+8 00:00 自動隨機禁言一名成員

    獎勵公告: 公告可領取固定獎勵的「幸運」成員

    新成員歡迎: 自動發送私訊給新加入成員

    管理控制: 簡單的機器人管理和測試指令

    多語言版本: 提供 Python 和 Java 兩種實作版本

安裝與設定
Python 版本
bash

# 安裝依賴
pip install discord.py pytz

# 設定環境
# 編輯腳本中的配置常數：
# - EXCLUDED_USER_ID
# - EXEMPT_ROLE_ID  
# - ANNOUNCE_CHANNEL_ID
# - TOKEN

# 運行機器人
python bot.py

Java 版本
bash

# 需求：Java 11+、Maven/Gradle
# 將 JDA 依賴加入建置文件

# 在 Bot.java 中設定常數：
# - EXCLUDED_USER_ID
# - EXEMPT_ROLE_ID
# - ANNOUNCE_CHANNEL_ID
# - TOKEN

# 編譯並運行

指令列表

    /start - 啟用每日禁言系統（僅管理員）

    /test - 測試公告（僅特定用戶）

    !中獎訊息測試 - 透過訊息測試公告

    !停止 - 關閉機器人

配置說明

    修改 XXX.json 檔案進行資料持久化

    設定適當的頻道和身分組 ID

    配置獎勵金額和訊息內容

貢獻指南

本專案為開源專案，歡迎為 Python 和 Java 版本提交問題和拉取請求





一个 Discord 机器人，每天随机禁言成员并发送奖励公告。原本包含复杂的投票系统，现已简化为核心功能，同时保留每日随机禁言特色。
功能特色

    每日随机禁言: 每天 UTC+8 00:00 自动随机禁言一名成员

    奖励公告: 公告可领取固定奖励的「幸运」成员

    新成员欢迎: 自动发送私信给新加入成员

    管理控制: 简单的机器人管理和测试指令

    多语言版本: 提供 Python 和 Java 两种实现版本

安装与设置
Python 版本
bash

# 安装依赖
pip install discord.py pytz

# 设置环境
# 编辑脚本中的配置常数：
# - EXCLUDED_USER_ID
# - EXEMPT_ROLE_ID  
# - ANNOUNCE_CHANNEL_ID
# - TOKEN

# 运行机器人
python bot.py

Java 版本
bash

# 需求：Java 11+、Maven/Gradle
# 将 JDA 依赖加入构建文件

# 在 Bot.java 中设置常数：
# - EXCLUDED_USER_ID
# - EXEMPT_ROLE_ID
# - ANNOUNCE_CHANNEL_ID
# - TOKEN

# 编译并运行

指令列表

    /start - 启用每日禁言系统（仅管理员）

    /test - 测试公告（仅特定用户）

    !中奖讯息测试 - 透过讯息测试公告

    !停止 - 关闭机器人

配置说明

    修改 XXX.json 文件进行数据持久化

    设置适当的频道和身份组 ID

    配置奖励金额和讯息内容

贡献指南

本项目为开源项目，欢迎为 Python 和 Java 版本提交问题和拉取请求。







