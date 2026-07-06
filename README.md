# NekoMemo

NekoMemo 是一款背题软件，可从学习通网页提取题库，帮助背题练习喵~

## 功能特性

- **网页解析**：从学习通网页中解析并提取题库
- **题库管理**：多题库管理，支持单选题、单选题、填空题
- **练习模式**：支持测试全部或部分题目，支持打乱题目或选项
- **分类标签**
- **导入/导出**：备份题库或与他人分享
- **Material 3**

## 截图

![Screenshot 1](screenshots/1.jpg) ![Screenshot 2](screenshots/2.jpg)
![Screenshot 3](screenshots/3.jpg) ![Screenshot 4](screenshots/4.jpg)
![Screenshot 5](screenshots/5.jpg)

## 使用说明

1. 在题库页右上角加号，从学习通抓取
2. 登录学习通，找到课程的作业详情页面
3. 点击右下角按钮，提取题目
4. 保存到题库
5. 回到题库页，刷题测试喵~

## 题库 JSON 格式

题库通过 JSON 文件进行导入/导出，用于备份或分享。文件整体结构如下：

```json
{
  "version": 1,
  "nekomemo": {
    "title": "题库标题",
    "category": "分类名称",
    "questions": [
      {
        "text": "题干文本",
        "options": ["选项A", "选项B", "选项C", "选项D"],
        "correctIndices": [0],
        "type": "SINGLE_CHOICE"
      }
    ]
  }
}
```

### 字段说明

| 字段 | 类型 | 说明 | 约束 |
|---|---|---|---|
| `version` | Int | 格式版本号，当前为 `1` | 高版本会尝试兼容导入 |
| `nekomemo` | Object | 题库数据容器（可省略，直接使用扁平结构） | — |
| `title` | String | 题库标题 | 最大 200 字符，不能为空 |
| `category` | String | 分类名称 | 最大 20 字符，为空时默认 `GENERAL`，不存在时自动创建 |
| `questions` | Array | 题目数组 | 最多 5000 题，超出截断 |
| `questions[].text` | String | 题干 | 最大 10000 字符，为空则跳过该题 |
| `questions[].type` | String | 题型枚举名 | 见下表，默认 `SINGLE_CHOICE` |
| `questions[].options` | Array\<String\> | 选项列表（填空题/简答题存储答案文本） | 最多 10 个，每个最大 2000 字符 |
| `questions[].correctIndices` | Array\<Int\> | 正确答案索引（从 0 开始） | 填空题/简答题为全部索引；兼容旧版 `correctIndex`（单个 Int） |

### 支持的题型

`type` 字段同时兼容枚举名和旧版字符串：

| 枚举名 | 旧字符串 | 说明 | options 用途 |
|---|---|---|---|
| `SINGLE_CHOICE` | Single Choice | 单选题 | 选项（最少 2 个） |
| `MULTIPLE_CHOICE` | Multiple Choice | 多选题 | 选项（最少 2 个） |
| `TRUE_FALSE` | True/False | 判断题 | 选项（最少 2 个） |
| `FILL_BLANK` | Fill in the Blank | 填空题 | 答案文本 |
| `SHORT_ANSWER` | Short Answer | 简答题 | 答案文本 |

### 导入示例

```json
{
  "version": 1,
  "nekomemo": {
    "title": "计算机网络题库",
    "category": "计算机网络",
    "questions": [
      {
        "text": "OSI 参考模型共有几层？",
        "options": ["4 层", "5 层", "6 层", "7 层"],
        "correctIndices": [3],
        "type": "SINGLE_CHOICE"
      },
      {
        "text": "以下哪些属于 TCP/IP 模型的层次？",
        "options": ["应用层", "传输层", "会话层", "网络层"],
        "correctIndices": [0, 1, 3],
        "type": "MULTIPLE_CHOICE"
      },
      {
        "text": "TCP 协议建立连接的过程称为 ________。",
        "options": ["三次握手"],
        "correctIndices": [0],
        "type": "FILL_BLANK"
      },
      {
        "text": "HTTP 是一种无状态协议。",
        "options": ["正确", "错误"],
        "correctIndices": [0],
        "type": "TRUE_FALSE"
      },
      {
        "text": "简述 CSMA/CD 的工作原理。",
        "options": ["先听后发，边听边发，冲突停发，随机重发"],
        "correctIndices": [0],
        "type": "SHORT_ANSWER"
      }
    ]
  }
}
```

## 许可协议
    Copyright 2026 JamGmilk
    MIT License
