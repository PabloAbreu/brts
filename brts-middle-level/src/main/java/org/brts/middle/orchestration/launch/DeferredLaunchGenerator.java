package org.brts.middle.orchestration.launch;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Accumulates the sequence of low-level build steps produced by the middle-level orchestrator and renders them into a
 * deferred-launch artifact (e.g. a shell script) that the user can run later to actually produce the disc.
 * <p>
 * Implementations decide how each semantic step is rendered (bash, PowerShell, direct in-process invocation, ...),
 * keeping the orchestrator itself free of any launch-mechanism-specific logic.
 */
public interface DeferredLaunchGenerator {

	/** Adds a human-readable comment/section marker ahead of the next step(s). */
	void comment(String text);

	/** Records an mkv-to-playlist conversion step. */
	void mkvToPlaylist(Path descriptorFile, Path bdmvOutputDir);

	/** Records a title-menu creation step. */
	void createTitleMenu(Path descriptorFile, Path discOutputDir, Path baseDir);

	/** Records an index.bdmv write step. */
	void indexWrite(Path indexFile, Path bdmvOutputDir);

	/** Records a MovieObject.bdmv write step. */
	void mobjWrite(Path moviesFile, Path bdmvOutputDir);

	/** Records a final user-facing message (e.g. a completion notice). */
	void message(String text);

	/**
	 * Finalizes and persists the launch artifact under {@code outputDir}.
	 *
	 * @return the path of the generated artifact
	 */
	Path write(Path outputDir) throws IOException;

}
