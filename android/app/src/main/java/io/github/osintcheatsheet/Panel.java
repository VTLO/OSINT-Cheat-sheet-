package io.github.osintcheatsheet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Widgets from the other two references: the cyber-infographic (charcoal, electric blue,
 * angled tabs, circuit traces) and the MPC drum machine (navy chassis, pads, transport keys).
 */
final class Panel {

    static final int BG = 0xFF171B22;
    static final int SURFACE = 0xFF1F2530;
    static final int BLUE = 0xFF2F9BEF;
    static final int BLUE_DIM = 0xFF1F5F94;
    static final int TEXT = 0xFFF2F4F7;
    static final int TEXT_DIM = 0xFF8C96A3;
    static final int NAVY = 0xFF1C3A70;
    static final int NAVY_GRID = 0xFF0E1C36;
    static final int KEY = 0xFFECEFF3;
    static final int KEY_PRESSED = 0xFFBFC6D0;
    static final int KEY_TEXT = 0xFF1A1F27;
    static final int PAD_GREY = 0xFFB9BCC2;
    static final int PAD_YELLOW = 0xFFF0B43C;
    static final int PAD_GREEN = 0xFF7BE04A;
    static final int PAD_SELECTED = 0xFFF5A030;
    static final int PAD_RING = 0xFFFFF06A;
    static final int PAD_EMPTY = 0xFF27354F;
    static final int REC_RED = 0xFFC41E2C;
    static final int LED_OFF = 0xFF3A3F46;
    static final int LED_GREEN = 0xFF4CFF3A;
    static final int LED_RED = 0xFFFF3B3B;

    /** Pad looks, mirroring how the MPC lights pads by content. */
    static final int PAD_STATE_EMPTY = 0, PAD_STATE_WEB = 1, PAD_STATE_CLI = 2, PAD_STATE_NOTES = 3;

    // ------------------------------------------------------------------ infographic

