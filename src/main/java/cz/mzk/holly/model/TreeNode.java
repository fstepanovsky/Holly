package cz.mzk.holly.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kremlacek
 */
public class TreeNode {

    private final String name;

    private final Map<String, TreeNode> subObjects;
    private final List<String> pagePaths = new LinkedList<>();

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

    public List<String> getPagePaths() {
        return Collections.unmodifiableList(pagePaths);
    }

    public String getName() {
        return name;
    }

}
