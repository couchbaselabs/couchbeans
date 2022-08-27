package cbb;

import cbb.annotations.Scope;
import org.apache.commons.lang3.StringUtils;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Scope(BeanScope.NODE)
public class Node {
    private final String name;
    private final String clusterUsername = Couchbeans.CBB_USERNAME;
    private String clusterPassword;

    private final boolean dcpClient = DCPListener.RUNNING.get();
    private final String[] tags = Utils.env("CBB_NODE_TAGS")
            .stream()
            .flatMap(v -> Arrays.stream(v.split(",")))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .toArray(String[]::new);
    private final String[] ignoredPackages = Utils.env("CBB_NODE_IGNORE_PACKAGES")
            .stream()
            .flatMap(v -> Arrays.stream(v.split(",")))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .toArray(String[]::new);
    private String[] ips;
    private boolean running;
    private long lastBeat;

    private final String timezone = ZoneId.systemDefault().getId();

    public Node() {
        try {
            List<String> ips = new ArrayList<>();
            NetworkInterface.getNetworkInterfaces().asIterator().forEachRemaining(ni -> {
                ni.getInetAddresses().asIterator().forEachRemaining(ip -> {
                    if (!(ip.isLoopbackAddress() || ip.isLinkLocalAddress())) {
                        ips.add(ip.toString());
                    }
                });
            });
            this.ips = ips.toArray(String[]::new);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        this.name = Utils.envOrDefault("CBB_NODE_NAME", Utils.env("HOSTNAME").orElseGet(() -> {
            if (this.ips.length == 1) {
                return this.ips[0];
            }

            if (!dcpClient) {
                return String.format("%s_%d", System.getProperty("program.name"), ProcessHandle.current().pid());
            }
            throw new RuntimeException("Failed to set node name from `CBB_NODE_NAME`, `HOSTNAME` env vars or a single non-local network interface");
        }));
    }

    public String[] tags() {
        return tags;
    }

    public String name() {
        return name;
    }

    public String[] ips() {
        return ips;
    }

    public String naturalKey() {
        return name;
    }

    protected void setRunning(boolean isRunning) {
        this.running = isRunning;
        Couchbeans.store(this);
    }

    public boolean isRunning() {
        return this.running;
    }

    public void sendHeartbeat() {
        this.lastBeat = System.currentTimeMillis();
        Couchbeans.store(this);
    }

    public String[] getTags() {
       return tags;
    }

    public String[] getIgnoredPackages() {
        return ignoredPackages;
    }
}
