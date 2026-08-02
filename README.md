# NekoMemo

NekoMemo 是一款背题软件，可从学习通网页提取题库，帮助背题练习喵~

> 本仓库为 [JamGmilk/NekoMemo](https://github.com/JamGmilk/NekoMemo) 的分支维护版本。

## 功能特性

- **网页解析**：从学习通网页中解析并提取题库
- **题库管理**：多题库管理，支持单选题、多选题、判断题、填空题、简答题；支持合并、复制、排序与分类筛选
- **练习模式**：
  - 练习范围：全部题目 / 仅错题 / 仅收藏
  - 题型筛选、自定义题量，支持打乱题目或选项
  - 未完成测试可自动保存并继续
- **错题与收藏**：答错自动加入错题本，答对可移出；题目支持收藏；题库列表展示掌握度与错题数
- **分类标签**：自定义分类，设置页统一管理
- **导入/导出**：JSON 文件备份与分享，支持粘贴文本、单文件与批量导入
- **Material 3**：浅色 / 深色 / 跟随系统

## 截图

![Screenshot 1](screenshots/1.jpg) ![Screenshot 2](screenshots/2.jpg)
![Screenshot 3](screenshots/3.jpg) ![Screenshot 4](screenshots/4.jpg)
![Screenshot 5](screenshots/5.jpg)

## 使用说明

1. 在题库页右上角加号，从学习通抓取（或从 JSON 导入）
2. 登录学习通，找到课程的作业详情页面
3. 点击右下角按钮，提取题目
4. 保存到题库（可新建或追加到已有题库）
5. 进入题库详情，配置练习范围（全部 / 错题 / 收藏）后开始测试
6. 也可在题库列表直接「练错题」；未完成的测试下次进入可继续

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
    "title": "局部血液循环障碍",
    "category": "病理学",
    "questions": [
      {
        "text": "慢性淤血时，组织或器官的主要病理变化是",
        "options": ["实质细胞增生肥大", "间质细胞减少", "实质细胞萎缩变性，间质纤维增生", "实质细胞和间质均无明显变化", "实质细胞坏死，间质钙化"],
        "correctIndices": [2],
        "type": "SINGLE_CHOICE"
      },
      {
        "text": "下肢深静脉血栓脱落最常栓塞的器官是",
        "options": ["脑", "肺", "肾", "脾", "肝"],
        "correctIndices": [1],
        "type": "SINGLE_CHOICE"
      },
      {
        "text": "患者，男，60岁，突发持续性胸痛4小时，心电图示ST段弓背向上抬高，血清肌钙蛋白升高。该患者心脏最可能的病变是",
        "options": ["心肌脂肪变性", "心肌梗死", "心肌纤维化", "病毒性心肌炎", "心肌肥大"],
        "correctIndices": [1],
        "type": "SINGLE_CHOICE"
      },
      {
        "text": "心肌梗死的继发性病变包括",
        "options": ["心脏破裂", "室壁瘤形成", "附壁血栓形成", "心肌脂肪变性", "纤维素性心包炎"],
        "correctIndices": [0, 1, 2, 4],
        "type": "MULTIPLE_CHOICE"
      },
      {
        "text": "慢性肺淤血的病理变化包括",
        "options": ["肺泡壁毛细血管扩张充血", "肺泡腔内出现心力衰竭细胞", "肺泡腔内大量中性粒细胞渗出", "肺间质纤维组织增生", "肺泡腔内出现大量纤维蛋白渗出"],
        "correctIndices": [0, 1, 3],
        "type": "MULTIPLE_CHOICE"
      },
      {
        "text": "血栓形成是指血液在活体的心血管腔内凝固形成固体质块的过程。",
        "options": ["正确", "错误"],
        "correctIndices": [0],
        "type": "TRUE_FALSE"
      },
      {
        "text": "贫血性梗死常发生于侧支循环丰富且组织疏松的器官，如肺和肠。",
        "options": ["正确", "错误"],
        "correctIndices": [1],
        "type": "TRUE_FALSE"
      },
      {
        "text": "长期淤血可引起淤血性________和淤血性________，最终导致器官实质细胞萎缩坏死。",
        "options": ["水肿", "硬化"],
        "correctIndices": [0, 1],
        "type": "FILL_BLANK"
      },
      {
        "text": "引起肺动脉栓塞的血栓栓子约95%以上来自________静脉血栓。",
        "options": ["下肢深"],
        "correctIndices": [0],
        "type": "FILL_BLANK"
      },
      {
        "text": "简述血栓形成的三个条件（Virchow三联征）。",
        "options": ["心血管内皮细胞损伤、血流状态改变、血液凝固性增加"],
        "correctIndices": [0],
        "type": "SHORT_ANSWER"
      }
    ]
  }
}
```

## 使用 AI 生成题库 JSON

如果已有纯文本格式的题库（如教材习题、考试真题），可借助 LLM 自动转换为 NekoMemo 支持的 JSON 格式。将以下提示词发送给任意大语言模型（如 ChatGPT、Claude、DeepSeek 等），并在末尾粘贴题库文本即可。

### 提示词

```
你是一个题库解析助手。你的任务是将用户提供的纯文本题库转换为 NekoMemo 题库应用的 JSON 导入格式。

