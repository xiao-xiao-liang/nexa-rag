package com.nexarag.model.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 权重模型路由选择器，负责按静态权重生成候选模型尝试顺序。
 */
public class WeightedModelRouteSelector {

    private final Random random;

    /**
     * 创建权重模型路由选择器。
     */
    public WeightedModelRouteSelector() {
        this(new Random());
    }

    /**
     * 创建可指定随机源的权重模型路由选择器，便于测试固定随机行为。
     *
     * @param random 随机源
     */
    public WeightedModelRouteSelector(Random random) {
        this.random = random;
    }

    /**
     * 按权重生成候选顺序。
     *
     * @param candidates 原始候选列表
     * @return 排序后的候选列表
     */
    public List<ModelRouteDecision> orderCandidates(List<ModelRouteDecision> candidates) {
        // 1. 仅保留正权重候选
        List<ModelRouteDecision> weightedCandidates = candidates.stream()
                .filter(candidate -> safeWeight(candidate) > 0)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        // 2. 没有正权重时退化为优先级排序
        if (weightedCandidates.isEmpty()) {
            return candidates.stream()
                    .sorted(Comparator.comparing(this::safePriority))
                    .toList();
        }

        // 3. 按静态权重逐个抽取，生成 fallback 尝试顺序
        List<ModelRouteDecision> ordered = new ArrayList<>();
        while (!weightedCandidates.isEmpty()) {
            ModelRouteDecision selected = selectOne(weightedCandidates);
            ordered.add(selected);
            weightedCandidates.remove(selected);
        }
        return ordered;
    }

    private ModelRouteDecision selectOne(List<ModelRouteDecision> candidates) {
        int totalWeight = candidates.stream()
                .mapToInt(this::safeWeight)
                .sum();
        int hit = random.nextInt(totalWeight);
        int cursor = 0;
        for (ModelRouteDecision candidate : candidates) {
            cursor += safeWeight(candidate);
            if (hit < cursor) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private int safeWeight(ModelRouteDecision decision) {
        return decision.weight() == null ? 0 : decision.weight();
    }

    private int safePriority(ModelRouteDecision decision) {
        return decision.priority() == null ? Integer.MAX_VALUE : decision.priority();
    }
}
