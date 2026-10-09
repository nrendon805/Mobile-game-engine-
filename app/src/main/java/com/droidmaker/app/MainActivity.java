package com.droidmaker.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.droidmaker.core.project.Project;
import com.droidmaker.run.RunnerActivity;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

/** Start screen: list of projects with create, import, export and demo projects. */
public final class MainActivity extends BaseActivity {
    private static final int REQ_IMPORT = 10, REQ_EXPORT = 11;
    private LinearLayout list;
    private File exportDir;

    @Override protected boolean needsProject() { return false; }

    @Override
    protected void build(Bundle state) {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout bar = Ui.toolbar(this, "DroidMaker", false);
        bar.addView(Ui.icon(this, "⇩", v -> importProject()));
        bar.addView(Ui.icon(this, "?", v -> about()));
        root.addView(bar);

        LinearLayout content = Ui.col(this);
        content.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 24));
        LinearLayout actions = Ui.row(this);
        actions.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        TextView newBtn = Ui.button(this, "+  New project", Ui.ACCENT, v -> newProject());
        actions.addView(newBtn, Ui.lpw(0, Ui.WRAP, 1));
        View gap = new View(this);
        actions.addView(gap, Ui.lp(Ui.dp(this, 10), 1));
        actions.addView(Ui.button(this, "Demos", v -> makeDemos(true)), Ui.lp(Ui.WRAP, Ui.WRAP));
        content.addView(actions);
        content.addView(Ui.section(this, "Your projects"));
        list = Ui.col(this);
        content.addView(list);
        root.addView(Ui.scroll(this, content), Ui.lpw(Ui.MATCH, 0, 1));
        setContentView(root);

        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (!sp.getBoolean("demosCreated", false)) {
            sp.edit().putBoolean("demosCreated", true).apply();
            makeDemos(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.open(null);
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        List<ProjectStore.Entry> entries = App.store().list();
        if (entries.isEmpty()) {
            TextView t = Ui.text(this, "No projects yet.\nTap “New project” to start, or “Demos” for ready-made examples.", 15, Ui.DIM);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 24), Ui.dp(this, 40), Ui.dp(this, 24), Ui.dp(this, 40));
            list.addView(t);
            return;
        }
        for (ProjectStore.Entry e : entries) list.addView(card(e));
    }

    private View card(ProjectStore.Entry e) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        LinearLayout texts = Ui.col(this);
        TextView name = Ui.text(this, e.name, 17, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);
        String when = DateFormat.getMediumDateFormat(this).format(new Date(e.modified)) + " " + DateFormat.getTimeFormat(this).format(new Date(e.modified));
        texts.addView(Ui.text(this, e.objects + " objects · " + e.rooms + " rooms · " + when, 13, Ui.DIM));
        row.addView(texts, Ui.lpw(0, Ui.WRAP, 1));
        TextView play = Ui.icon(this, "▶", v -> RunnerActivity.start(this, e.dir));
        play.setTextColor(Ui.OK);
        row.addView(play);
        row.addView(Ui.icon(this, "⋮", v -> menu(e)));
        card.addView(row);
        card.setOnClickListener(v -> open(e.dir));
        card.setOnLongClickListener(v -> { menu(e); return true; });
        card.setBackground(Ui.ripple(Ui.round(Ui.PANEL, Ui.dp(this, 14)), 0));
        return card;
    }

    private void open(File dir) {
        try {
            Project p = Project.load(dir);
            App.open(p);
            startActivity(new Intent(this, ProjectActivity.class));
        } catch (Exception ex) {
            Dialogs.message(this, "Cannot open project", String.valueOf(ex.getMessage()));
        }
    }

    private void menu(ProjectStore.Entry e) {
        Dialogs.choose(this, e.name, new String[]{"Open", "Play", "Rename", "Duplicate", "Export (share file)", "Delete"}, w -> {
            switch (w) {
                case 0: open(e.dir); break;
                case 1: RunnerActivity.start(this, e.dir); break;
                case 2:
                    Dialogs.input(this, "Rename project", e.name, "name", false, s -> {
                        if (s.isEmpty()) return;
                        try { App.store().rename(e.dir, s); } catch (Exception ex) { Dialogs.toast(this, "Rename failed"); }
                        refresh();
                    });
                    break;
                case 3:
                    Dialogs.input(this, "Duplicate as", e.name + " copy", "name", false, s -> {
                        try { App.store().duplicate(e.dir, s.isEmpty() ? e.name + " copy" : s); } catch (Exception ex) { Dialogs.toast(this, "Duplicate failed"); }
                        refresh();
                    });
                    break;
                case 4: export(e); break;
                case 5:
                    Dialogs.confirm(this, "Delete project?", "“" + e.name + "” and all its images and sounds will be deleted. This cannot be undone.", "Delete", () -> {
                        App.store().delete(e.dir);
                        refresh();
                    });
                    break;
            }
        });
    }

    private void newProject() {
        Dialogs.input(this, "New project", "My Game", "project name", false, s -> {
            try {
                Project p = App.store().create(s.isEmpty() ? "My Game" : s);
                App.open(p);
                startActivity(new Intent(this, ProjectActivity.class));
            } catch (Exception ex) {
                Dialogs.message(this, "Cannot create project", String.valueOf(ex.getMessage()));
            }
        });
    }

    private void makeDemos(boolean ask) {
        Runnable make = () -> {
            try {
                DemoProjects.createAll(this, App.store());
                Dialogs.toast(this, "Demo projects created");
            } catch (Exception ex) {
                Dialogs.message(this, "Demo creation failed", String.valueOf(ex));
            }
            refresh();
        };
        if (ask) Dialogs.confirm(this, "Add demo projects?", "Creates a 2D platformer, a 2.5D raycast maze and a 3D world you can play and study.", "Create", make);
        else make.run();
    }

    private void importProject() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    private void export(ProjectStore.Entry e) {
        exportDir = e.dir;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, ProjectStore.safe(e.name) + ".dmk.zip");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_IMPORT) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                File d = App.store().importZip(in);
                Dialogs.toast(this, "Imported " + d.getName());
            } catch (Exception ex) {
                Dialogs.message(this, "Import failed", String.valueOf(ex.getMessage()));
            }
            refresh();
        } else if (req == REQ_EXPORT && exportDir != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                App.store().export(exportDir, out);
                Dialogs.toast(this, "Project exported");
            } catch (Exception ex) {
                Dialogs.message(this, "Export failed", String.valueOf(ex.getMessage()));
            }
        }
    }

    private void about() {
        Dialogs.message(this, "DroidMaker",
                "A Game Maker 8 style game engine that runs entirely on your phone.\n\n"
                        + "• Objects react to events (Create, Step, Collision, Touch, Draw …) with drag-and-drop actions or code.\n"
                        + "• Rooms can be 2D, 2.5D raycast (Wolfenstein style) or true 3D.\n"
                        + "• Sprites and backgrounds are drawn in the built-in pixel editor or imported.\n"
                        + "• Sounds can be imported or generated with the effect synthesizer.\n"
                        + "• The script language is GML-like: var, if, for, while, repeat, with, switch, arrays, scripts and nearly 500 functions.\n\n"
                        + "Colors in code are 0xRRGGBB (c_red = 0xFF0000).");
    }
}
