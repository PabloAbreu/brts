package org.brts.common.utils.composition;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brts.common.utils.expressions.Expression;
import org.brts.common.utils.expressions.ObjectExpression;

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;
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

	private final ELContext elContext = elManager.getELContext();

	private final VariableMapper variableMapper = elContext.getVariableMapper();
	private final List<String> variableDefinitionOrder = new ArrayList<>();

	// base path to resolve relative paths against
	private final Path basePath;

	public CompositionContextImpl(int frameNumber, ImagesComposition configuration, Path basePath) {
		this.frameNumber = frameNumber;
		setVariable("frameNumber", frameNumber);
		// insert all constants from configuration into context
		Map<String, ObjectExpression> constants = configuration.getConstants();
		// set base path variable if not overriden by constants
		if (constants == null || !constants.containsKey("basePath"))
			setVariable("basePath", basePath.toFile().getAbsolutePath());
		if (constants != null)
			constants.forEach(this::setVariable);
		this.basePath = Paths.get((String) eval(ObjectExpression.expr("${basePath}")));
		log.trace("Initialized CompositionContextImpl with frameNumber={}, basePath={}", frameNumber, this.basePath);
	}

	public final void setVariable(String name, Expression value) {
		if (value.isValue()) {
			setVariable(name, value.getValue());
		} else {
			variableDefinitionOrder.add(name);
			variableMapper.setVariable(name, new LazyValueExpression(value, this));
		}
	}

	private void setVariable(String name, Object value) {
		variableDefinitionOrder.add(name);
		variableMapper.setVariable(name, factory.createValueExpression(value, Object.class));
	}

	@Override
	public Object eval(Expression expression) {
		if (expression.isValue())
			return expression.getValue();
		ValueExpression exp = factory.createValueExpression(elContext, expression.getExpression(), Object.class);
		try {
			return exp.getValue(elContext);
		} catch (Exception e) {
			// log all available context variables for debugging
			log.error("Error evaluating expression '{}'. Available variables: {}", expression.getExpression(),
					variableDefinitionOrder);
			throw new RuntimeException("Failed to evaluate expression: " + expression.getExpression(), e);
		}
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
