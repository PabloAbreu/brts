package org.brts.cli;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MethodSetter;
import org.kohsuke.args4j.spi.Setters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for CLI feature runners.
 * <p>
 * Each concrete subclass represents a single sub-command (e.g. {@code clip-parse}, {@code m2ts-info}). It knows how to
 * describe itself for usage output and how to parse its own args4j options bean before delegating to {@link #execute}.
 *
 * @param <O> the args4j options-bean type
 */
@Slf4j
public abstract class FeatureRunner<O extends BaseOptions> {

	/**
	 * Checks whether the raw, unparsed command-line arguments request the banner to be suppressed (see
	 * {@link BaseOptions#isNoBanner()}). Used before args4j parsing has taken place, e.g. by {@code BrtsMain}.
	 */
	public static boolean isBannerSuppressed(String[] args) {
		if (args == null) {
			return false;
		}
		for (String arg : args) {
			if (BaseOptions.OPTION_NO_BANNER.equals(arg)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The sub-command name as typed on the command line (e.g. {@code "clip-parse"}).
	 */
	public abstract String getCommandName();

	/** A short one-line description for usage/help output. */
	public abstract String getDescription();

	/**
	 * Executes the feature logic after arguments have been parsed into {@code opts}.
	 */
	protected abstract void execute(O opts) throws Exception;

	/** Creates a fresh, empty options bean to be populated by args4j. */
	protected O createOptions() {
		// determine concrete type of O via reflection and verify it has a no-arg
		// constructor
		// then we can safely instantiate it here instead of forcing each subclass to
		// implement createOptions()
		// (this is a bit of reflection magic to avoid boilerplate in each subclass)
		// see https://stackoverflow.com/a/3403985/4561887 for the general technique of
		// determining generic type parameters at runtime
		// and https://stackoverflow.com/a/182928/4561887 for the specific pattern of
		// verifying a no-arg constructor
		Class<?> clazz = this.getClass();
		while (clazz != null) {
			if (clazz.getSuperclass() == FeatureRunner.class) {
				// found the direct subclass of FeatureRunner, now determine the type
				// parameter
				// O
				Type genericSuperclass = clazz.getGenericSuperclass();
				if (genericSuperclass instanceof ParameterizedType parameterizedType) {
					Type[] typeArgs = parameterizedType.getActualTypeArguments();
					if (typeArgs.length == 1) {
						Type typeArg = typeArgs[0];
						if (typeArg instanceof Class) {
							Class<?> optionsClass = (Class<?>) typeArg;
							try {
								@SuppressWarnings("unchecked")
								O options = (O) optionsClass.getDeclaredConstructor().newInstance();
								return options;
							} catch (Exception e) {
								throw new IllegalStateException("Options class must have a no-arg constructor", e);
							}
						}
					}
				}
			}
			clazz = clazz.getSuperclass();
		}

		throw new IllegalStateException("Unable to determine options class");
	}

	/**
	 * Parses command-line arguments into the options bean and invokes {@link #execute}. On parse errors, prints usage
	 * to {@code stderr} and exits.
	 */
	public void run(String[] args) throws Exception {

		if (args.length == 1 && "--help".equals(args[0])) {
			printUsage(System.err);
			return;
		}
		O opts = createOptions();
		try {
			// always create a new parser for each run to avoid reusing state from previous runs
			// this is a concern mostly for unit tests that invoke run() multiple times in the same JVM
			CmdLineParser parser = new FeatureRunnerCmdLineParser(opts);
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(getCommandName() + ": " + e.getMessage());
			printUsage(System.err);
			if (opts.isErrorDetails()) {
				System.err.println(getCommandName() + " failed. Details:\n");
				e.printStackTrace();
			}
			System.exit(1);
		}

		try {
			validateOptions(opts);
		} catch (DescriptorValidationException e) {
			System.err.println(getCommandName() + ": " + e.getMessage());
			if (opts.isErrorDetails()) {
				e.printStackTrace();
			}
			System.exit(1);
		}

		try {
			execute(opts);
		} catch (Exception e) {
			if (opts.isErrorDetails()) {
				System.err.println(getCommandName() + " failed. Details:\n");
				e.printStackTrace();
			}
			throw e;
		}
	}

	/**
	 * Validates the fully-parsed options bean before {@link #execute} runs, so that bad input is reported up-front
	 * rather than failing mid-processing. Override to add programmatic checks beyond the declared constraints.
	 */
	protected void validateOptions(O opts) {
		DescriptorValidator.validateOrThrow(opts, "arguments for '" + getCommandName() + "'");
	}

	private static class FeatureRunnerCmdLineParser extends CmdLineParser {

		public FeatureRunnerCmdLineParser(Object bean) {
			super(bean);
			// no manage custom annotations
			// support for @JsonInputOption
			parseJsonInputOption(bean);
		}

		@RequiredArgsConstructor
		private static class JsonInputOptionAsOption implements Option {

			private final JsonInputOption o;

			@Override
			public String name() {
				return o.name();
			}

			@Override
			public String usage() {
				return o.usage();
			}

			@Override
			public boolean required() {
				return o.required();
			}

			@Override
			public String[] aliases() {
				return o.aliases();
			}

			@Override
			public String metaVar() {
				return o.metaVar();
			}

			@Override
			public boolean help() {
				return o.help();
			}

			@Override
			public boolean hidden() {
				return o.hidden();
			}

			@Override
			public String[] depends() {
				return o.depends();
			}

			@Override
			public String[] forbids() {
				return o.forbids();
			}

			@Override
			public Class<JsonInputOptionHandler> handler() {
				return JsonInputOptionHandler.class;
			}

			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return Option.class;
			}

		}

		protected void parseJsonInputOption(Object bean) {
			// recursively process all the methods/fields.
			for (Class<?> c = bean.getClass(); c != null; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					JsonInputOption o = m.getAnnotation(JsonInputOption.class);
					if (o != null)
						addOption(new MethodSetter(this, bean, m), new JsonInputOptionAsOption(o));
				}

				for (Field f : c.getDeclaredFields()) {
					JsonInputOption o = f.getAnnotation(JsonInputOption.class);
					if (o != null)
						addOption(Setters.create(f, bean), new JsonInputOptionAsOption(o));
				}
			}
		}

	}

	/**
	 * Prints the command name, description, and args4j option details to the given stream.
	 */
	public void printUsage(PrintStream out) {
		out.println("  " + getCommandName() + " — " + getDescription());
		CmdLineParser parser = new FeatureRunnerCmdLineParser(createOptions());
		parser.printUsage(out);
		out.println();
	}

	// -----------------------------------------------------------------
	// JSON convenience methods
	// -----------------------------------------------------------------

	protected <T> T loadJson(File file, Class<T> type) throws Exception {
		return JsonMapperFactory.get().readValue(file, type);
	}

	protected <T> T loadJson(File file, TypeReference<T> typeRef) throws Exception {
		return JsonMapperFactory.get().readValue(file, typeRef);
	}

	/**
	 * Writes {@code value} as JSON. If {@code output} is non-null, writes to that file; otherwise pretty-prints to
	 * stdout.
	 */
	protected void writeJson(File output, Object value) throws Exception {
		ObjectMapper mapper = JsonMapperFactory.get();
		if (output != null) {
			mapper.writeValue(output, value);
		} else {
			System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
		}
	}

}
