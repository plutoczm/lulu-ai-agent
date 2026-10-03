package com.lulu.luluaiagent.agent;

import com.lulu.luluaiagent.model.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Lightweight semantic classifier for ambiguous natural-language requests.
 *
 * <p>This component never answers the user. It only selects one fixed route.
 * The selected route is then executed by deterministic workflows.</p>
 */
@Component
@Slf4j
public class SemanticIntentClassifier implements IntentClassifier {

    private final ChatClient client;

    public SemanticIntentClassifier(ModelRouter modelRouter) {
        this.client = ChatClient.builder(modelRouter.fastModel()).build();
    }

    @Override
    public Optional<AgentRequestRouter.Route> classify(String message) {
        if (message == null || message.isBlank()) {
            return Optional.of(AgentRequestRouter.Route.FAST_PATH);
        }

        String systemPrompt = """
                You are a routing classifier, not an assistant.
                Classify the user's request into exactly ONE label:

                CURRENT_TIME
                - The user wants the current clock time, current date, weekday, or equivalent
                  in any wording or language.
                - Examples: "what does the clock say right now", "此刻钟走到哪了",
                  "tell me today's date", "现在那边是几点".

                REALTIME_SEARCH
                - The answer depends on fresh/current external information that can change over time.
                - Includes current/latest products, availability, prices, releases, news, weather,
                  markets, sports, policies, public facts, schedules, versions, or recent events.
                - Examples: "眼下小米车系有哪些", "what cars can I buy from Xiaomi these days",
                  "最近这家公司有什么新动作", "今年刚出的那款现在还能买吗".

                AGENT
                - The user wants an external action or multi-step operation: open/read a URL,
                  manipulate files, run commands, download/create artifacts, use maps/images,
                  or carry out a tool-based task.

                FAST_PATH
                - Stable knowledge, explanation, writing, brainstorming, casual conversation,
                  relationship analysis, or anything that does not require current external facts
                  or external actions.

                Important:
                - Judge semantics, not keyword matching.
                - "I feel anxious right now" is FAST_PATH, not REALTIME_SEARCH.
                - "What products are available right now?" is REALTIME_SEARCH.
                - "Explain what time zones are" is FAST_PATH.
                - "What time is it in Tokyo right now?" is CURRENT_TIME.
                - Return ONLY the label. No punctuation, JSON, explanation, or extra text.
                """;

        try {
            String raw = client.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("Semantic intent classification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AgentRequestRouter.Route> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z_]", "");

        try {
            return Optional.of(AgentRequestRouter.Route.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            log.warn("Semantic classifier returned invalid label: {}", raw);
            return Optional.empty();
        }
    }
}
