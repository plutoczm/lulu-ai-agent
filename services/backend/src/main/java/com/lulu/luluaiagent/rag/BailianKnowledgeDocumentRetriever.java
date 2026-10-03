package com.lulu.luluaiagent.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retriever for the 2026 Alibaba Cloud Model Studio RAG REST API.
 */
public class BailianKnowledgeDocumentRetriever implements DocumentRetriever {

    private final String apiKey;
    private final String workspaceId;
    private final String indexId;
    private final int topK;

    public BailianKnowledgeDocumentRetriever(
            String apiKey, String workspaceId, String indexId, int topK) {
        this.apiKey = apiKey;
        this.workspaceId = workspaceId;
        this.indexId = indexId;
        this.topK = topK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        validateConfiguration();

        String url = "https://" + workspaceId
                + ".cn-beijing.maas.aliyuncs.com"
                + "/api/v1/indices/rag/index/retrieve";

        JSONObject payload = new JSONObject();
        payload.set("index_id", indexId);
        payload.set("query", query.text());
        payload.set("top_k", topK);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(payload.toString())
                .timeout(30000)
                .execute()) {

            String body = response.body();
            if (!response.isOk()) {
                throw new IllegalStateException(
                        "Bailian RAG request failed with HTTP " + response.getStatus());
            }

            JSONObject root = JSONUtil.parseObj(body);
            if (!root.getBool("success", false)) {
                throw new IllegalStateException(
                        "Bailian RAG request failed: " + root.getStr("message", "unknown error"));
            }

            JSONObject data = root.getJSONObject("data");
            JSONArray nodes = data == null ? null : data.getJSONArray("nodes");
            List<Document> documents = new ArrayList<>();
            if (nodes == null) {
                return documents;
            }

            for (Object item : nodes) {
                JSONObject node = (JSONObject) item;
                String text = node.getStr("text");
                if (StrUtil.isBlank(text)) {
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                JSONObject rawMetadata = node.getJSONObject("metadata");
                if (rawMetadata != null) {
                    metadata.putAll(rawMetadata);
                }
                if (node.containsKey("score")) {
                    metadata.put("score", node.get("score"));
                }
                documents.add(new Document(text, metadata));
            }
            return documents;
        }
    }

    private void validateConfiguration() {
        if (StrUtil.isBlank(apiKey)
                || StrUtil.isBlank(workspaceId)
                || StrUtil.isBlank(indexId)) {
            throw new IllegalStateException(
                    "Bailian RAG requires api-key, workspace-id and index-id.");
        }
    }
}
