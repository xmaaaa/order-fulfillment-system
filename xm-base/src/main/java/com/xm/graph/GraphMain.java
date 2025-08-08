package com.xm.graph;

import java.util.Scanner;

/**
 * @author XM
 * @date 2025/8/8
 */
public class GraphMain {


    /**
     * 输入:
     * 第一行包含两个整数 N，M，表示图中拥有 N 个节点，M 条边
     * 后续 M 行，每行包含两个整数 s 和 t，表示图中的 s 节点与 t 节点中有一条路径
     *
     * 由此构建图，两种方式，邻接矩阵，邻接表
     *
     * @param args
     */
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();

        // 节点编号从1到n，所以申请 n+1 这么大的数组
        int[][] graph = new int[n + 1][n + 1];

        for (int i = 0; i < m; i++) {
            int s = sc.nextInt();
            int t = sc.nextInt();
            // 使用邻接矩阵表示无向图，1 表示 s 与 t 是相连的
            graph[s][t] = 1;
        }
    }
}
