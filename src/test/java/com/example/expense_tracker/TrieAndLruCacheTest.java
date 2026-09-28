package com.example.expense_tracker;

import com.example.expense_tracker.util.LruCache;
import com.example.expense_tracker.util.Trie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TrieAndLruCacheTest {

    @Test
    @DisplayName("Trie prefix autocomplete and frequency ranking")
    void testTrieAutocomplete() {
        Trie trie = new Trie();
        trie.insert("Amazon");
        trie.insert("Amazon Prime");
        trie.insert("Amazon Web Services");
        trie.insert("Apple");
        trie.insert("Alphabet");

        // Bump frequency of AWS
        trie.insert("Amazon Web Services");
        trie.insert("Amazon Web Services");

        List<String> suggestions = trie.autocomplete("am", 5);
        assertThat(suggestions).hasSize(3);
        // Amazon Web Services has frequency 3, so it should rank first
        assertThat(suggestions.get(0)).isEqualTo("Amazon Web Services");
    }

    @Test
    @DisplayName("LRU Cache constant time get/put and eviction policy")
    void testLruCacheEviction() {
        LruCache<String, Integer> cache = new LruCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache.get("a")).isEqualTo(1); // 'a' accessed, so 'b' becomes LRU

        // Add 4th item, should evict 'b'
        cache.put("d", 4);

        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("a")).isEqualTo(1);
        assertThat(cache.get("c")).isEqualTo(3);
        assertThat(cache.get("d")).isEqualTo(4);

        assertThat(cache.getHits()).isGreaterThan(0);
    }
}
