package org.brts.common.utils.composition;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.brts.common.utils.expressions.Expression;
import org.brts.common.utils.expressions.ObjectExpression;

import jakarta.el.ELManager;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link CompositionContext} that manages expression language evaluation within a specific frame
 * context.
 *
 * Note that an instance of this class is typically created for each frame being processed, allowing only to cache and
 * parse expressions within the same frame, but not across different frames.
 *
 * <p>
 * This class provides functionality to evaluate EL expressions and maintain frame-specific context information. It uses
 * the EL (Expression Language) API to create and evaluate value expressions dynamically.
 * </p>
 *
 * <p>
 * The context automatically registers the current frame number as a variable accessible within evaluated expressions.
 * </p>
 *
 * @author [Author Name]
 * @version 1.0
 * @see CompositionContext
 * @see ELManager
 */
@Getter
@Slf4j
public class CompositionContextImpl implements CompositionContext {

	private final int frameNumber;

	private final ELManager elManager = new ELManager();

	private final ExpressionFactory factory = ELManager.getExpressionFactory();

	// base path to resolve relative paths against
	private final Path basePath;

	public CompositionContextImpl(int frameNumber, ImagesComposition configuration, Path basePath) {
		this.frameNumber = frameNumber;
		setVariable("frameNumber", frameNumber);
		// insert all constants from configuration into context
		Map<String, ObjectExpression> constants = configuration.getConstants();
		if (constants != null)
			constants.forEach(this::setVariable);
		// set base path variable if not overriden by constants
		if (constants == null || !constants.containsKey("basePath"))
			setVariable("basePath", basePath.toFile().getAbsolutePath());
		this.basePath = Paths.get((String) eval(ObjectExpression.expr("${basePath}")));
		log.debug("Initialized CompositionContextImpl with frameNumber={}, basePath={}", frameNumber, this.basePath);
	}

	public void setVariable(String name, Expression value) {
		setVariable(name, eval(value));
	}

	public void setVariable(String name, Object value) {
		elManager.getELContext().getELResolver().setValue(elManager.getELContext(), null, name, value);
	}

	@Override
	public Object eval(Expression expression) {
		if (expression.isValue())
			return expression.getValue();
		ValueExpression exp = factory.createValueExpression(elManager.getELContext(), expression.getExpression(),
				Object.class);
		return exp.getValue(elManager.getELContext());
	}

	@Override
	public Path resolvePath(Path relativePath) {
		Path resolvedPath = basePath.resolve(relativePath).normalize();
		if (!resolvedPath.startsWith(basePath)) {
			// sanity check
			throw new IllegalArgumentException(
					"Resolved path " + resolvedPath + " is outside of base path " + basePath);
		}
		return resolvedPath;
	}

}
