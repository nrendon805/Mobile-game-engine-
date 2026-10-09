package com.droidmaker.core.runtime;

/** Services the host (Android or a test harness) provides to the running game. */
public interface Platform {
    long timeMillis();

    // sound
    void soundPlay(int snd, boolean loop);
    void soundStop(int snd);
    void soundStopAll();
    boolean soundIsPlaying(int snd);
    void soundVolume(int snd, double volume);
    void soundPitch(int snd, double pitch);

    // dialogs (block the game thread until answered)
    void showMessage(String msg);
    boolean showQuestion(String msg);
    String getString(String msg, String def);
    void toast(String msg);

    // device
    void vibrate(int ms);
    void openUrl(String url);

    // persistent key/value storage for this project
    String loadValue(String key);
    void saveValue(String key, String value);
    void deleteValue(String key);
    String readFile(String name);
    void writeFile(String name, String text);
    boolean fileExists(String name);
    void deleteFile(String name);

    /** ARGB pixels of a sprite frame (for the raycaster), or null. */
    int[] spritePixels(int sprite, int frame);
    /** ARGB pixels of a background, or null. */
    int[] backgroundPixels(int bg);

    /** The game asked to end (game_end or back button). */
    void gameEnded();

    /**
     * A script error happened. Return true to keep running (ignore) or false to stop the game.
     * May block while the user decides.
     */
    boolean scriptError(String message);

    void log(String msg);
}
