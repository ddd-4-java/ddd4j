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
     * \u5148\u904d\u5386\u5f53\u524d\u8282\u70b9\u7684\u672a\u8bbf\u95ee\u540e\u7ee7\uff0c\u518d\u628a\u5f53\u524d\u8282\u70b9\u52a0\u5165\u7ed3\u679c\u3002
     * @param <T> \u56fe\u8282\u70b9\u7c7b\u578b
     * @param graph \u8981\u904d\u5386\u7684\u6709\u5411\u56fe
     * @param node \u5f53\u524d\u8bbf\u95ee\u7684\u8282\u70b9
     * @param visitedNodes \u5df2\u7ecf\u8bbf\u95ee\u8fc7\u7684\u8282\u70b9\u96c6\u5408
     * @param sortedNodes \u6309\u6df1\u5ea6\u4f18\u5148\u540e\u5e8f\u7d2f\u79ef\u7ed3\u679c\u7684\u5217\u8868
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
