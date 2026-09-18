package org.brts.middle.orchestration.launch;

import java.io.IOException;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;

/**
 * An implementation of {@link AbstractDeferredLaunchGenerator} that immediately executes each CLI invocation, and
 * outputs messages/comments to the standard error.
 */
@RequiredArgsConstructor
public class ImmediateLaunchGenerator extends AbstractDeferredLaunchGenerator {
	private final BrtsImmediateInvocator invocator;

	@Override
	protected void invoke(BrtsCliInvocation invocation) {
		// run in process with BrtsMain
		// so we don't worry about paths
		// and we don't have to wait for JVM startup for each invocation
		try {
			invocator.invoke(invocation.toTokens().toArray(new String[0]));
		} catch (Exception e) {
			throw new RuntimeException("Failed to invoke BRTS CLI", e);
		}
	}

	@Override
	public void comment(String text) {
		System.err.println("# " + text);
	}

	@Override
	public void message(String text) {
		System.err.println(text);
	}

	@Override
	public Path write(Path outputDir) throws IOException {
		// nothing to write, since this generator executes immediately
		return null;
	}
}
