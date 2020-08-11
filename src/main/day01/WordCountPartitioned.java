package day01;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WordCountPartitioned {
	
	public static void main(String[] args) throws IOException, URISyntaxException {
		final String sentence = String.join(" ", Files.readAllLines(Paths.get(WordCount.class.getResource("words").toURI()), StandardCharsets.UTF_8));
		
		final Map<String, Long> expected = WordCount.countWordsInSequential(sentence);
		for (int i = 0; i < 1000; i++) {
			final Map<String, Long> actual = WordCountPartitioned.countWordsInParallel(sentence);
			if (!expected.equals(actual)) {
				throw new RuntimeException("Broken!");
			}
		}
	}
	
	static Map<String, Long> countWordsInParallel(String sentence) {
		return Arrays.stream(sentence.split("\\s+"))
				.collect(Collectors.groupingByConcurrent(w -> w.charAt(0)))
				.values()
				.parallelStream()
				.map(WordCountPartitioned::localReduceCount)
				.reduce(WordCountPartitioned::mergeAll)
				.orElse(new HashMap<>());
	}
	
	static Map<String, Long> localReduceCount(List<String> localWords) {
		return localWords.stream()
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}
	
	static Map<String, Long> mergeAll(Map<String, Long> map1, Map<String, Long> map2) {
		Map<String, Long> merged = new HashMap<>(map1);
		merged.putAll(map2);
		return merged;
//		map1.putAll(map2);
//		return map1;
	}
}
