// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.util.tree;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.joval.intf.util.tree.IForest;
import org.joval.intf.util.tree.INode;
import org.joval.intf.util.tree.ITree;
import org.joval.intf.util.tree.ITreeBuilder;
import org.joval.util.tree.Forest;
import org.joval.util.JOVALMsg;
import org.joval.util.JOVALSystem;
import org.joval.util.StringTools;
import org.joval.util.tree.Tree;

/**
 * An abstract tree that is intended to serve as a base class for ITree implementations whose access operations are too
 * expensive for direct, repeated use in searches.  The CachingTree stores search results in an in-memory cache for better
 * performance.
 *
 * The CachingTree provides methods (preload and preloadLinks) that should be overridden by subclasses to populate the cache in
 * bulk, and it also provides internal methods that convert regular expression searches into progressive tree node searches,
 * which are used when the preload methods return false.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public abstract class CachingTree implements ITree {
    private String ESCAPED_DELIM;
    protected IForest cache;

    public CachingTree() {
	cache = new Forest();
	ESCAPED_DELIM = Matcher.quoteReplacement(getDelimiter());
    }

    protected boolean preload() {
	return false;
    }

    protected boolean preloadLinks() {
	return false;
    }

    // Implement ITree (sparsely) -- subclasses must implement the getDelimiter and lookup methods.

    public INode getRoot() {
	throw new UnsupportedOperationException();
    }

    public Collection<String> search(Pattern p, boolean followLinks) {
	if (preload()) {
	    try {
		return cache.search(p, followLinks);
	    } catch (PatternSyntaxException e) {
		JOVALSystem.getLogger().warn(JOVALMsg.ERROR_PATTERN, p.pattern());
		JOVALSystem.getLogger().warn(JOVALSystem.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	    }
	    return null;
	} else {
	    return treeSearch(p.pattern(), followLinks);
	}
    }

    // Internal

    /**
     * Get the first token from the given path.  The delimiter is the escaped result of getDelimiter().
     */
    protected final String getToken(String path, String delim) {
	int ptr = path.indexOf(delim);
	if (ptr == -1) {
	    return path;
	} else {
	    return path.substring(0, ptr);
	}
    }

    /**
     * Remove the first token from the given path.  The delimiter is the escaped result of getDelimiter().
     */
    protected final String trimToken(String path, String delim) {
	int ptr = path.indexOf(delim);
	if (ptr == 0) {
	    return path.substring(1);
	} else if (ptr > 0) {
	    return path.substring(ptr + delim.length());
	} else {
	    return null;
	}
    }

    // Private

    /**
     * Search for a path.  This method converts the path string into tokens delimited by the separator character. Each token
     * is prepended with a ^ and appended with a $.  The method then iterates down the filesystem searching for each token,
     * in sequence, using the Matcher.find method.
     */
    private Collection<String> treeSearch(String path, boolean followLinks) {
	Collection<String> result = new Vector<String>();
	try {
	    if (path.startsWith("^")) {
		path = path.substring(1);
	    }
	    if (path.endsWith("$")) {
		path = path.substring(0, path.length()-1);
	    }
	    StringBuffer sb = new StringBuffer();
	    Iterator<String> iter = StringTools.tokenize(path, ESCAPED_DELIM, false);
	    for (int i=0; iter.hasNext(); i++) {
		String token = iter.next();
		if (token.length() > 0) {
		    boolean bound = i > 0 && token.indexOf(".*") == -1 && token.indexOf(".+") == -1;
		    if (bound && !token.startsWith("^")) {
			sb.append('^');
		    }
		    sb.append(token);
		    if (bound && !token.endsWith("$")) {
			sb.append('$');
		    }
		}
		if (iter.hasNext()) {
		    sb.append(ESCAPED_DELIM);
		}
	    }
	    result.addAll(treeSearch(null, sb.toString(), followLinks));
	} catch (Exception e) {
	    JOVALSystem.getLogger().warn(JOVALSystem.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	}
	return result;
    }

    /**
     * Search for a path on the tree, relative to the given parent path.  All the other search methods ultimately invoke
     * this one.  For the sake of efficiency, this class maintains a map of all the files and directories that it encounters
     * when searching for a path.  That way, it can resolve similar path searches very quickly without having to access the
     * underlying implementation.
     *
     * @arg parent	The parent path, which is a fully-resolved portion of the path (regex-free).
     * @arg path	The search pattern, consisting of ESCAPED_DELIM-delimited tokens for matching node names.
     * @arg followLinks	Specified whether or not filesystem links should be followed by the search.
     *
     * @returns a list of matching local paths
     *
     * @throws FileNotFoundException if a match cannot be found.
     */
    private Collection<String> treeSearch(String parent, String path, boolean followLinks) throws Exception {
	if (path == null || path.length() < 1) {
	    throw new IOException(JOVALSystem.getMessage(JOVALMsg.ERROR_FS_NULLPATH));
	}
	String parentName = parent == null ? "[root]" : parent;
	JOVALSystem.getLogger().trace(JOVALMsg.STATUS_FS_SEARCH, parentName, path);

	INode accessor = null;
	//
	// Advance to the starting position, which is either a root node or the node whose path is specified by parent.
	//
	ITreeBuilder tree = null;
	INode node = null;
	if (parent == null) {
	    String root = getToken(path, ESCAPED_DELIM);
	    tree = cache.getTreeBuilder(root);
	    if (tree == null) { // first-ever call
		tree = new Tree(root, getDelimiter());
		cache.addTree(tree);
	    }
	    node = tree.getRoot();
	    path = trimToken(path, ESCAPED_DELIM);
	} else {
	    String root = getToken(parent, getDelimiter());
	    tree = cache.getTreeBuilder(root);
	    if (tree == null) {
		tree = new Tree(root, getDelimiter());
		cache.addTree(tree);
		node = tree.getRoot();
		while ((parent = trimToken(parent, getDelimiter())) != null) {
		    node = tree.makeNode(node, getToken(parent, getDelimiter()));
		}
	    } else {
		node = tree.getRoot();
		try {
		    while ((parent = trimToken(parent, getDelimiter())) != null) {
			node = node.getChild(getToken(parent, getDelimiter()));
		    }
		} catch (NoSuchElementException e) {
		    do {
			node = tree.makeNode(node, getToken(parent, getDelimiter()));
		    } while ((parent = trimToken(parent, getDelimiter())) != null);
		} catch (UnsupportedOperationException e) {
		    do {
			node = tree.makeNode(node, getToken(parent, getDelimiter()));
		    } while ((parent = trimToken(parent, getDelimiter())) != null);
		}
	    }
	}

	//
	// Discover the node's children using the accessor, or fetch them from the cache.
	//
	boolean cacheRead = node.getType() == INode.Type.BRANCH;
	List<String> results = new Vector<String>();
	Collection<INode> children = null;
	if (cacheRead) {
	    children = node.getChildren();
	} else {
	    String nodePath = node.getPath();
	    if (nodePath.length() == 0) {
		nodePath = getDelimiter();
	    }
	    try {
		accessor = lookup(nodePath);
		if (accessor.getType() == INode.Type.LINK && !followLinks) {
		    return results;
		}
	    } catch (IllegalArgumentException e) {
	    } catch (NoSuchElementException e) {
		// the node has disappeared since being discovered
	    }
	    if (!nodePath.endsWith(getDelimiter())) {
		accessor = lookup(nodePath + getDelimiter());
	    }
	    try {
		if (accessor.hasChildren()) {
		    children = accessor.getChildren();
		    for (INode child : children) {
			tree.makeNode(node, child.getName());
		    }
		} else {
		    return results; // end of the line
		}
	    } catch (UnsupportedOperationException e) {
		return results; // accessor is a leaf
	    }
	}

	//
	// Search the children for the next token in the search path.
	//
	String token = getToken(path, ESCAPED_DELIM);
	path = trimToken(path, ESCAPED_DELIM);
	if (isBounded(token)) {
	    Pattern p = Pattern.compile(token);
	    for (INode child : children) {
	 	if (p.matcher(child.getName()).find()) {
		    if (path == null) {
			results.add(child.getPath());
		    } else {
			results.addAll(treeSearch(child.getPath(), path, followLinks));
		    }
		}
	    }
	} else if (".*".equals(token)) {
	    for (INode child : children) {
		results.add(child.getPath());
		results.addAll(treeSearch(child.getPath(), ".*", followLinks));
	    }
	} else {
	    //
	    // Optimization for wildcard-terminated searches
	    //
	    if (token.endsWith(".*") || token.endsWith(".+")) {
		StringBuffer sb = new StringBuffer(node.getPath()).append(getDelimiter());
		String prefix = sb.append(token.substring(0, token.length() - 2)).toString();
		if (prefix.indexOf(".*") == -1 && prefix.indexOf(".+") == -1) {
		    for (INode child : children) {
			String childPath = child.getPath();
			if (childPath.startsWith(prefix)) {
			    if (token.endsWith(".*")) {
				results.add(childPath);
			    } else if (token.endsWith(".+") && childPath.length() > prefix.length()) {
				results.add(childPath);
			    }
			    results.addAll(treeSearch(childPath, ".*", followLinks));
			}
		    }
		    return results;
		}
	    }

	    //
	    // General-purpose algorithm:
	    // If the token is not bounded, then recursively gather all children, then filter for matches
	    //
	    Vector<String> candidates = new Vector<String>();
	    for (INode child : children) {
		candidates.add(child.getPath());
		candidates.addAll(treeSearch(child.getPath(), ".*", followLinks));
	    }

	    StringBuffer pattern = new StringBuffer(StringTools.escapeRegex(node.getPath()));
	    pattern.append(ESCAPED_DELIM).append(token);
	    if (path != null) {
		// Reconstruct the remaining path and append it to the pattern
		do {
		    pattern.append(ESCAPED_DELIM);
		    token = getToken(path, ESCAPED_DELIM);
		    if (isBounded(token)) {
			pattern.append(token.substring(1, token.length() - 1));
		    } else {
			pattern.append(token);
		    }
		} while ((path = trimToken(path, ESCAPED_DELIM)) != null);
	    }

	    for (String candidate : candidates) {
		if (Pattern.matches(pattern.toString(), candidate)) {
		    results.add(candidate);
		}
	    }
	}
	return results;
    }

    /**
     * Returns whether the token represents a pattern for an entire node name.
     */
    private boolean isBounded(String token) {
	return token.startsWith("^") && token.endsWith("$");
    }
}
