package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.github.diudiuseven.dashboardbuilderenginejava.domain.ComponentNode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Stateless depth-first operations on the nested component tree.
 */
@Component
public class ComponentTree {

    /** Find a node anywhere in the tree by id. */
    public Optional<ComponentNode> findById(List<ComponentNode> roots, String id) {
        if (id == null) {
            return Optional.empty();
        }
        Deque<ComponentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            ComponentNode node = stack.pop();
            if (id.equals(node.getId())) {
                return Optional.of(node);
            }
            // push in reverse so traversal order matches document order
            List<ComponentNode> kids = node.getChildren();
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.push(kids.get(i));
            }
        }
        return Optional.empty();
    }

    /** Find the node that directly contains the node with the given id. */
    public Optional<ComponentNode> findParent(List<ComponentNode> roots, String id) {
        Deque<ComponentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            ComponentNode node = stack.pop();
            for (ComponentNode child : node.getChildren()) {
                if (id.equals(child.getId())) {
                    return Optional.of(node);
                }
            }
            for (ComponentNode child : node.getChildren()) {
                stack.push(child);
            }
        }
        return Optional.empty();
    }

    /** Collect the id of the given node and every descendant id. */
    public List<String> subtreeIds(ComponentNode root) {
        List<String> ids = new ArrayList<>();
        Deque<ComponentNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ComponentNode node = stack.pop();
            ids.add(node.getId());
            node.getChildren().forEach(stack::push);
        }
        return ids;
    }

    /**
     * Remove the node with the given id together with its whole subtree.
     * Detaching the parent's child reference ensures no dangling node is
     * left reachable from the root list.
     *
     * @return true if a node was removed
     */
    public boolean removeWithSubtree(List<ComponentNode> roots, String id) {
        if (roots.removeIf(n -> id.equals(n.getId()))) {
            return true;
        }
        Deque<ComponentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            ComponentNode node = stack.pop();
            if (node.getChildren().removeIf(c -> id.equals(c.getId()))) {
                return true;
            }
            node.getChildren().forEach(stack::push);
        }
        return false;
    }
}
