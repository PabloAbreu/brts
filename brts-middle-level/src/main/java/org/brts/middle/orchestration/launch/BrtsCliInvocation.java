package org.brts.middle.orchestration.launch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A CLI-agnostic description of a single {@code brts} invocation: which level dispatcher to use, which command, and
 * which ordered flag/value arguments to pass. Renderer-specific {@link DeferredLaunchGenerator} implementations (bash,
 * PowerShell, direct in-process invocation, ...) turn this into their own syntax via {@link #toTokens()}.
 */
public record BrtsCliInvocation(String level, String command, List<Argument> arguments) {

	/** A single {@code --flag value} pair, in the order it must appear on the command line. */
	public record Argument(String flag, String value) {
	}

	/** Flattens this invocation into ordered tokens: level, command, then each flag/value pair. */
	public List<String> toTokens() {
		List<String> tokens = new ArrayList<>();
		tokens.add(level);
		tokens.add(command);
		for (Argument argument : arguments) {
			tokens.add(argument.flag());
			tokens.add(argument.value());
		}
		return tokens;
	}

	private static String toAbsolute(Path path) {
		return path.toAbsolutePath().toString();
	}

	public static BrtsCliInvocation mkvToPlaylist(Path descriptorFile, Path bdmvOutputDir) {
		return new BrtsCliInvocation("low", "mkv-to-playlist",
				List.of(new Argument("--descriptor", toAbsolute(descriptorFile)),
						new Argument("--output", toAbsolute(bdmvOutputDir))));
	}

	public static BrtsCliInvocation createTitleMenu(Path descriptorFile, Path discOutputDir, Path baseDir) {
		return new BrtsCliInvocation("low", "create-title-menu",
				List.of(new Argument("--descriptor", toAbsolute(descriptorFile)),
						new Argument("--output", toAbsolute(discOutputDir)),
						new Argument("--base-dir", toAbsolute(baseDir))));
	}

	public static BrtsCliInvocation indexWrite(Path indexFile, Path bdmvOutputDir) {
		return new BrtsCliInvocation("low", "index-write", List.of(new Argument("--input", toAbsolute(indexFile)),
				new Argument("--output", toAbsolute(bdmvOutputDir))));
	}

	public static BrtsCliInvocation mobjWrite(Path moviesFile, Path bdmvOutputDir) {
		return new BrtsCliInvocation("low", "mobj-write", List.of(new Argument("--input", toAbsolute(moviesFile)),
				new Argument("--output", toAbsolute(bdmvOutputDir))));
	}

}
