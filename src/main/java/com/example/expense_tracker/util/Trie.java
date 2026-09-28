package com.example.expense_tracker.util;

import java.util.*;

/**
 * Trie (Prefix Tree) data structure for ultra-fast, ranked text autocomplete.
 * Supports case-insensitive prefix search and frequency-weighted ranking.
 *
 * Time Complexity:
 *  - Insert(word): O(L) where L is length of the word
 *  - Autocomplete(prefix, limit): O(P + N) where P is prefix length and N is nodes traversed
 * Space Complexity:
 *  - O(Alphabet_Size * Average_Word_Length * Total_Words)
 */
public class Trie {

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord = false;
        int frequency = 0;
        String fullWord = null;
    }

    private final TrieNode root;

    public Trie() {
        this.root = new TrieNode();
    }

    /**
     * Inserts a word into the Trie or increments its frequency count.
     */
    public synchronized void insert(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }
        String normalized = word.trim().toLowerCase();
        TrieNode current = root;

        for (char ch : normalized.toCharArray()) {
            current = current.children.computeIfAbsent(ch, c -> new TrieNode());
        }
        current.isEndOfWord = true;
        current.frequency++;
        current.fullWord = word.trim(); // preserve original casing for presentation
    }

    /**
     * Suggests the top-N words matching the specified prefix, ranked by frequency.
     */
    public List<String> autocomplete(String prefix, int maxResults) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedPrefix = prefix.trim().toLowerCase();
        TrieNode current = root;

        for (char ch : normalizedPrefix.toCharArray()) {
            current = current.children.get(ch);
            if (current == null) {
                return Collections.emptyList(); // Prefix not found
            }
        }

        // Collect all completions under this subtree
        List<TrieNode> results = new ArrayList<>();
        collectAllWords(current, results);

        // Sort by frequency descending, then alphabetically
        results.sort((a, b) -> {
            int freqCompare = Integer.compare(b.frequency, a.frequency);
            if (freqCompare != 0) return freqCompare;
            return a.fullWord.compareToIgnoreCase(b.fullWord);
        });

        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, results.size()); i++) {
            suggestions.add(results.get(i).fullWord);
        }
        return suggestions;
    }

    private void collectAllWords(TrieNode node, List<TrieNode> results) {
        if (node.isEndOfWord && node.fullWord != null) {
            results.add(node);
        }
        for (TrieNode child : node.children.values()) {
            collectAllWords(child, results);
        }
    }
}
