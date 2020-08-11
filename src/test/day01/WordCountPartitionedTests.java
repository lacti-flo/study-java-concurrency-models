package day01;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class WordCountPartitionedTests {
	@Test
	public void countWithPartitions() {
		final String sentence = SentenceReader.readSentence();
		final Map<String, Long> expected = WordCount.countWordsInSequential(sentence);
		for (int i = 0; i < 1000; i++) {
			final Map<String, Long> actual = WordCountPartitioned.countWordsInParallel(sentence);
			if (!expected.equals(actual)) {
				throw new RuntimeException("Broken!");
			}
		}
	}
}