    /** Blue label tab with a slanted right edge ("PHASE 1 & 2"). */
    static final class TabDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        TabDrawable(int color) {
            paint.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float slant = b.height() * 0.9f;
            path.reset();
            path.moveTo(b.left, b.top);
            path.lineTo(b.right - slant, b.top);
            path.lineTo(b.right, b.bottom);
            path.lineTo(b.left, b.bottom);
            path.close();
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static TextView tab(Context c, String label) {
        TextView tab = new TextView(c);
        tab.setText(label);
        tab.setTypeface(Flipper.title(c));
        tab.setTextSize(12);
        tab.setLetterSpacing(0.06f);
        tab.setTextColor(TEXT);
        tab.setSingleLine(true);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setBackground(new TabDrawable(BLUE));
        tab.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 30), Ui.dp(c, 5));
        return tab;
    }

    /** Blue-outlined card with a tab on its top edge, a grey kicker and a bold white headline. */
    static final class SectionCard extends FrameLayout {
        final TextView tab;
        final TextView kicker;
        final TextView headline;

        SectionCard(Context c) {
            super(c);
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable outline = new GradientDrawable();
            outline.setStroke(Ui.dp(c, 1.5f), BLUE);
            box.setBackground(outline);
            box.setPadding(Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 12));
            kicker = text(c, 13, TEXT_DIM, true);
            box.addView(kicker);
            headline = text(c, 20, TEXT, true);
            headline.setMaxLines(2);
            headline.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(headline);
            LayoutParams boxLp = new LayoutParams(-1, -2);
            boxLp.topMargin = Ui.dp(c, 13);
            addView(box, boxLp);

            tab = tab(c, "");
            addView(tab, new LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        }

        void set(String tabText, String kickerText, String headlineText) {
            tab.setText(tabText);
            kicker.setText(kickerText);
            headline.setText(headlineText);
        }
    }

    /** Faint circuit traces with nodes running down both edges of the screen. */
    static final class CircuitDrawable extends Drawable {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint node = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float unit;

        CircuitDrawable(Context c) {
            unit = Ui.dp(c, 1);
            line.setColor(BLUE);
            line.setAlpha(70);
            line.setStrokeWidth(unit * 1.2f);
            line.setStyle(Paint.Style.STROKE);
            node.set(line);
            node.setAlpha(120);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawColor(BG);
            float[] xs = {b.left + unit * 5, b.left + unit * 9, b.right - unit * 9, b.right - unit * 5};
            for (float x : xs) canvas.drawLine(x, b.top, x, b.bottom, line);
            // Branches and nodes at a regular rhythm, alternating sides.
            int i = 0;
            for (float y = b.top + unit * 120; y < b.bottom - unit * 40; y += unit * 150, i++) {
                boolean left = i % 2 == 0;
                float x = left ? xs[1] : xs[2];
                float stub = left ? unit * 6 : -unit * 6;
                canvas.drawLine(x, y, x + stub, y + unit * 6, line);
                canvas.drawCircle(x + stub, y + unit * 6, unit * 2.5f, node);
                float other = left ? xs[0] : xs[3];
                canvas.drawCircle(other, y + unit * 60, unit * 2.5f, node);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    // ------------------------------------------------------------------ MPC

    static TextView text(Context c, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setTypeface(bold ? Flipper.title(c) : Flipper.sans(c));
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    /** Navy hardware chassis. */
    static GradientDrawable chassis(Context c) {
        GradientDrawable d = Flipper.rounded(NAVY, 0, 0, 14, c);
        d.setStroke(Ui.dp(c, 1.5f), BLUE_DIM);
        return d;
    }

    static TextView pad(Context c) {
        TextView pad = text(c, 10.5f, KEY_TEXT, true);
        pad.setGravity(Gravity.CENTER);
        pad.setMaxLines(3);
        pad.setEllipsize(TextUtils.TruncateAt.END);
        int p = Ui.dp(c, 5);
        pad.setPadding(p, p, p, p);
        return pad;
    }

    static void stylePad(TextView pad, int state, boolean selected, String label) {
        Context c = pad.getContext();
        int fill;
        switch (state) {
            case PAD_STATE_CLI: fill = PAD_GREEN; break;
            case PAD_STATE_NOTES: fill = PAD_YELLOW; break;
            case PAD_STATE_WEB: fill = PAD_GREY; break;
            default: fill = PAD_EMPTY; break;
        }
        GradientDrawable bg = Flipper.rounded(selected ? PAD_SELECTED : fill, selected ? 3 : 0, PAD_RING, 3, c);
        pad.setBackground(bg);
        pad.setText(state == PAD_STATE_EMPTY ? "" : label);
        pad.setEnabled(state != PAD_STATE_EMPTY);
    }

    /** "REC ● / PLAY ● / STOP" key: label and LED above a big rounded key. */
    static final class TransportKey extends LinearLayout {
        private final View led;
        private final int ledOn;

        TransportKey(Context c, String label, int ledColor, int keyColor, View.OnClickListener click) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout caption = new LinearLayout(c);
            caption.setGravity(Gravity.CENTER_VERTICAL);
            ledOn = ledColor;
            led = new View(c);
            int dot = Ui.dp(c, 9);
            if (ledColor != 0) {
                LayoutParams ledLp = new LayoutParams(dot, dot);
                ledLp.rightMargin = Ui.dp(c, 4);
                caption.addView(led, ledLp);
            }
            caption.addView(text(c, 11, TEXT, true));
            ((TextView) caption.getChildAt(caption.getChildCount() - 1)).setText(label);
            addView(caption);

            View key = new View(c);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed},
                    Flipper.rounded(Color.rgb(Color.red(keyColor) * 3 / 4, Color.green(keyColor) * 3 / 4,
                            Color.blue(keyColor) * 3 / 4), 0, 0, 9, c));
            states.addState(new int[0], Flipper.rounded(keyColor, 0, 0, 9, c));
            key.setBackground(states);
            key.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                click.onClick(v);
            });
            key.setContentDescription(label);
            LayoutParams keyLp = new LayoutParams(-1, Ui.dp(c, 36));
            keyLp.topMargin = Ui.dp(c, 5);
            addView(key, keyLp);
            setLed(ledColor == LED_GREEN);
        }

        void setLed(boolean on) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(on ? ledOn : LED_OFF);
            led.setBackground(d);
        }
    }

    /** Small "ONE" / "TWO"-style pill key with a caption above. */
    static LinearLayout pill(Context c, String label, int color, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView caption = text(c, 11, TEXT, true);
        caption.setText(label);
        box.addView(caption);
        View key = new View(c);
        key.setBackground(Flipper.rounded(color, 0, 0, 6, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 18));
        lp.topMargin = Ui.dp(c, 4);
        box.addView(key, lp);
        box.setContentDescription(label);
        box.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            click.onClick(v);
        });
        return box;
    }

    private Panel() {
    }
}