## 输出格式

输出必须是以下 JSON 结构，不要输出任何其他内容（不要 markdown 代码块标记、不要解释说明）：

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

## 字段说明

- title：题库标题，从文本中的章节名或文档标题提取，如"第十五章 甲状腺疾病"
- category：分类名称，从文本中的学科或章节大类提取，如"病理学"；无法判断时填 "GENERAL"
- questions：题目数组，每道题包含以下字段：
  - text：题干文本，去除题号前缀（如"1."、"16."），保留完整题干内容
  - type：题型，取值为以下五种枚举名之一
  - options：字符串数组，用途因题型而异（见下表）
  - correctIndices：整数数组，正确答案的索引（从 0 开始）

## 题型映射与数据存储规则

| 题型识别关键词 | type 值 | options 用途 | correctIndices 规则 |
|---|---|---|---|
| 单选题、A1型、A2型、选择题（单答案） | SINGLE_CHOICE | 各选项文本 | 正确选项的索引，单个元素如 [0] |
| 多选题、X型 | MULTIPLE_CHOICE | 各选项文本 | 所有正确选项的索引，如 [0,2,3] |
| 判断题 | TRUE_FALSE | ["正确", "错误"] | 正确选项的索引，[0] 或 [1] |
| 填空题 | FILL_BLANK | 每个空的答案文本，按顺序排列 | 所有索引，如 [0,1,2] |
| 简答题、问答题、论述题 | SHORT_ANSWER | 参考答案文本（单个元素） | [0] |

## 解析规则

1. 题号剥离：去除题干开头的编号前缀（如"1."、"16."、"A1型选择题"等子类型标记），只保留题目内容本身
2. 选项清洗：去除选项字母前缀（如"A."、"B "、"C、"），只保留选项文本内容；去除多余空格
3. 答案匹配：答案通常在文本末尾以"答案"关键词分隔。按题号对应匹配到各题。答案格式可能为：
   - 字母格式（如"A"、"BCE"）→ 转为索引（A=0, B=1, ...）
   - 文本格式（如"缺碘"、"乳头状癌"）→ 填空题直接作为 options，简答题作为 options
   - 判断题答案（如"正确"/"错误"、"对"/"错"、"T"/"F"）→ 转为 [0] 或 [1]
4. 填空题处理：题干中的空格（连续下划线、连续空格、横线等）代表填空数量。将对应答案按顺序填入 options 数组。如果一道题有多个空，每个空的答案单独作为一个元素
5. 选项字母不一致：有些文本中选项前缀格式不统一（如"A."、"B "、"C、"混用），统一识别并剥离
6. 缺失答案：如果某道题找不到对应答案，跳过该题不输出
7. 无效题目：题干为空或选项不完整的选择题，跳过不输出

## 多选题答案识别

当答案为多个字母（如"ABC"、"BDE"）时，判定为多选题，即使文本未明确标注"多选题"。将每个字母转为对应索引。

## 特殊处理

- 如果文本中某类题型标注了子类型（如"A1型选择题"、"A2型选择题"），统一归为 SINGLE_CHOICE，不区分子类型
- 如果选择题的答案是单个字母，归为 SINGLE_CHOICE
- 如果选择题的答案是多个字母，归为 MULTIPLE_CHOICE
- 填空题题干中的填空位置（下划线、空格、横线）在输出的 text 字段中统一替换为连续下划线"________"（8个下划线）

## 输出要求

- 输出纯 JSON，不要包裹在 markdown 代码块中
- 不要输出任何解释性文字
- JSON 中不要有注释
- 确保所有字符串经过正确的 JSON 转义（引号、换行符等）
- options 数组不能为空数组，每道题至少有 1 个元素

现在请将以下题库文本转换为 NekoMemo JSON 格式：
```

### 使用方法

1. 复制上方提示词，粘贴到 LLM 对话框中
2. 在提示词末尾追加你的题库纯文本
3. 将 LLM 输出的 JSON 保存为 `.json` 文件
4. 在 NekoMemo 设置页点击「导入题库」选择该文件

## 许可协议
    Copyright 2026 JamGmilk
    MIT License
