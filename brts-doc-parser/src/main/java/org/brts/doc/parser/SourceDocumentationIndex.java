package org.brts.doc.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

final class SourceDocumentationIndex {

	private final Map<String, String> typeDocumentation = new LinkedHashMap<>();
	private final Map<String, String> fieldDocumentation = new LinkedHashMap<>();

	static SourceDocumentationIndex create(List<Path> sourceRoots) throws IOException {
		SourceDocumentationIndex index = new SourceDocumentationIndex();
		List<Path> sources = new ArrayList<>();
		for (Path root : sourceRoots) {
			if (Files.isDirectory(root)) {
				try (var paths = Files.walk(root)) {
					paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
				}
			}
		}
		if (sources.isEmpty()) {
			return index;
		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("A JDK is required to extract source Javadocs");
		}
		try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
			JavacTask task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null,
					files.getJavaFileObjectsFromPaths(sources));
			DocTrees docTrees = DocTrees.instance(task);
			for (CompilationUnitTree unit : task.parse()) {
				new DocumentationScanner(index, docTrees, unit).scan(unit, null);
			}
		}
		return index;
	}

	String typeDescription(Class<?> type) {
		return typeDocumentation.get(type.getCanonicalName());
	}

	String fieldDescription(Class<?> declaringType, String fieldName) {
		return fieldDocumentation.get(declaringType.getCanonicalName() + "#" + fieldName);
	}

	private static String render(DocCommentTree documentation) {
		if (documentation == null) {
			return null;
		}
		String text = documentation.toString().strip().replaceAll("[\\t ]+", " ").replaceAll(" ?\\n ?", "\n")
				.replaceAll("\n{3,}", "\n\n");
		return text.isBlank() ? null : text;
	}

	private static final class DocumentationScanner extends TreePathScanner<Void, Void> {

		private final SourceDocumentationIndex index;
		private final DocTrees docTrees;
		private final String packageName;
		private final Deque<String> types = new ArrayDeque<>();

		private DocumentationScanner(SourceDocumentationIndex index, DocTrees docTrees, CompilationUnitTree unit) {
			this.index = index;
			this.docTrees = docTrees;
			this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
		}

		@Override
		public Void visitClass(ClassTree node, Void unused) {
			types.addLast(node.getSimpleName().toString());
			index.typeDocumentation.put(qualifiedTypeName(), render(docTrees.getDocCommentTree(getCurrentPath())));
			super.visitClass(node, unused);
			types.removeLast();
			return null;
		}

		@Override
		public Void visitVariable(VariableTree node, Void unused) {
			TreePath parent = getCurrentPath().getParentPath();
			if (!types.isEmpty() && parent != null && parent.getLeaf() instanceof ClassTree) {
				index.fieldDocumentation.put(qualifiedTypeName() + "#" + node.getName(),
						render(docTrees.getDocCommentTree(getCurrentPath())));
			}
			return super.visitVariable(node, unused);
		}

		private String qualifiedTypeName() {
			String localName = String.join(".", types);
			return packageName.isEmpty() ? localName : packageName + "." + localName;
		}
	}
}