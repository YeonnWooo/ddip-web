package com.ddip.backend.recommendation.config;

/**
 * Chang's Extent Analysis (1996) 기반 Fuzzy AHP 가중치 계산기
 *
 * ────────────────────────────────────────────────
 * 알고리즘 개요
 * 1. TFN 쌍대비교 행렬 입력 (각 원소 = {l, m, u})
 * 2. Fuzzy Synthetic Extent 계산:
 *      Si = (ΣRow_l / ΣAll_u, ΣRow_m / ΣAll_m, ΣRow_u / ΣAll_l)
 * 3. Degree of Possibility V(Si >= Sj) 계산:
 *      - mi >= mj → 1
 *      - uj <= li → 0
 *      - 그 외   → (lj - ui) / ((mi - ui) - (mj - lj))
 * 4. 최소값 벡터(d') → 정규화 → 가중치
 * ────────────────────────────────────────────────
 *
 * 입력 형식: double[n][n][3]
 *   [i][j][0] = l (lower),  [i][j][1] = m (middle),  [i][j][2] = u (upper)
 */
public class FuzzyAhpComputer {

    private FuzzyAhpComputer() {}

    /**
     * @param tfnMatrix 삼각 퍼지수 쌍대비교 행렬 [n][n][3]
     * @return 정규화된 가중치 벡터 (길이 n)
     */
    public static double[] compute(double[][][] tfnMatrix) {
        int n = tfnMatrix.length;

        // ── Step 1: 행별 TFN 합계 ──────────────────────────
        double[] rowL = new double[n];
        double[] rowM = new double[n];
        double[] rowU = new double[n];
        double totalL = 0, totalM = 0, totalU = 0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                rowL[i] += tfnMatrix[i][j][0];
                rowM[i] += tfnMatrix[i][j][1];
                rowU[i] += tfnMatrix[i][j][2];
            }
            totalL += rowL[i];
            totalM += rowM[i];
            totalU += rowU[i];
        }

        // ── Step 2: Fuzzy Synthetic Extent ─────────────────
        // Si = (rowL[i]/totalU, rowM[i]/totalM, rowU[i]/totalL)
        double[] sl = new double[n];
        double[] sm = new double[n];
        double[] su = new double[n];
        for (int i = 0; i < n; i++) {
            sl[i] = rowL[i] / totalU;
            sm[i] = rowM[i] / totalM;
            su[i] = rowU[i] / totalL;
        }

        // ── Step 3: Degree of Possibility ─────────────────
        // d'(Ai) = min_{j≠i} V(Si >= Sj)
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            double minV = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double v = possibility(sl[i], sm[i], su[i], sl[j], sm[j], su[j]);
                if (v < minV) minV = v;
            }
            d[i] = (minV == Double.MAX_VALUE) ? 1.0 : Math.max(0.0, minV);
        }

        // ── Step 4: 정규화 ────────────────────────────────
        double sum = 0;
        for (double v : d) sum += v;
        if (sum == 0) {
            double[] uniform = new double[n];
            java.util.Arrays.fill(uniform, 1.0 / n);
            return uniform;
        }
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            weights[i] = Math.round((d[i] / sum) * 10000.0) / 10000.0;
        }
        return weights;
    }

    /**
     * V(Si >= Sj): Si가 Sj보다 크거나 같을 가능성
     */
    private static double possibility(
            double li, double mi, double ui,
            double lj, double mj, double uj) {
        if (mi >= mj) return 1.0;
        if (uj <= li) return 0.0;
        // intersection point heuristic (Chang 1996)
        double denom = (mi - ui) - (mj - lj);
        if (Math.abs(denom) < 1e-10) return 0.0;
        return (lj - ui) / denom;
    }

    // ── TFN 상수 (Saaty-Buckley 변환 테이블) ────────────────────────

    public static double[] tfn(int a) {
        return switch (a) {
            case 1 -> new double[]{1.0, 1.0, 1.0};
            case 2 -> new double[]{1.0, 2.0, 3.0};
            case 3 -> new double[]{2.0, 3.0, 4.0};
            case 4 -> new double[]{3.0, 4.0, 5.0};
            case 5 -> new double[]{4.0, 5.0, 6.0};
            case 6 -> new double[]{5.0, 6.0, 7.0};
            case 7 -> new double[]{6.0, 7.0, 8.0};
            case 8 -> new double[]{7.0, 8.0, 9.0};
            case 9 -> new double[]{8.0, 9.0, 10.0};
            default -> new double[]{1.0, 1.0, 1.0};
        };
    }

    /** 역수 TFN: a^-1 → (1/u, 1/m, 1/l) */
    public static double[] tfnReciprocal(int a) {
        double[] t = tfn(a);
        return new double[]{1.0 / t[2], 1.0 / t[1], 1.0 / t[0]};
    }
}
