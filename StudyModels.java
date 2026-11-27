package com.mycompany.studyhive;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Shared domain models, formatting/network utilities, Base64 protocol helpers,
 * and circular avatar/logo rendering for StudyHive.
 */
public class StudyModels {

    // [MODIFIABLE DEFAULT PORT] Change default port if another local app uses 5000.
    public static final int DEFAULT_PORT = 5000;

    // [MODIFIABLE ANIMAL AVATARS] Animal-only preset list used in the profile avatar picker.
    // REMINDER: If you add/rename an animal here and place image files in src/main/resources/avatars/,
    // ensure the image files are placed inside the project resources folder and match the lowercase name
    // (e.g., "BEAR" -> src/main/resources/avatars/bear.png or .jpg).
    public static final String[] ANIMAL_AVATARS = {"OWL", "FOX", "CAT", "BEAR", "RABBIT", "DEER"};

    /**
     * Types of structured study board items broadcast across the session.
     */
    public enum EntryType {
        NOTE, QUESTION, IMAGE, FILE
    }

    /**
     * Represents a single peer reply attached to a study question.
     */
    public static class ResponseItem {
        public final String author;
        public final String text;
        public final String timestamp;

        /**
         * Creates an immutable question response record.
         */
        public ResponseItem(String author, String text, String timestamp) {
            this.author = author;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    /**
     * Represents a note, question, image, or file shared on the study board.
     */
    public static class BoardEntry {
        public final String id;
        public final EntryType type;
        public final String author;
        public final String content;
        public final String fileName;
        public final String base64Data;
        public final String timestamp;
        public boolean resolved;
        public final List<ResponseItem> responses = new ArrayList<>();

        /**
         * Creates a board entry with optional file/image payload and resolution state.
         */
        public BoardEntry(String id, EntryType type, String author, String content,
            String fileName, String base64Data, String timestamp, boolean resolved) {
            this.id = id;
            this.type = type;
            this.author = author;
            this.content = content;
            this.fileName = fileName;
            this.base64Data = base64Data;
            this.timestamp = timestamp;
            this.resolved = resolved;
        }
    }

    /**
     * Returns the current local wall-clock time formatted as HH:mm.
     */
    public static String nowTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * Formats a non-negative elapsed second count into HH:MM:SS for session timers.
     */
    public static String formatDuration(long totalSeconds) {
        long safe = Math.max(0L, totalSeconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long seconds = safe % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Converts raw byte counts into readable B, KB, or MB labels.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Detects a non-loopback local IPv4 address for hosting LAN sessions, falling back to 127.0.0.1.
     */
    public static String detectLocalIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Builds a circular avatar icon from a custom uploaded image ("CUSTOM:<base64>"),
     * a bundled animal image resource, or a warm vintage animal fallback badge.
     */
    public static ImageIcon createAvatarIcon(String avatarSpec, int size) {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage sourceImg = loadAvatarSourceImage(avatarSpec);

        Ellipse2D.Float circle = new Ellipse2D.Float(1, 1, size - 2, size - 2);
        g2.setClip(circle);

        if (sourceImg != null) {
            int w = sourceImg.getWidth();
            int h = sourceImg.getHeight();
            int crop = Math.min(w, h);
            int x = (w - crop) / 2;
            int y = (h - crop) / 2;
            BufferedImage cropped = sourceImg.getSubimage(x, y, crop, crop);
            Image scaled = cropped.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            g2.drawImage(scaled, 0, 0, null);
        } else {
            // [MODIFIABLE FALLBACK ANIMAL BADGE COLORS] Adjust colors if no image file is found.
            String key = (avatarSpec == null || avatarSpec.isBlank()) ? "CAT" : avatarSpec.toUpperCase();
            Color bg = switch (key) {
                case "OWL" -> new Color(0x5C, 0x40, 0x28);
                case "FOX" -> new Color(0x9C, 0x4F, 0x24);
                case "BEAR" -> new Color(0x4E, 0x34, 0x22);
                case "RABBIT" -> new Color(0x7A, 0x5C, 0x46);
                case "DEER" -> new Color(0x87, 0x5A, 0x36);
                default -> new Color(0x3B, 0x23, 0x14);
            };
            g2.setColor(bg);
            g2.fill(circle);

            g2.setColor(new Color(0xEC, 0xE0, 0xD1));
            int fontSize = Math.max(9, size / 4);
            g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics fm = g2.getFontMetrics();
            String label = key.length() > 5 ? key.substring(0, 5) : key;
            int tx = (size - fm.stringWidth(label)) / 2;
            int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, tx, ty);
        }

        g2.setClip(null);
        g2.setColor(new Color(0x3B, 0x23, 0x14));
        g2.setStroke(new BasicStroke(Math.max(1.5f, size / 32f)));
        g2.draw(circle);
        g2.dispose();
        return new ImageIcon(canvas);
    }

    /**
     * Loads a custom Base64 avatar image or looks up a matching animal image in /avatars/.
     */
    private static BufferedImage loadAvatarSourceImage(String avatarSpec) {
        if (avatarSpec == null || avatarSpec.isBlank()) return null;
        try {
            if (avatarSpec.startsWith("CUSTOM:")) {
                byte[] bytes = Base64.getDecoder().decode(avatarSpec.substring("CUSTOM:".length()));
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
            // [MODIFIABLE AVATAR RESOURCE PATH] Change "/avatars/" if you store animal images elsewhere.
            // REMINDER: Ensure image files (e.g., owl.png, fox.png, cat.png, bear.png, rabbit.png, deer.png)
            // are placed inside the project resources folder (such as src/main/resources/avatars/) so Maven/NetBeans
            // copies them into the compiled classpath.
            String lower = avatarSpec.toLowerCase();
            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                InputStream stream = StudyModels.class.getResourceAsStream("/avatars/" + lower + ext);
                if (stream != null) {
                    return ImageIO.read(stream);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Builds the StudyHive brand logo icon from a custom logo image resource,
     * falling back to a clean vintage StudyHive emblem if no image file is bundled.
     */
    public static ImageIcon createStudyHiveLogoIcon(int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        try {
            // [MODIFIABLE LOGO RESOURCE PATH] Replace with your own StudyHive logo file in resources.
            // REMINDER: Ensure your logo image file (e.g., src/main/resources/logo/studyhive_logo.png)
            // is included inside the project folder and packaged in the build output.
            for (String path : new String[]{"/logo/studyhive_logo.png", "/logo/studyhive_logo.jpg", "/studyhive_logo.png"}) {
                InputStream stream = StudyModels.class.getResourceAsStream(path);
                if (stream != null) {
                    BufferedImage logoImg = ImageIO.read(stream);
                    if (logoImg != null) {
                        double scale = Math.min((double) width / logoImg.getWidth(), (double) height /logoImg.getHeight());
                        int drawW = Math.max(1, (int) (logoImg.getWidth() * scale));
                        int drawH = Math.max(1, (int) (logoImg.getHeight() * scale));
                        int dx = (width - drawW) / 2;
                        int dy = (height - drawH) / 2;
                        g2.drawImage(logoImg.getScaledInstance(drawW, drawH, Image.SCALE_SMOOTH), dx, dy, null);
                        g2.dispose();
                        return new ImageIcon(canvas);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback vintage emblem (rounded coffee badge + open book motif)
        int pad = 2;
        g2.setColor(new Color(0x8C, 0x62, 0x39));
        g2.fillRoundRect(pad, pad, width - pad * 2, height - pad * 2, 10, 10);
        g2.setColor(new Color(0x3B, 0x23, 0x14));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(pad, pad, width - pad * 2, height - pad * 2, 10, 10);

        g2.setColor(new Color(0xF5, 0xEE, 0xE4));
        int bookW = Math.max(12, width - 14);
        int bookH = Math.max(10, height - 16);
        int bx = (width - bookW) / 2;
        int by = (height - bookH) / 2;
        g2.fillRoundRect(bx, by, bookW, bookH, 4, 4);
        g2.setColor(new Color(0x3B, 0x23, 0x14));
        g2.drawLine(width / 2, by, width / 2, by + bookH);

        g2.dispose();
        return new ImageIcon(canvas);
    }

    /**
     * Returns a short readable label for an avatar specification.
     */
    public static String avatarDisplayLabel(String avatarSpec) {
        if (avatarSpec == null || avatarSpec.isBlank()) return "CAT";
        if (avatarSpec.startsWith("CUSTOM:")) return "PHOTO";
        return avatarSpec.toUpperCase();
    }

    /**
     * Encodes text fields in UTF-8 Base64 so single-line pipe-delimited frames never break.
     */
    public static String encodeField(String raw) {
        if (raw == null) return "";
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a UTF-8 Base64 protocol field back into plain text.
     */
    public static String decodeField(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
