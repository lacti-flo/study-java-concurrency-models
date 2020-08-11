package day01;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class WordCountTests {
	@Test
	public void countSequential() {
		Map<String, Long> previous = WordCount.countWordsInSequential(SentenceReader.readSentence());
		for (int i = 0; i < 100; ++i) {
			Map<String, Long> next = WordCount.countWordsInSequential(SentenceReader.readSentence());
			if (!previous.equals(next)) {
				throw new RuntimeException("Broken!");
			}
			previous = next;
		}
	}
}
