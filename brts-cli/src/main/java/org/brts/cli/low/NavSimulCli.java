package org.brts.cli.low;

import com.fasterxml.jackson.core.type.TypeReference;
import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.bdmv.NavigationCommandSimulator;
import org.brts.lowlevel.bdmv.NavigationCommandSimulator.SimulationResult;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI for the HDMV navigation command simulator.
 */
public class NavSimulCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "JSON file containing an array of NavigationCommand objects")
		File input;

		@Option(name = "--psr", usage = "JSON file with initial PSR values, e.g. {\"PSR4\": 1, \"PSR10\": 5}")
		File psr;

		@Option(name = "--max-steps", usage = "Maximum number of instructions to execute (default: 1000000)")
		long maxSteps = 1_000_000;

	}

	public static class Simul extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "nav-simul";
		}

		@Override
		public String getDescription() {
			return "Simulate HDMV navigation commands with virtual registers";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			// Read navigation commands
			List<NavigationCommand> commands = loadJson(opts.input, new TypeReference<List<NavigationCommand>>() {
			});

			// Read PSR initialization (optional)
			Map<Integer, Long> psrInit = null;
			if (opts.psr != null) {
				Map<String, Long> raw = loadJson(opts.psr, new TypeReference<Map<String, Long>>() {
				});
				psrInit = new HashMap<>();
				for (var entry : raw.entrySet()) {
					String key = entry.getKey().trim();
					if (!key.toUpperCase().startsWith("PSR")) {
						System.err.println(
								"nav-simul: invalid PSR key '" + key + "' — expected format PSR<N>, e.g. PSR4");
						System.exit(1);
					}
					int idx = Integer.parseInt(key.substring(3));
					psrInit.put(idx, entry.getValue());
				}
			}

			// Run simulation
			NavigationCommandSimulator simulator = new NavigationCommandSimulator(psrInit, opts.maxSteps);
			SimulationResult result;
			try {
				result = simulator.run(commands);
			} catch (NavigationCommandSimulator.SimulationException e) {
				System.err.println("nav-simul: simulation error — " + e.getMessage());
				System.exit(1);
				return;
			}

			// Print results
			if (!result.externalEffects().isEmpty()) {
				System.out.println("External effects:");
				for (String effect : result.externalEffects()) {
					System.out.println("  " + effect);
				}
			}

			if (!result.finalGprState().isEmpty()) {
				System.out.println("Final GPR state:");
				for (var entry : result.finalGprState().entrySet()) {
					System.out.printf("  GPR[%d] = %d (0x%08X)%n", entry.getKey(), entry.getValue(), entry.getValue());
				}
			}

			System.out.printf("Terminated: %s (after %d steps, final pc=%d)%n", result.terminationReason(),
					result.stepsExecuted(), result.finalPc());
		}

	}

}
