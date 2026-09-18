package org.brts.middle.orchestration.launch;

import java.nio.file.Path;

/**
 * Translates the semantic {@link DeferredLaunchGenerator} build steps into {@link BrtsCliInvocation} instances, keeping
 * the knowledge of each command's level/name/arguments independent of how it is ultimately rendered.
 */
public abstract class AbstractDeferredLaunchGenerator implements DeferredLaunchGenerator {

	/** Renders a single resolved CLI invocation using this generator's syntax. */
	protected abstract void invoke(BrtsCliInvocation invocation);

	@Override
	public void mkvToPlaylist(Path descriptorFile, Path bdmvOutputDir) {
		invoke(BrtsCliInvocation.mkvToPlaylist(descriptorFile, bdmvOutputDir));
	}

	@Override
	public void createTitleMenu(Path descriptorFile, Path discOutputDir, Path baseDir) {
		invoke(BrtsCliInvocation.createTitleMenu(descriptorFile, discOutputDir, baseDir));
	}

	@Override
	public void indexWrite(Path indexFile, Path bdmvOutputDir) {
		invoke(BrtsCliInvocation.indexWrite(indexFile, bdmvOutputDir));
	}

	@Override
	public void mobjWrite(Path moviesFile, Path bdmvOutputDir) {
		invoke(BrtsCliInvocation.mobjWrite(moviesFile, bdmvOutputDir));
	}

}
