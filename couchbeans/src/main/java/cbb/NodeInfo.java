package cbb;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class NodeInfo {
    public final static String[] tags = Arrays.stream(Utils.envOrDefault("CBB_NODE_TAGS", "").split(","))
            .filter(StringUtils::isNotEmpty)
            .map(String::trim)
            .map(String::toLowerCase)
            .toArray(String[]::new);

    public final static NodeType TYPE = (!DCPListener.RUNNING.get()) ? NodeType.FOREIGN :
            (Utils.hasLocalDataService()) ? NodeType.INTERNAL : NodeType.EXTERNAL;

}
