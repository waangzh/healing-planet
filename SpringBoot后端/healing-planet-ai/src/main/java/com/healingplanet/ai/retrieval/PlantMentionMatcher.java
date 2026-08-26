package com.healingplanet.ai.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable trie for deterministic catalog mention matching. */
final class PlantMentionMatcher {
    private final Node root;

    private PlantMentionMatcher(Node root) { this.root = root; }

    static PlantMentionMatcher build(Map<String, List<PlantNameBinding>> bindings) {
        Node root = new Node();
        bindings.forEach((name, values) -> {
            Node node = root;
            for (int i = 0; i < name.length(); i++) {
                node = node.children.computeIfAbsent(name.charAt(i), ignored -> new Node());
            }
            node.bindings = List.copyOf(values);
        });
        return new PlantMentionMatcher(root);
    }

    static PlantMentionMatcher empty() { return new PlantMentionMatcher(new Node()); }

    /** Longest match wins at each position; bindings preserve alias collisions. */
    List<PlantMention> find(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) return List.of();
        List<PlantMention> matches = new ArrayList<>();
        for (int start = 0; start < normalizedQuery.length();) {
            Node node = root;
            int longestEnd = -1;
            List<PlantNameBinding> longestBindings = List.of();
            for (int cursor = start; cursor < normalizedQuery.length(); cursor++) {
                node = node.children.get(normalizedQuery.charAt(cursor));
                if (node == null) break;
                if (!node.bindings.isEmpty()) {
                    longestEnd = cursor + 1;
                    longestBindings = node.bindings;
                }
            }
            if (longestEnd < 0) {
                start++;
            } else {
                matches.add(new PlantMention(normalizedQuery.substring(start, longestEnd), start,
                        longestEnd, longestBindings));
                start = longestEnd;
            }
        }
        return List.copyOf(matches);
    }

    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private List<PlantNameBinding> bindings = List.of();
    }
}
