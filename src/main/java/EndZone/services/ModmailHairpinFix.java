package EndZone.services;

import EndZone.config.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * Discord log links use the WAN IP. Opening {@code http://WAN:8080} on the same
 * PC is NAT hairpin and times out. The Windows hosts file cannot remap a numeric
 * IP (browsers connect to the address as-is). Assign that WAN IP to the loopback
 * adapter so the public URL stays in Discord but TCP never leaves this machine.
 */
public final class ModmailHairpinFix {
    private static final Logger logger = LoggerFactory.getLogger(ModmailHairpinFix.class);
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final String LOOPBACK = "Loopback Pseudo-Interface 1";

    private ModmailHairpinFix() {
    }

    public static void apply(BotConfig config) {
        if (config == null || !isWindows()) {
            return;
        }
        String publicHost = config.getModmailLogsPublicHost();
        if (!isPublicIpv4(publicHost)) {
            return;
        }
        if (isAssignedLocally(publicHost)) {
            logger.info("[ModmailLogs] {} is local on this PC — public log links will open here", publicHost);
            return;
        }
        int port = config.getModmailLogsPublicPort();
        if (addLoopbackAddress(publicHost)) {
            logger.info("[ModmailLogs] Assigned {} to loopback so public /logs links open on this PC (port {})",
                    publicHost, port);
            return;
        }
        logger.warn("[ModmailLogs] Opening http://{}:{} from THIS PC will fail until loopback is set (NAT hairpin).",
                publicHost, port);
        logger.warn("[ModmailLogs] Run as Administrator: scripts\\fix-log-hairpin.ps1");
        logger.warn("[ModmailLogs] If the bot moved off this PC, run: scripts\\undo-log-hairpin.ps1");
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static boolean isPublicIpv4(String host) {
        if (host == null || !IPV4.matcher(host).matches()) {
            return false;
        }
        String[] p = host.split("\\.");
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        if (a == 10 || a == 127 || a == 0) {
            return false;
        }
        if (a == 192 && b == 168) {
            return false;
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return false;
        }
        return a != 169 || b != 254;
    }

    static boolean isAssignedLocally(String ip) {
        try {
            InetAddress target = InetAddress.getByName(ip);
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.equals(target)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // treat as not local
        }
        return false;
    }

    static boolean addLoopbackAddress(String ip) {
        try {
            Process process = new ProcessBuilder(
                    "netsh", "interface", "ipv4", "add", "address",
                    LOOPBACK, ip, "255.255.255.255", "skipassource=true"
            ).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes());
            int code = process.waitFor();
            if (code == 0 || out.toLowerCase().contains("object already exists")) {
                return isAssignedLocally(ip);
            }
            logger.debug("[ModmailLogs] netsh add address exited {}: {}", code, out.trim());
        } catch (Exception e) {
            logger.debug("[ModmailLogs] netsh add address failed: {}", e.getMessage());
        }
        return false;
    }
}
