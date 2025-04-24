package com.xm.dp;

/**
 * @author XM
 * @date 2025/4/24
 */
public class Knapsack {

    public static void main(String[] args) {

        int amount = 3;
        int coins[] = new int[]{1, 2};
        int[] dpSum = getDp(amount, coins);

        System.out.println(dpSum[amount]);


        int[] dp2 = getDp2(amount, coins);

        System.out.println(dp2[amount]);
    }

    /**
     * 求排列
     *
     * @param amount
     * @param coins
     * @return
     */
    private static int[] getDp2(int amount, int[] coins) {
        // dp[j] 表示凑出金额 j 的排列数
        int[] dp2 = new int[amount + 1];
        // 金额为 0 时有 1 种“空序列”
        dp2[0] = 1;

        for (int j = 1; j <= amount; j++) {
            for (int coin : coins) {
                if (j >= coin) {
                    dp2[j] += dp2[j - coin];
                }
            }
        }
        return dp2;
    }

    /**
     * 求排列
     *
     * @param amount
     * @param coins
     * @return
     */
    private static int[] getDp(int amount, int[] coins) {
        // dp[i][j] = 以 coins[i] 结尾、凑出 j 的排列数
        int[][] dp = new int[2][amount + 1];
        // dpSum[j] = 所有以任意硬币结尾的、凑出 j 的排列总数
        int[] dpSum = new int[amount + 1];

        // base case：凑出 0，只有一种空序列
        dpSum[0] = 1;

        for (int j = 1; j <= amount; j++) {
            // 先清零，下面累加
            dpSum[j] = 0;
            for (int i = 0; i < 2; i++) {
                if (j >= coins[i]) {
                    // 以 coins[i] 结尾的所有排列数
                    dp[i][j] = dpSum[j - coins[i]];
                } else {
                    dp[i][j] = 0;
                }
                dpSum[j] += dp[i][j];
            }
        }
        return dpSum;
    }

}
