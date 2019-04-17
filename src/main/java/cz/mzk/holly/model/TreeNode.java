package cz.mzk.holly.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kremlacek
 */
public class TreeNode {

    private final String name;

    private final Map<String, TreeNode> subObjects;
    private final Set<String> pagePaths = new LinkedHashSet<>();

    public TreeNode(boolean parallel, String name) {
        this.name = name;

        if (parallel) {
            subObjects = new ConcurrentHashMap<>();
        } else {
            subObjects = new HashMap<>();
        }
    }

    public TreeNode createSubTree(String name) {
        var subTree = new TreeNode(false, name);

        subObjects.put(name, subTree);

        return subTree;
    }

    public Map<String, TreeNode> getSubTree() {
        return Collections.unmodifiableMap(subObjects);
    }

    public void addPagePath(String page) {
        pagePaths.add(page);
    }

    public Set<String> getPagePaths() {
        return Collections.unmodifiableSet(pagePaths);
    }

    public String getName() {
        return name;
    }

}
