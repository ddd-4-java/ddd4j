/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.kit.lang;

import com.google.common.graph.Graph;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 图拓扑排序工具类（通用算法）。
 *
 * <p>提供基于 Guava Graph 的拓扑排序能力，可用于任务依赖调度、构建顺序等场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class GraphKit {

    /**
     * 对有向图进行拓扑排序
     *
     * @param graph 有向图
     * @param <T>   节点类型
     * @return 拓扑排序后的节点列表
     */
    public static <T> List<T> topologicalSort(Graph<T> graph) {
        List<T> sortedNodes = new ArrayList<>();
        Set<T> visitedNodes = new HashSet<>();

        for (T node : graph.nodes()) {
            if (!visitedNodes.contains(node)) {
                depthFirstSearch(graph, node, visitedNodes, sortedNodes);
            }
        }

        return sortedNodes;
    }

    /**
     * 先遍历当前节点的未访问后继，再把当前节点加入结果。
     *
     * @param <T>          图节点类型
     * @param graph        要遍历的有向图
     * @param node         当前访问的节点
     * @param visitedNodes 已经访问过的节点集合
     * @param sortedNodes  按深度优先后序累积结果的列表
     */
    private static <T> void depthFirstSearch(Graph<T> graph, T node, Set<T> visitedNodes, List<T> sortedNodes) {
        visitedNodes.add(node);

        for (T successor : graph.successors(node)) {
            if (!visitedNodes.contains(successor)) {
                depthFirstSearch(graph, successor, visitedNodes, sortedNodes);
            }
        }

        sortedNodes.add(node);
    }

}
