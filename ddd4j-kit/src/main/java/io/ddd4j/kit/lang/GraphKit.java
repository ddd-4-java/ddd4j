package io.ddd4j.kit.lang;

import com.google.common.graph.Graph;
import lombok.experimental.UtilityClass;

import java.util.*;

/**
 * 图拓扑排序工具类（通用算法）。
 *
 * <p>提供基于 Guava Graph 的拓扑排序能力，可用于任务依赖调度、构建顺序等场景。
 *
 * @author Loong Wan
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
