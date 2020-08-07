package day01;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WordCount {
	
	public static void main(String[] args) throws IOException, URISyntaxException {
		final String sentence = String.join(" ", Files.readAllLines(Paths.get(WordCount.class.getResource("words").toURI()), StandardCharsets.UTF_8));
		System.out.println(countWordsInSequential(sentence));
	}
	
	static Map<String, Long> countWordsInSequential(String sentence) {
		return Arrays.stream(sentence.split("\\s+")) // map
//				.parallel() // 문제 없나?
				.reduce(new HashMap<>(), WordCount::reduceCount, WordCount::mergeMap); // reduce
	}
	
	
	private static Map<String, Long> reduceCount(Map<String, Long> map, String word) {
		Map<String, Long> newMap = new HashMap<>(map);
		newMap.compute(word, (unused, oldMaybe) -> oldMaybe != null ? oldMaybe + 1 : 1);
		return newMap;
	}
	
	private static Map<String, Long> mergeMap(Map<String, Long> map1, Map<String, Long> map2) {
		Map<String, Long> merged = new HashMap<>(map1);
		merged.putAll(map2);
		return merged;
	}
}
