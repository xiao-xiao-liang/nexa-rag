export interface DiffLine {
  type: "added" | "removed" | "unchanged";
  oldLineNumber?: number;
  newLineNumber?: number;
  content: string;
}

export interface DiffResult {
  lines: DiffLine[];
  additions: number;
  deletions: number;
  hasChanges: boolean;
}

/**
 * 使用最长公共子序列 (LCS) 算法对两段文本进行逐行差异比对
 */
export function computeLineDiff(oldText: string, newText: string): DiffResult {
  const oldLines = oldText ? oldText.split("\n") : [];
  const newLines = newText ? newText.split("\n") : [];

  const n = oldLines.length;
  const m = newLines.length;

  // 边界快速优化：完全相同
  if (oldText === newText) {
    return {
      lines: oldLines.map((content, idx) => ({
        type: "unchanged",
        oldLineNumber: idx + 1,
        newLineNumber: idx + 1,
        content,
      })),
      additions: 0,
      deletions: 0,
      hasChanges: false,
    };
  }

  // 边界快速优化：原文本为空
  if (n === 0) {
    return {
      lines: newLines.map((content, idx) => ({
        type: "added",
        newLineNumber: idx + 1,
        content,
      })),
      additions: m,
      deletions: 0,
      hasChanges: m > 0,
    };
  }

  // 边界快速优化：新文本为空
  if (m === 0) {
    return {
      lines: oldLines.map((content, idx) => ({
        type: "removed",
        oldLineNumber: idx + 1,
        content,
      })),
      additions: 0,
      deletions: n,
      hasChanges: n > 0,
    };
  }

  // 构建 LCS 动态规划矩阵 (限制规模防止超大文本占用过多内存)
  const maxLines = 2000;
  const boundedN = Math.min(n, maxLines);
  const boundedM = Math.min(m, maxLines);

  const dp: number[][] = Array.from({ length: boundedN + 1 }, () =>
    new Array(boundedM + 1).fill(0)
  );

  for (let i = 1; i <= boundedN; i++) {
    for (let j = 1; j <= boundedM; j++) {
      if (oldLines[i - 1] === newLines[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  // 回溯还原 Diff 序列
  const result: DiffLine[] = [];
  let i = boundedN;
  let j = boundedM;
  let additions = 0;
  let deletions = 0;

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      result.unshift({
        type: "unchanged",
        oldLineNumber: i,
        newLineNumber: j,
        content: oldLines[i - 1],
      });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      result.unshift({
        type: "added",
        newLineNumber: j,
        content: newLines[j - 1],
      });
      additions++;
      j--;
    } else if (i > 0 && (j === 0 || dp[i][j - 1] < dp[i - 1][j])) {
      result.unshift({
        type: "removed",
        oldLineNumber: i,
        content: oldLines[i - 1],
      });
      deletions++;
      i--;
    }
  }

  return {
    lines: result,
    additions,
    deletions,
    hasChanges: additions > 0 || deletions > 0,
  };
}
