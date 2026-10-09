package com.droidmaker.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.droidmaker.core.lang.Builtins;
import com.droidmaker.core.project.Project;

import java.io.File;

/** Application: holds the currently open project shared by all editor screens. */
public final class App extends Application {
    private static App instance;
    private static Project current;
    private ProjectStore store;

    /** Code being edited in the code editor (scripts, code actions, creation code ...). */
    public interface CodeTarget {
        String title();
        String get();
        void set(String code);
    }

    public static CodeTarget codeTarget;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new ProjectStore(new File(getFilesDir(), "projects"));
        // warm up the function table on a background thread
        new Thread(Builtins::ensureLoaded).start();
    }

    public static App get() { return instance; }
    public static ProjectStore store() { return instance.store; }

    public static Project project() { return current; }

    public static void open(Project p) { current = p; }

    /** Saves the open project; shows a message if that fails. */
    public static boolean save() {
        if (current == null) return true;
        try {
            current.save();
            return true;
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(instance, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return false;
        }
    }
}
