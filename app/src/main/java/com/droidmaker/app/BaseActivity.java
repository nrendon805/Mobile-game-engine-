package com.droidmaker.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

import com.droidmaker.core.project.Project;

/** Base for editor screens: requires an open project (returns to the start screen otherwise). */
public abstract class BaseActivity extends Activity {
    protected Project p;

    protected boolean needsProject() { return true; }

    @Override
    protected final void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        p = App.project();
        if (needsProject() && p == null) {
            finish();
            return;
        }
        build(state);
    }

    protected abstract void build(Bundle state);
}
