package com.xm;

import com.google.common.collect.Lists;

import java.util.ArrayList;
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
        // 一定要注意有可能stack为空，root不为空
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
            if (node.left != null) {
                stack.push(node.left);
            }
            if (node.right != null) {
                stack.push(node.right);
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
            // 将该节点弹出，避免重复操作
            st.pop();
            if (node != null) {
                // 下面再将右左中节点添加到栈中（前序遍历-中左右，入栈顺序右左中）
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

    /**
     * 层序遍历，类似上面bfs，只不过要分组
     * 可以解决很多同层相关问题
     *
     * @param root
     */
    public List<List<Integer>> levelTraversal(TreeNode root) {
        List<List<Integer>> result = Lists.newArrayList();
        Deque<TreeNode> stack = new LinkedList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            List<Integer> nodes = Lists.newArrayList();
            int size = stack.size();
            while (size > 0) {
                TreeNode node = stack.poll();
                // 此处可以解决同层问题
                nodes.add(node.val);
                if (node.left != null) {
                    stack.add(node.left);
                }
                if (node.right != null) {
                    stack.add(node.right);
                }
                size--;
            }
            result.add(nodes);
        }
        return result;
    }


    /**
     * 层序遍历递归版，记录深度
     *
     * @param root
     */
    public List<List<Integer>> levelTraversal2(TreeNode root, int deep) {
        List<List<Integer>> result = Lists.newArrayList();
        traversal(result, root, deep);
        return result;
    }

    private void traversal(List<List<Integer>> resList,TreeNode root, int deep) {
        if (root == null) {
            return;
        }
        deep++;
        if (resList.size() < deep) {
            //当层级增加时，list的Item也增加，利用list的索引值进行层级界定
            List<Integer> item = new ArrayList<Integer>();
            resList.add(item);
        }
        resList.get(deep - 1).add(root.val);
        traversal(resList, root.left, deep);
        traversal(resList, root.right, deep);
    }


    public static void main(String[] args) {
        TreeNode treeNode = new TreeNode(1, new TreeNode(2, new TreeNode(4), new TreeNode(5)), new TreeNode(3));

    }
}
