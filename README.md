# AI 智能助手（Spring Boot + DeepSeek）：对话 · 工具调用 · RAG 知识库问答

一个基于 **Spring Boot + DeepSeek API** 构建的 AI 智能助手，集成了三大核心能力：
**大模型对话**、**Function Calling 工具调用**、**RAG 知识库问答**。

## ✨ 核心能力

- ✅ **智能对话**：调用 DeepSeek 大模型，返回自然语言回答
- ✅ **工具调用（Function Calling）**：AI 根据问题自动选择并调用工具
  - `get_current_time`：查询当前时间
  - `calculate`：完成加减乘除运算（演示 AI 从自然语言提取参数并调用函数）
- ✅ **RAG 知识库问答**：从知识库检索最相关的文档片段，再让大模型基于片段回答
  - 自研文本向量化 + 余弦相似度检索
  - 只将"最相关段落"喂给模型，约束其不幻觉、不乱编

## 🛠 技术栈

| 技术 | 用途 |
| --- | --- |
| Java 21 | 开发语言 |
| Spring Boot | Web 框架与依赖注入 |
| Spring RestClient | 调用 DeepSeek HTTP API |
| Jackson | JSON 解析 |
| DeepSeek API | 大模型服务（对话 + Function Calling） |

## 📁 项目结构

```
chat-demo/
├── pom.xml                              # Maven 依赖配置
└── src/main/java/com/example/chatdemo/
    ├── ChatDemoApplication.java         # 启动入口
    ├── ChatConfig.java                  # 配置 RestClient Bean
    ├── DeepSeekService.java             # 核心业务：对话 / 工具调用 / RAG 检索
    └── ChatController.java              # REST 接口层
```

## 🚀 快速开始

### 1. 配置 API Key

在 `src/main/resources/application.properties` 填入 DeepSeek Key：

```properties
deepseek.api-key=sk-你的key
deepseek.base-url=https://api.deepseek.com
server.port=8080
```

### 2. 运行

启动 `ChatDemoApplication` 主类，等待控制台出现 `Tomcat started on port 8080`。

### 3. 测试接口

| 接口 | 能力 | 示例 |
| --- | --- | --- |
| `/chat?message=你好` | 基础对话 | `http://localhost:8080/chat?message=你好` |
| `/assistant?message=现在几点` | 工具调用（查时间） | `http://localhost:8080/assistant?message=现在几点` |
| `/assistant?message=12加8等于几` | 工具调用（计算） | `http://localhost:8080/assistant?message=12加8等于几` |
| `/rag?message=什么是RAG` | RAG 知识库问答 | `http://localhost:8080/rag?message=什么是RAG` |

## 🔍 核心原理

### RAG 知识库问答（检索增强生成）

```
用户提问
   ↓
① 检索：把问题转成向量，与知识库每段资料算余弦相似度
   ↓
② 召回：挑出最相关的 1 段资料
   ↓
③ 生成：把"该段资料 + 问题"发给大模型
   ↓
④ 大模型依据资料回答（带约束，不幻觉）
```

### Function Calling（工具调用）

```
用户输入 → 声明 tools → 模型返回 tool_calls → 程序执行工具 → 结果回填 → 模型组织回答
```

## 📌 关键设计

- **RAG 检索**：自研文本向量化（相邻字符特征）+ 余弦相似度，从知识库召回最相关片段
- **工具声明**：用 JSON Schema 声明工具，模型据此决定是否调用、调哪个
- **参数抽取**：模型从自然语言提取结构化参数（如把 "12加8" 解析为 `{"a":12,"b":8}`）
- **结果回填**：工具结果通过 `role: tool` 消息回传，由模型组织最终回答

## 🧭 后续规划

- [ ] 接入真实 Embedding 模型 + 向量数据库（替换自研检索，支持大规模知识库）
- [ ] 增加多轮对话上下文记忆
- [ ] 增加更多工具（查天气 / 查数据库 / 联网搜索）
