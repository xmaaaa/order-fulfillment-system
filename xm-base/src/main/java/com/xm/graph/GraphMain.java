package com.xm.graph;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * @author XM
 * @date 2025/8/8
 */
public class GraphMain {
    static List<List<Integer>> result = new ArrayList<>();
    static List<Integer> path = new ArrayList<>();

    /**
     * 输入: 有向无环图
     * 第一行包含两个整数 N，M，表示图中拥有 N 个节点，M 条边
     * 后续 M 行，每行包含两个整数 s 和 t，表示图中的 s 节点与 t 节点中有一条路径
     * <p>
     * 由此构建图，两种方式，邻接矩阵，邻接表
     * 输出：所有 *节点1到节点n* 的路径
     *
     * @param args
     */
    public static void main(String[] args) {
        // testcase
        //5 5
        //1 3
        //3 5
        //1 2
        //2 4
        //4 5
        graphList();
    }

    /**
     * 邻接矩阵
     */
    private static void graphArray() {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();

        // 邻接矩阵
        int[][] graph = new int[n + 1][n + 1];
        for (int i = 0; i < m; i++) {
            int s = sc.nextInt();
            int t = sc.nextInt();
            // 有向图，1 表示 s 与 t 是相连的
            graph[s][t] = 1;
        }

        path.add(1);
        dfs(graph, 1, n);
        printResult();
    }

    private static void printResult() {
        if (result.isEmpty()) {
            System.out.print(-1);
        }
        for (List<Integer> cur : result) {
            for (int i = 0; i < cur.size() - 1; i++) {
                System.out.print(cur.get(i) + " ");
            }
            System.out.println(cur.get(cur.size() - 1));
        }
    }

    private static void dfs(int[][] graphArray, int index, int n) {
        if (index == n) {
            result.add(new ArrayList<>(path));
            return;
        }
        // 注意当前节点就是i, 不要搞错了
        for (int i = 0; i < graphArray[index].length; i++) {
            if (graphArray[index][i] == 1) {
                path.add(i);
                dfs(graphArray, i, n);
                path.remove(path.size() - 1);
            }
        }
    }

    /**
     * 邻接表
     */
    private static void graphList() {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int m = sc.nextInt();

        // 邻接表
        List<LinkedList<Integer>> graph2 = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            graph2.add(new LinkedList<>());
        }
        // 注意m-- 不是--m
        while (m-- > 0) {
            int s = sc.nextInt();
            int t = sc.nextInt();
            // 有向图只用添加单边
            graph2.get(s).add(t);
        }

        path.add(1);
        dfs2(graph2, 1, n);
        printResult();
    }

    private static void dfs2(List<LinkedList<Integer>> graph2, int index, int n) {
        if (index == n) {
            result.add(new ArrayList<>(path));
            return;
        }

        List<Integer> curList = graph2.get(index);
        if (curList.isEmpty()) {
            return;
        }
        for (Integer integer : curList) {
            path.add(integer);
            dfs2(graph2, integer, n);
            path.remove(path.size() - 1);
        }
    }
}
