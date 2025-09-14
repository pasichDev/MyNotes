package com.pasich.mynotes.utils.backgrounds;

import com.pasich.mynotes.data.model.NoteBackground;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for storing predefined note backgrounds
 */
public class BackgroundPresets {
    
    // Light theme colors (15 colors)
    private static final String[] LIGHT_COLORS = {
        "#FFE5E5", // Light pink
        "#E5F5FF", // Light blue
        "#E5FFE5", // Light green
        "#FFF5E5", // Light yellow
        "#F5E5FF", // Light purple
        "#FFE5F5", // Light magenta
        "#E5FFF5", // Light mint
        "#F5FFE5", // Light lime
        "#FFE5E5", // Light coral
        "#E5E5FF", // Light indigo
        "#FFECB3", // Light amber
        "#E8F5E8", // Light emerald
        "#FFF3E0", // Light peach
        "#F3E5F5", // Light orchid
        "#E0F2F1"  // Light aqua
    };
    
    // Dark theme colors (15 colors)
    private static final String[] DARK_COLORS = {
        "#4D2626", // Dark pink
        "#264D4D", // Dark blue
        "#264D26", // Dark green
        "#4D4D26", // Dark yellow
        "#4D264D", // Dark purple
        "#4D2640", // Dark magenta
        "#264D40", // Dark mint
        "#404D26", // Dark lime
        "#4D2626", // Dark coral
        "#26264D", // Dark indigo
        "#5D4037", // Dark amber
        "#2E4A2E", // Dark emerald
        "#6A4428", // Dark peach
        "#4A2D4A", // Dark orchid
        "#2C4F4A"  // Dark aqua
    };
    
    // Light theme gradients (15 gradients)
    private static final String[][] LIGHT_GRADIENTS = {
        {"#FFE5E5", "#E5F5FF", "45"},
        {"#E5FFE5", "#FFF5E5", "90"},
        {"#F5E5FF", "#FFE5F5", "135"},
        {"#E5FFF5", "#F5FFE5", "180"},
        {"#FFE5E5", "#E5E5FF", "225"},
        {"#E5F5FF", "#E5FFE5", "270"},
        {"#FFF5E5", "#F5E5FF", "315"},
        {"#FFE5F5", "#E5FFF5", "0"},
        {"#F5FFE5", "#FFE5E5", "60"},
        {"#E5E5FF", "#E5F5FF", "120"},
        {"#FFECB3", "#E8F5E8", "30"},
        {"#FFF3E0", "#F3E5F5", "150"},
        {"#E0F2F1", "#FFECB3", "210"},
        {"#F3E5F5", "#FFF3E0", "240"},
        {"#E8F5E8", "#E0F2F1", "300"}
    };
    
    // Dark theme gradients (15 gradients)
    private static final String[][] DARK_GRADIENTS = {
        {"#4D2626", "#264D4D", "45"},
        {"#264D26", "#4D4D26", "90"},
        {"#4D264D", "#4D2640", "135"},
        {"#264D40", "#404D26", "180"},
        {"#4D2626", "#26264D", "225"},
        {"#264D4D", "#264D26", "270"},
        {"#4D4D26", "#4D264D", "315"},
        {"#4D2640", "#264D40", "0"},
        {"#404D26", "#4D2626", "60"},
        {"#26264D", "#264D4D", "120"},
        {"#5D4037", "#2E4A2E", "30"},
        {"#6A4428", "#4A2D4A", "150"},
        {"#2C4F4A", "#5D4037", "210"},
        {"#4A2D4A", "#6A4428", "240"},
        {"#2E4A2E", "#2C4F4A", "300"}
    };
    
    /**
     * Get list of color backgrounds
     */
    public static List<NoteBackground> getColorBackgrounds(boolean isDarkTheme) {
        List<NoteBackground> backgrounds = new ArrayList<>();
        
        // Add default background
        backgrounds.add(NoteBackground.createDefault());
        
        String[] colors = isDarkTheme ? DARK_COLORS : LIGHT_COLORS;
        
        for (String color : colors) {
            backgrounds.add(NoteBackground.createColor(color));
        }
        
        return backgrounds;
    }
    
    /**
     * Get list of gradient backgrounds
     */
    public static List<NoteBackground> getGradientBackgrounds(boolean isDarkTheme) {
        List<NoteBackground> backgrounds = new ArrayList<>();
        
        String[][] gradients = isDarkTheme ? DARK_GRADIENTS : LIGHT_GRADIENTS;
        
        for (String[] gradient : gradients) {
            backgrounds.add(NoteBackground.createGradient(
                gradient[0], 
                gradient[1], 
                Integer.parseInt(gradient[2])
            ));
        }
        
        return backgrounds;
    }
    
    /**
     * Get all backgrounds grouped by categories
     */
    public static List<List<NoteBackground>> getAllBackgroundsByCategory(boolean isDarkTheme) {
        List<List<NoteBackground>> allBackgrounds = new ArrayList<>();
        
        allBackgrounds.add(getColorBackgrounds(isDarkTheme));
        allBackgrounds.add(getGradientBackgrounds(isDarkTheme));
        
        return allBackgrounds;
    }
    
    /**
     * Get category names
     */
    public static String[] getCategoryNames() {
        return new String[]{"Colors", "Gradients"};
    }

}
