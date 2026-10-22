package com.example.chatdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.HashMap;
import java.util.Map;

@Service
public class DeepSeekService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekService(RestClient restClient) {
        this.restClient = restClient;
    }

    public String chat(String userMessage) throws Exception {
        String body = """
            {
              "model": "deepseek-chat",
              "messages": [{"role": "user", "content": "请用30字以内回答%s"}],
              "stream": false
            }
            """.formatted(userMessage);

        String json = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(json);
        return root.path("choices").path(0).path("message").path("content").asText();
    }
    public String assistant(String userMessage) throws Exception {
        // 声明我们提供的两个工具
        String requestBody = """
            {
              "model": "deepseek-chat",
              "messages": [
                {"role": "system", "content": "你是智能助手，回答简洁自然。"},
                {"role": "user", "content": "%s"}
              ],
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "get_current_time",
                    "description": "获取当前时间，当用户询问时间、几点、现在几点了时调用",
                    "parameters": {"type": "object", "properties": {}}
                  }
                },
                {
                  "type": "function",
                  "function": {
                    "name": "calculate",
                    "description": "计算两个数的加减乘除，当用户让做算术时调用",
                    "parameters": {
                      "type": "object",
                      "properties": {
                        "a": {"type": "number", "description": "第一个数"},
                        "b": {"type": "number", "description": "第二个数"}
                      },
                      "required": ["a", "b"]
                    }
                  }
                }
              ]
            }
            """.formatted(userMessage);

        String json = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(json);
        JsonNode message = root.path("choices").path(0).path("message");

        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && toolCalls.size() > 0) {
            String callId = toolCalls.get(0).path("id").asText();
            String funcName = toolCalls.get(0).path("function").path("name").asText();

            // 真正的"执行工具"：根据 AI 点名的工具，跑对应的 Java 代码
            String result;
            if (funcName.equals("get_current_time")) {
                result = java.time.LocalTime.now().withSecond(0).toString();
            } else if (funcName.equals("calculate")) {
                // 解析 AI 填进来的参数（arguments 是一段 JSON）
                String argumentsJson = toolCalls.get(0).path("function").path("arguments").asText();
                JsonNode args = mapper.readTree(argumentsJson);
                double a = args.path("a").asDouble();
                double b = args.path("b").asDouble();
                result = String.valueOf(a + b);          // 真的用 Java 算出来
            } else {
                result = "没有这个工具";
            }

            // 回传给 AI 组织回答
            String secondBody = """
                {
                  "model": "deepseek-chat",
                  "messages": [
                    {"role": "system", "content": "你是智能助手，回答简洁自然。"},
                    {"role": "user", "content": "%s"},
                    {"role": "assistant", "tool_calls": %s},
                    {"role": "tool", "tool_call_id": "%s", "content": "%s"}
                  ]
                }
                """.formatted(userMessage, toolCalls.toString(), callId, result);

            String json2 = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(secondBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root2 = mapper.readTree(json2);
            return root2.path("choices").path(0).path("message").path("content").asText();
        }

        return message.path("content").asText();
    }
    public String rag(String userMessage) throws Exception {
        // 第②步：真正的"检索"——先挑出与问题最相关的一段资料，只把这段给 AI
        int bestIdx = search(userMessage);
        String material = knowledge[bestIdx];

        String body = """
            {
              "model": "deepseek-chat",
              "messages": [
                {"role": "system", "content": "你是知识库问答助手，只能依据下面给出的资料回答，资料中没有的内容就如实说不知道。"},
                {"role": "user", "content": "资料：%s\\n\\n问题：%s"}
              ],
              "stream": false
            }
            """.formatted(material, userMessage);

        String json = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(json);
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    // ============ 检索部分：把文字变成"向量"，算相似度，挑最相关的段落 ============

    // 知识库：多段资料，每段一个主题
    private static final String[] knowledge = {
        "Java 是一门面向对象的编程语言，支持封装、继承、多态三大特性，适合企业级应用开发。",
        "Spring Boot 是用于快速开发 Spring 应用的框架，内置了 Web 服务器、数据访问、自动配置等能力，简化了配置。",
        "RAG 是检索增强生成，指先从知识库检索相关文档片段，再让大模型基于片段生成回答，能有效减少幻觉。",
        "AI Agent 是能自主感知、决策并调用工具完成任务的智能体，核心是工具调用与多轮推理。"
    };

    // 把一段文字变成"特征->出现次数"的向量（简单做法：用相邻两个字符作为特征）
    private Map<String, Integer> toVector(String text) {
        Map<String, Integer> vec = new HashMap<>();
        String clean = text.toLowerCase();
        for (int i = 0; i < clean.length() - 1; i++) {
            String ch = clean.substring(i, i + 2);   // 相邻两个字作为特征
            vec.merge(ch, 1, Integer::sum);
        }
        for (String w : clean.split("[^a-z0-9]+")) {  // 英文单词也作为特征
            if (!w.isEmpty()) vec.merge(w, 1, Integer::sum);
        }
        return vec;
    }

    // 计算两个向量的"余弦相似度"（越接近1越相似，0表示完全不相关）
    private double cosine(Map<String, Integer> a, Map<String, Integer> b) {
        double dot = 0, na = 0, nb = 0;
        for (int v : a.values()) na += v * v;
        for (int v : b.values()) nb += v * v;
        for (var e : a.entrySet()) {
            Integer vb = b.get(e.getKey());
            if (vb != null) dot += e.getValue() * vb;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 0.0001);
    }

    // 检索：从知识库里找出与问题最相关的一段，返回它的下标
    private int search(String question) {
        Map<String, Integer> qVec = toVector(question);
        int best = 0;
        double bestScore = -1;
        for (int i = 0; i < knowledge.length; i++) {
            double s = cosine(qVec, toVector(knowledge[i]));
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return best;
    }
}