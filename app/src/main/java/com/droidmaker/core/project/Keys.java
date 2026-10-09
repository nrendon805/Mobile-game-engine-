package com.droidmaker.core.project;

/** Virtual key codes (Game Maker / Windows VK compatible) and their names. */
public final class Keys {
    private Keys() {}

    public static final int NOKEY = 0, ANYKEY = 1, BACKSPACE = 8, TAB = 9, ENTER = 13, SHIFT = 16,
            CONTROL = 17, ALT = 18, ESCAPE = 27, SPACE = 32, PAGEUP = 33, PAGEDOWN = 34, END = 35,
            HOME = 36, LEFT = 37, UP = 38, RIGHT = 39, DOWN = 40, INSERT = 45, DELETE = 46;

    /** Keys shown in the event picker. */
    public static final int[] PICKER = buildPicker();

    private static int[] buildPicker() {
        int[] base = {LEFT, RIGHT, UP, DOWN, SPACE, ENTER, ESCAPE, SHIFT, CONTROL, BACKSPACE, TAB, ANYKEY, NOKEY};
        int[] r = new int[base.length + 26 + 10];
        int k = 0;
        for (int b : base) r[k++] = b;
        for (int c = 'A'; c <= 'Z'; c++) r[k++] = c;
        for (int c = '0'; c <= '9'; c++) r[k++] = c;
        return r;
    }

    public static String name(int k) {
        switch (k) {
            case NOKEY: return "<no key>";
            case ANYKEY: return "<any key>";
            case BACKSPACE: return "Backspace";
            case TAB: return "Tab";
            case ENTER: return "Enter";
            case SHIFT: return "Shift";
            case CONTROL: return "Ctrl";
            case ALT: return "Alt";
            case ESCAPE: return "Escape";
            case SPACE: return "Space";
            case PAGEUP: return "Page Up";
            case PAGEDOWN: return "Page Down";
            case END: return "End";
            case HOME: return "Home";
            case LEFT: return "Left";
            case UP: return "Up";
            case RIGHT: return "Right";
            case DOWN: return "Down";
            case INSERT: return "Insert";
            case DELETE: return "Delete";
        }
        if ((k >= 'A' && k <= 'Z') || (k >= '0' && k <= '9')) return String.valueOf((char) k);
        if (k >= 112 && k <= 123) return "F" + (k - 111);
        return "Key " + k;
    }

    /** Code-friendly constant for a key (vk_left, ord("A") ...). */
    public static String codeName(int k) {
        switch (k) {
            case NOKEY: return "vk_nokey";
            case ANYKEY: return "vk_anykey";
            case BACKSPACE: return "vk_backspace";
            case TAB: return "vk_tab";
            case ENTER: return "vk_enter";
            case SHIFT: return "vk_shift";
            case CONTROL: return "vk_control";
            case ALT: return "vk_alt";
            case ESCAPE: return "vk_escape";
            case SPACE: return "vk_space";
            case LEFT: return "vk_left";
            case UP: return "vk_up";
            case RIGHT: return "vk_right";
            case DOWN: return "vk_down";
        }
        if ((k >= 'A' && k <= 'Z') || (k >= '0' && k <= '9')) return "ord(\"" + (char) k + "\")";
        return String.valueOf(k);
    }
}
