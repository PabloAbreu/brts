package org.brts.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;

public class ProcessUtils {

	@RequiredArgsConstructor
	public static class StreamGobbler extends Thread {

		private final InputStream input;

		protected Consumer<String> consumer = null;

		@Override
		public void run() {
			try (var reader = new BufferedReader(new InputStreamReader(input))) {
				String line;
				while ((line = reader.readLine()) != null) {
					consumer.accept(line);
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public static class StringStreamGobbler extends StreamGobbler {

		private final StringBuilder output = new StringBuilder();

		public StringStreamGobbler(InputStream input) {
			super(input);
			consumer = s -> output.append(s).append("\n");
		}

		public String getOutput() {
			return output.toString();
		}

	}

}
