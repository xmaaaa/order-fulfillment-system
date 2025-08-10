package com.xm;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * 树相关
 * 前序遍历：中左右
 * 中序遍历：左中右
 * 后序遍历：左右中
 *
 * @author hongwan
 * @date 2022/10/20
 */
public class TreeNodeTest {


    public static class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        TreeNode() {
        }

        TreeNode(int val) {
            this.val = val;
        }

        TreeNode(int val, TreeNode left, TreeNode right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }
    }

    List<Integer> pre = Lists.newArrayList();
    List<Integer> in = Lists.newArrayList();
    List<Integer> next = Lists.newArrayList();

    /**
     * 递归实现
     *
     * @param root
     * @param result
     */
    public void preorder(TreeNode root, List<Integer> result) {
        if (root == null) {
            return;
        }
        // 如果放在这就是中左右
        result.add(root.val);
        preorder(root.left, result);
        // 放在这里就是左中右
        preorder(root.right, result);
        // 放在这里就是左右中
    }

    /**
     * 迭代法：树的前序遍历, 根左右
     *
     * @param root
     */
    public void pre(TreeNode root) {
        Deque<TreeNode> stack = new LinkedList<>();
        stack.push(root);
        while (!stack.isEmpty() || root != null) {
            if (root != null) {
                pre.add(root.val);
                stack.push(root);
                root = root.left;
            } else {
                root = stack.pop();
                root = root.right;
            }
        }

        // 方法2: 入栈右左, 出栈就是左右
        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            pre.add(node.val);
            if (node.right != null) {
                stack.push(node.right);
            }
            if (node.left != null) {
                stack.push(node.left);
            }
        }
        return;
    }

    /**
     * 迭代法：树的中序遍历, 左根右
     *
     * @param root
     */
    public void in(TreeNode root) {
        Deque<TreeNode> stack = new LinkedList<>();
        while (!stack.isEmpty() || root != null) {
            if (root != null) {
                stack.push(root);
                root = root.left;
            } else {
                root = stack.pop();
                in.add(root.val);
                root = root.right;
            }
        }
        return;
    }

    /**
     * 迭代法：树的后序遍历, 左右根
     *
     * @param root
     */
    public void next(TreeNode root) {
        Deque<TreeNode> stack = new LinkedList<>();
        stack.push(root);
        TreeNode pre = null;
        while (!stack.isEmpty() || root != null) {
            if (root != null) {
                stack.push(root);
                root = root.left;
            } else {
                // 此时需要判断是否要去右节点，如果没去过才去，所以不能pop掉
                root = stack.peek();
                if (root.right == null || root.right == pre) {
                    next.add(root.val);
                    stack.pop();
                    pre = root;
                    root = null;
                } else {
                    root = root.right;
                }
            }
        }

        // 方法2: 入栈左右, 出栈就是中右左，最后翻转就是左右中
        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            next.add(node.val);
            if (node.right != null) {
                stack.push(node.right);
            }
            if (node.left != null) {
                stack.push(node.left);
            }
        }
        Collections.reverse(next);

        return;
    }

    /**
     * 统一迭代法: 用null来标记是否为root，只有走过的才会塞入，什么意思呢？
     * 就是假设左中右，先不将左塞入，只有当左子节点也经历过遍历，才会有null标记才会塞入值，只有中可以直接塞入
     * 通过更换顺序即可统一代码
     *
     * @param root
     * @return
     */
    public List<Integer> preorderTraversal(TreeNode root) {
        List<Integer> result = new LinkedList<>();
        Stack<TreeNode> st = new Stack<>();
        if (root != null) {
            st.push(root);
        }
        while (!st.empty()) {
            TreeNode node = st.peek();
            if (node != null) {
                // 将该节点弹出，避免重复操作，下面再将右左中节点添加到栈中（前序遍历-中左右，入栈顺序右左中）
                st.pop();
                // 添加右节点（空节点不入栈）
                if (node.right != null) {
                    st.push(node.right);
                }
                // 添加左节点（空节点不入栈）
                if (node.left != null) {
                    st.push(node.left);
                }
                // 添加中节点
                st.push(node);
                // 中节点访问过，但是还没有处理，加入空节点做为标记。
                st.push(null);

            } else {
                // 将空节点弹出
                st.pop();
                // 重新取出栈中元素
                node = st.pop();
                // 加入到结果集
                result.add(node.val);
            }
        }
        return result;
    }

    /**
     * 深度优先遍历, 用栈
     *
     * @param root
     */
    public void depthFirstTraversal(TreeNode root) {
        Deque<TreeNode> stack = new LinkedList<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            // 从栈头取出
            TreeNode node = stack.pop();
            if (node != null) {
                // 逻辑
                // ......
                // 先右后左
                stack.push(node.left);
                stack.push(node.right);
            }
        }
    }

    /**
     * 广度优先遍历, 用队列
     *
     * @param root
     */
    public void breadthFirstTraversal(TreeNode root) {
        Deque<TreeNode> stack = new LinkedList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            // 从队头取出
            TreeNode node = stack.poll();
            if (node != null) {
                // 逻辑
                // ...
                // 先左后右
                stack.add(node.left);
                stack.add(node.right);
            }
        }
    }

    public static void main(String[] args) {
        TreeNode treeNode = new TreeNode(1, new TreeNode(2, new TreeNode(4), new TreeNode(5)), new TreeNode(3));

    }
}
