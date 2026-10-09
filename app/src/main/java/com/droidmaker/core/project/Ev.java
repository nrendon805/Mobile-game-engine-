package com.droidmaker.core.project;

/**
 * Event identifiers, following Game Maker 8's layout: an event is a (type, number) pair.
 * The "number" is the alarm index, key code, collision object id, mouse/touch sub-event, etc.
 */
public final class Ev {
    private Ev() {}

    public static final int CREATE = 0, DESTROY = 1, ALARM = 2, STEP = 3, COLLISION = 4,
            KEYBOARD = 5, MOUSE = 6, OTHER = 7, DRAW = 8, KEYPRESS = 9, KEYRELEASE = 10;
    public static final int TYPE_COUNT = 11;

    // STEP numbers
    public static final int STEP_NORMAL = 0, STEP_BEGIN = 1, STEP_END = 2;
    // DRAW numbers
    public static final int DRAW_NORMAL = 0, DRAW_GUI = 1;

    // MOUSE / TOUCH numbers (GM8 values plus touch gestures)
    public static final int M_LEFT = 0, M_RIGHT = 1, M_MIDDLE = 2, M_NONE = 3,
            M_LEFT_PRESSED = 4, M_RIGHT_PRESSED = 5, M_MIDDLE_PRESSED = 6,
            M_LEFT_RELEASED = 7, M_RIGHT_RELEASED = 8, M_MIDDLE_RELEASED = 9,
            M_ENTER = 10, M_LEAVE = 11,
            M_GLOBAL_LEFT = 50, M_GLOBAL_LEFT_PRESSED = 53, M_GLOBAL_LEFT_RELEASED = 56,
            M_SWIPE_LEFT = 60, M_SWIPE_RIGHT = 61, M_SWIPE_UP = 62, M_SWIPE_DOWN = 63,
            M_DOUBLE_TAP = 64, M_LONG_PRESS = 65;

    // OTHER numbers
    public static final int O_OUTSIDE = 0, O_BOUNDARY = 1, O_GAME_START = 2, O_GAME_END = 3,
            O_ROOM_START = 4, O_ROOM_END = 5, O_NO_LIVES = 6, O_ANIMATION_END = 7,
            O_END_OF_PATH = 8, O_NO_HEALTH = 9, O_USER0 = 10, O_OUTSIDE_VIEW0 = 40;

    public static int key(int type, int num) { return type * 100000 + num; }
    public static int typeOf(int key) { return key / 100000; }
    public static int numOf(int key) { return key % 100000; }

    public static final String[] TYPE_NAMES = {
            "Create", "Destroy", "Alarm", "Step", "Collision", "Keyboard", "Touch / Mouse",
            "Other", "Draw", "Key Press", "Key Release"
    };

    /** Human-readable event name. */
    public static String name(int type, int num, Project p) {
        switch (type) {
            case CREATE: return "Create";
            case DESTROY: return "Destroy";
            case ALARM: return "Alarm " + num;
            case STEP: return num == STEP_BEGIN ? "Begin Step" : num == STEP_END ? "End Step" : "Step";
            case COLLISION: {
                GameObject o = p == null ? null : p.object(num);
                return "Collision: " + (o == null ? "<deleted>" : o.name);
            }
            case KEYBOARD: return "Key held: " + Keys.name(num);
            case KEYPRESS: return "Key pressed: " + Keys.name(num);
            case KEYRELEASE: return "Key released: " + Keys.name(num);
            case MOUSE: return mouseName(num);
            case OTHER: return otherName(num);
            case DRAW: return num == DRAW_GUI ? "Draw GUI" : "Draw";
        }
        return "?";
    }

    public static final int[] MOUSE_NUMS = {
            M_LEFT_PRESSED, M_LEFT, M_LEFT_RELEASED, M_ENTER, M_LEAVE,
            M_GLOBAL_LEFT_PRESSED, M_GLOBAL_LEFT, M_GLOBAL_LEFT_RELEASED,
            M_SWIPE_LEFT, M_SWIPE_RIGHT, M_SWIPE_UP, M_SWIPE_DOWN, M_DOUBLE_TAP, M_LONG_PRESS,
            M_RIGHT_PRESSED, M_NONE
    };

    public static String mouseName(int n) {
        switch (n) {
            case M_LEFT: return "Touch held on me";
            case M_RIGHT: return "Right button held";
            case M_MIDDLE: return "Middle button held";
            case M_NONE: return "No touch";
            case M_LEFT_PRESSED: return "Tapped me (press)";
            case M_RIGHT_PRESSED: return "Right pressed";
            case M_MIDDLE_PRESSED: return "Middle pressed";
            case M_LEFT_RELEASED: return "Released on me";
            case M_RIGHT_RELEASED: return "Right released";
            case M_MIDDLE_RELEASED: return "Middle released";
            case M_ENTER: return "Finger enters me";
            case M_LEAVE: return "Finger leaves me";
            case M_GLOBAL_LEFT: return "Global: touch held";
            case M_GLOBAL_LEFT_PRESSED: return "Global: touch pressed";
            case M_GLOBAL_LEFT_RELEASED: return "Global: touch released";
            case M_SWIPE_LEFT: return "Swipe left";
            case M_SWIPE_RIGHT: return "Swipe right";
            case M_SWIPE_UP: return "Swipe up";
            case M_SWIPE_DOWN: return "Swipe down";
            case M_DOUBLE_TAP: return "Double tap";
            case M_LONG_PRESS: return "Long press";
        }
        return "Touch " + n;
    }

    public static final int[] OTHER_NUMS = {
            O_OUTSIDE, O_BOUNDARY, O_GAME_START, O_GAME_END, O_ROOM_START, O_ROOM_END,
            O_ANIMATION_END, O_NO_LIVES, O_NO_HEALTH, O_OUTSIDE_VIEW0
    };

    public static String otherName(int n) {
        switch (n) {
            case O_OUTSIDE: return "Outside room";
            case O_BOUNDARY: return "Intersect boundary";
            case O_GAME_START: return "Game start";
            case O_GAME_END: return "Game end";
            case O_ROOM_START: return "Room start";
            case O_ROOM_END: return "Room end";
            case O_NO_LIVES: return "No more lives";
            case O_ANIMATION_END: return "Animation end";
            case O_END_OF_PATH: return "End of path";
            case O_NO_HEALTH: return "No more health";
        }
        if (n >= O_USER0 && n < O_USER0 + 16) return "User event " + (n - O_USER0);
        if (n >= O_OUTSIDE_VIEW0 && n < O_OUTSIDE_VIEW0 + 8) return "Outside view " + (n - O_OUTSIDE_VIEW0);
        return "Other " + n;
    }

    /** Ordering used for the event list in the object editor. */
    public static int sortKey(int key) {
        int t = typeOf(key), n = numOf(key);
        // create, destroy, alarm, step, collision, keyboard, press, release, mouse, other, draw
        int[] order = {0, 1, 2, 3, 4, 5, 8, 9, 10, 6, 7};
        int ot = t < order.length ? order[t] : t;
        if (t == STEP) n = n == STEP_BEGIN ? 0 : n == STEP_NORMAL ? 1 : 2;
        return ot * 100000 + n;
    }
}
