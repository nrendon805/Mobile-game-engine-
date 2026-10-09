package com.droidmaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.droidmaker.core.project.Ev;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.ResType;

/** Two-step picker: event type, then its sub-event (alarm number, key, collision object ...). */
public final class EventPicker {
    private EventPicker() {}

    public interface Pick { void pick(int type, int num); }

    private static final String[] NAMES = {"Create", "Destroy", "Step", "Alarm", "Collision", "Touch / Mouse",
            "Key held", "Key pressed", "Key released", "Other", "Draw"};
    private static final String[] ICONS = {"✚", "✖", "⟳", "⏰", "💥", "☝", "⌨", "⌨↓", "⌨↑", "★", "🎨"};
    private static final int[] COLORS = {0xFF36A46A, 0xFFD9534F, 0xFF3D7BE0, 0xFF8A5CD6, 0xFFE08A2E, 0xFF27A9D6,
            0xFF2BA3A3, 0xFF2BA3A3, 0xFF2BA3A3, 0xFFC9A227, 0xFFD45A9A};

    public static void show(Activity a, Project p, Pick pick) {
        GridLayout g = new GridLayout(a);
        g.setColumnCount(3);
        g.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        AlertDialog[] d = new AlertDialog[1];
        for (int i = 0; i < NAMES.length; i++) {
            int k = i;
            LinearLayout t = Ui.col(a);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.ripple(Ui.round(Ui.PANEL2, Ui.dp(a, 12)), 0));
            TextView ic = Ui.text(a, ICONS[i], 22, 0xFFFFFFFF);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(Ui.round(COLORS[i], Ui.dp(a, 10)));
            t.addView(ic, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 40)));
            TextView n = Ui.text(a, NAMES[i], 12, Ui.TEXT);
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, Ui.dp(a, 4), 0, 0);
            t.addView(n);
            t.setOnClickListener(v -> { d[0].dismiss(); second(a, p, k, pick); });
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = Ui.dp(a, 92);
            gp.height = Ui.dp(a, 86);
            gp.setMargins(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
            g.addView(t, gp);
        }
        LinearLayout wrap = Ui.col(a);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(g);
        d[0] = Dialogs.builder(a).setTitle("Add event").setView(Ui.scroll(a, wrap)).setNegativeButton("Cancel", null).create();
        d[0].show();
    }

    private static void second(Activity a, Project p, int which, Pick pick) {
        switch (which) {
            case 0: pick.pick(Ev.CREATE, 0); break;
            case 1: pick.pick(Ev.DESTROY, 0); break;
            case 2:
                Dialogs.choose(a, "Step event", new String[]{"Step (every frame)", "Begin step", "End step"},
                        w -> pick.pick(Ev.STEP, w == 0 ? Ev.STEP_NORMAL : w == 1 ? Ev.STEP_BEGIN : Ev.STEP_END));
                break;
            case 3: {
                String[] n = new String[12];
                for (int i = 0; i < 12; i++) n[i] = "Alarm " + i;
                Dialogs.choose(a, "Alarm", n, w -> pick.pick(Ev.ALARM, w));
                break;
            }
            case 4:
                Dialogs.resource(a, p, ResType.OBJECT, false, r -> { if (r != null) pick.pick(Ev.COLLISION, r.id); });
                break;
            case 5: {
                String[] n = new String[Ev.MOUSE_NUMS.length];
                for (int i = 0; i < n.length; i++) n[i] = Ev.mouseName(Ev.MOUSE_NUMS[i]);
                Dialogs.choose(a, "Touch event", n, w -> pick.pick(Ev.MOUSE, Ev.MOUSE_NUMS[w]));
                break;
            }
            case 6: Dialogs.key(a, k -> pick.pick(Ev.KEYBOARD, k)); break;
            case 7: Dialogs.key(a, k -> pick.pick(Ev.KEYPRESS, k)); break;
            case 8: Dialogs.key(a, k -> pick.pick(Ev.KEYRELEASE, k)); break;
            case 9: {
                int[] nums = new int[Ev.OTHER_NUMS.length + 16 + 7];
                int k = 0;
                for (int n : Ev.OTHER_NUMS) nums[k++] = n;
                for (int u = 0; u < 16; u++) nums[k++] = Ev.O_USER0 + u;
                for (int v = 1; v < 8; v++) nums[k++] = Ev.O_OUTSIDE_VIEW0 + v;
                String[] n = new String[nums.length];
                for (int i = 0; i < nums.length; i++) n[i] = Ev.otherName(nums[i]);
                Dialogs.choose(a, "Other event", n, w -> pick.pick(Ev.OTHER, nums[w]));
                break;
            }
            case 10:
                Dialogs.choose(a, "Draw event", new String[]{"Draw (in the room / 3D world)", "Draw GUI (on top of the screen, for HUDs)"},
                        w -> pick.pick(Ev.DRAW, w == 0 ? Ev.DRAW_NORMAL : Ev.DRAW_GUI));
                break;
        }
    }
}
