package com.studyhive; // [WATCH / CHANGE] Update package name if your NetBeans project uses a different package
import com.formdev.flatlaf.FlatLightLaf; // [WATCH / CHANGE] Requires FlatLaf dependency in classpath; fallback L&F runs if absent
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
public class Main {
public static void main(String[] args) {
try {
FlatLightLaf.setup();
// [WATCH / CHANGE] Global rounded-corner and theme keys (arc values & default font)
UIManager.put("Button.arc", 14);
UIManager.put("Component.arc", 14);
UIManager.put("TextComponent.arc", 14);
UIManager.put("ProgressBar.arc", 14);
UIManager.put("Panel.background", new Color(0xF7, 0xF4, 0xEE));
UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
} catch (Throwable ex) {
try {
UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
} catch (Exception ignored) {
}
}
SwingUtilities.invokeLater(() -> {
// [WATCH / CHANGE] Variable name: `app` (instance of `StudyHiveUI`)
StudyHiveUI app = new StudyHiveUI();
app.setVisible(true);
});
}
}
