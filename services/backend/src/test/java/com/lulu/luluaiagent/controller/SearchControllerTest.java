package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.auth.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchControllerTest {

    private final SearchController controller = new SearchController();

    @Test
    void searchesRealKnowledgeFiles() {
        MockHttpServletRequest request = authenticatedRequest();

        SearchController.SearchResponse response =
                controller.search("沟通", 20, "all", request);

        assertTrue(response.results().stream()
                .anyMatch(result -> "knowledge".equals(result.type())));
    }

    @Test
    void listsKnowledgeLibraryWhenQueryIsBlank() {
        MockHttpServletRequest request = authenticatedRequest();

        SearchController.SearchResponse response =
                controller.search("", 30, "knowledge", request);

        assertTrue(response.total() >= 30);
        assertTrue(response.results().stream()
                .allMatch(result -> "knowledge".equals(result.type())));
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                AuthSupport.USER_ATTR,
                new AuthUser(
                        UUID.randomUUID(),
                        "search-test@example.com",
                        "Search Test",
                        false));
        return request;
    }
}
