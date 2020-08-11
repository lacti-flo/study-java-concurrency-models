package day01;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

class SentenceReader {
	public static String readSentence() {
		try {
			return String.join(" ", Files.readAllLines(Paths.get(WordCount.class.getResource("words").toURI()), StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new RuntimeException("IO Broken", e);
		}
	}
}
