package com.xm.graph;

/**
 * 并查集实现 - 路径压缩 + 按秩合并（rank）
 * 可以想象一个叶子节点都指向根节点的树
 * 用途: 判断两个元素是否属于同一个集合
 * *有向图* 强连通 → 不能用并查集，要用 Tarjan 或 Kosaraju 算法找强连通分量
 *
 * @author XM
 * @date 2025/8/15
 */
public class UnionFind {
    private final int[] parent;
    private final int[] rank;

    /**
     * 此处的n要注意，是节点最大值Max + 1
     * 如果节点稀疏可以用map代替int[] 实现hash
     *
     * @param n
     */
    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            rank[i] = 1;
        }
    }

    /**
     * 过程中路径压缩，让parent直接指向根节点
     * 虽然 rank 可能不再真实，但路径压缩已经让高度降到 O(α(n))（几乎常数），所以即使秩失真，也不会拖慢性能。
     *
     * @param p
     * @return
     */
    public int find(int p) {
        return parent[p] == p ? p : (parent[p] = find(parent[p]));
    }

    /**
     * 合并两个集合
     *
     * @param p
     * @param q
     */
    public void union(int p, int q) {
        // 不可以直接用connected判断，因为要连接的是pq的父节点，而不是pq节点
        int pRoot = find(p);
        int qRoot = find(q);
        if (pRoot == qRoot) {
            return;
        }
        // 假设低的合入高的，那么高的高度不会发生变化，如果高度相同那么才会 + 1
        if (rank[pRoot] < rank[qRoot]) {
            parent[pRoot] = qRoot;
        } else if (rank[pRoot] > rank[qRoot]) {
            parent[qRoot] = pRoot;
        } else {
            parent[qRoot] = pRoot;
            rank[pRoot]++;
        }
    }

    /**
     * 判断两个集合是否连接, 直接判断根节点是否相同即可
     *
     * @param p
     * @param q
     * @return
     */
    public boolean isConnected(int p, int q) {
        return find(p) == find(q);
    }

}
