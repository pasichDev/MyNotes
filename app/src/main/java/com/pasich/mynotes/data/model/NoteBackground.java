package com.pasich.mynotes.data.model;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * Модель для зберігання даних про фон нотатки
 */
public class NoteBackground {
    
    public enum BackgroundType {
        DEFAULT,    // Стандартний фон додатку
        COLOR,      // Однотонний колір
        GRADIENT    // Градієнт
    }
    
    @SerializedName("type")
    private BackgroundType type;
    
    @SerializedName("primaryColor")
    private String primaryColor;    // Основний колір (для COLOR) або перший колір градієнта
    
    @SerializedName("secondaryColor") 
    private String secondaryColor;  // Другий колір для градієнта
    
    @SerializedName("gradientDirection")
    private int gradientDirection;  // Напрямок градієнта (0-360 градусів)
    
    public NoteBackground() {
        this.type = BackgroundType.DEFAULT;
        this.primaryColor = null;
        this.secondaryColor = null;
        this.gradientDirection = 0;
    }
    
    public NoteBackground(BackgroundType type, String primaryColor) {
        this.type = type;
        this.primaryColor = primaryColor;
        this.secondaryColor = null;
        this.gradientDirection = 0;
    }
    
    public NoteBackground(String primaryColor, String secondaryColor, int gradientDirection) {
        this.type = BackgroundType.GRADIENT;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.gradientDirection = gradientDirection;
    }
    
    // Геттери та сеттери
    public BackgroundType getType() {
        return type;
    }
    
    public void setType(BackgroundType type) {
        this.type = type;
    }
    
    public String getPrimaryColor() {
        return primaryColor;
    }
    
    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }
    
    public String getSecondaryColor() {
        return secondaryColor;
    }
    
    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }
    
    public int getGradientDirection() {
        return gradientDirection;
    }
    
    public void setGradientDirection(int gradientDirection) {
        this.gradientDirection = gradientDirection;
    }
    
    /**
     * Створює стандартний фон
     */
    public static NoteBackground createDefault() {
        return new NoteBackground();
    }
    
    /**
     * Створює кольоровий фон
     */
    public static NoteBackground createColor(String color) {
        return new NoteBackground(BackgroundType.COLOR, color);
    }
    
    /**
     * Створює градієнтний фон
     */
    public static NoteBackground createGradient(String primaryColor, String secondaryColor, int direction) {
        return new NoteBackground(primaryColor, secondaryColor, direction);
    }

    /**
     * Конвертує об'єкт в JSON строку для зберігання в базі даних
     */
    public String toJson() {
        if (type == BackgroundType.DEFAULT) {
            return "";
        }
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"").append(type.name()).append("\"");
        
        if (primaryColor != null) {
            json.append(",\"primaryColor\":\"").append(primaryColor).append("\"");
        }
        
        if (secondaryColor != null) {
            json.append(",\"secondaryColor\":\"").append(secondaryColor).append("\"");
        }
        
        if (gradientDirection > 0) {
            json.append(",\"gradientDirection\":").append(gradientDirection);
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Створює об'єкт з JSON строки
     */
    public static NoteBackground fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return createDefault();
        }
        
        try {
            // Простий парсинг JSON (можна замінити на Gson якщо потрібно)
            NoteBackground background = new NoteBackground();
            
            if (json.contains("\"type\":\"COLOR\"")) {
                background.type = BackgroundType.COLOR;
            } else if (json.contains("\"type\":\"GRADIENT\"")) {
                background.type = BackgroundType.GRADIENT;
            }
            
            // Отримуємо primaryColor
            String primaryPattern = "\"primaryColor\":\"";
            int primaryStart = json.indexOf(primaryPattern);
            if (primaryStart != -1) {
                primaryStart += primaryPattern.length();
                int primaryEnd = json.indexOf("\"", primaryStart);
                if (primaryEnd != -1) {
                    background.primaryColor = json.substring(primaryStart, primaryEnd);
                }
            }
            
            // Отримуємо secondaryColor
            String secondaryPattern = "\"secondaryColor\":\"";
            int secondaryStart = json.indexOf(secondaryPattern);
            if (secondaryStart != -1) {
                secondaryStart += secondaryPattern.length();
                int secondaryEnd = json.indexOf("\"", secondaryStart);
                if (secondaryEnd != -1) {
                    background.secondaryColor = json.substring(secondaryStart, secondaryEnd);
                }
            }
            
            // Отримуємо gradientDirection
            String directionPattern = "\"gradientDirection\":";
            int directionStart = json.indexOf(directionPattern);
            if (directionStart != -1) {
                directionStart += directionPattern.length();
                int directionEnd = json.indexOf(",", directionStart);
                if (directionEnd == -1) directionEnd = json.indexOf("}", directionStart);
                if (directionEnd != -1) {
                    try {
                        background.gradientDirection = Integer.parseInt(json.substring(directionStart, directionEnd).trim());
                    } catch (NumberFormatException e) {
                        background.gradientDirection = 0;
                    }
                }
            }
            
            return background;
        } catch (Exception e) {
            return createDefault();
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        NoteBackground that = (NoteBackground) obj;
        return type == that.type &&
               gradientDirection == that.gradientDirection &&
               java.util.Objects.equals(primaryColor, that.primaryColor) &&
               java.util.Objects.equals(secondaryColor, that.secondaryColor);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, primaryColor, secondaryColor, gradientDirection);
    }
    
    @NonNull
    @Override
    public String toString() {
        return "NoteBackground{" +
               "type=" + type +
               ", primaryColor='" + primaryColor + '\'' +
               ", secondaryColor='" + secondaryColor + '\'' +
               ", gradientDirection=" + gradientDirection +
               '}';
    }
}
