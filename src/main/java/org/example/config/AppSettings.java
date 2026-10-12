package org.example.config;

import org.springframework.stereotype.Component;

import java.util.prefs.Preferences;

/**
 * User preferences backed by the Java Preferences API. Uses the same node as the old
 * {@code SettingsController} so existing settings carry over.
 */
@Component
public class AppSettings {
    public enum Theme { LIGHT, DARK }

    public static final int DEFAULT_REMINDER_DAYS = 30;
    private static final String KEY_REMINDER_DAYS = "reminder.days";
    private static final String KEY_THEME = "theme";

    private final Preferences prefs = Preferences.userRoot().node("org/example/ui");

    public int getReminderDays() {
        int days = prefs.getInt(KEY_REMINDER_DAYS, DEFAULT_REMINDER_DAYS);
        return days > 0 ? days : DEFAULT_REMINDER_DAYS;
    }

    public void setReminderDays(int days) {
        prefs.putInt(KEY_REMINDER_DAYS, Math.max(1, days));
    }

    public Theme getTheme() {
        return "Dark".equalsIgnoreCase(prefs.get(KEY_THEME, "Light")) ? Theme.DARK : Theme.LIGHT;
    }

    public void setTheme(Theme theme) {
        prefs.put(KEY_THEME, theme == Theme.DARK ? "Dark" : "Light");
    }
}
