package io.github.osintcheatsheet;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Flipper Zero–style widgets: orange LCD panel, round icon buttons, D-pad and back button. */
final class Flipper {

    static final int ORANGE = 0xFFFF8200;
    static final int ORANGE_PRESSED = 0xFFC86400;
    static final int LCD = 0xFFFF8C1F;
    static final int DARK = 0xFF2E1700;
    static final int BORDER = 0xFF7A3D00;
    static final int BLACK = 0xFF000000;

    static Typeface pixel(Context c) {
        return c.getResources().getFont(R.font.vt323);
    }

    static Typeface title(Context c) {
        return c.getResources().getFont(R.font.silkscreen_bold);
    }

    static GradientDrawable rounded(int fill, int strokeDp, int strokeColor, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(Ui.dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(Ui.dp(c, strokeDp), strokeColor);
        return d;
    }

    /** Wraps content in the black-bezel, orange-screen LCD frame. */
    static View lcd(Context c, View content) {
        FrameLayout bezel = new FrameLayout(c);
        bezel.setBackground(rounded(BLACK, 3, BORDER, 22, c));
        int gap = Ui.dp(c, 6);
        bezel.setPadding(gap, gap, gap, gap);
        content.setBackground(rounded(LCD, 0, 0, 16, c));
        int pad = Ui.dp(c, 14);
        content.setPadding(pad, Ui.dp(c, 10), pad, Ui.dp(c, 12));
        bezel.addView(content, new FrameLayout.LayoutParams(-1, -2));
        return bezel;
    }

    /** Text drawn in black on the LCD. */
    static TextView lcdText(Context c, float sp, boolean bold) {
        TextView tv = new TextView(c);
        tv.setTextColor(BLACK);
        tv.setTypeface(bold ? title(c) : pixel(c));
        tv.setTextSize(sp);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    /** Black, orange-lettered soft key shown on the LCD ("◀ Prev." / "Next ▶"). */
    static TextView softKey(Context c, String label, View.OnClickListener click) {
        TextView key = new TextView(c);
        key.setText(label);
        key.setTypeface(title(c));
        key.setTextSize(15);
        key.setTextColor(ORANGE);
        key.setBackgroundColor(BLACK);
        int h = Ui.dp(c, 12), v = Ui.dp(c, 8);
        key.setPadding(h, v, h, v);
        key.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            click.onClick(view);
        });
        return key;
    }

    /** Round dark button with an orange icon and an orange label underneath. */
    static LinearLayout iconButton(Context c, int icon, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView circle = new ImageView(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(DARK);
        circle.setBackground(bg);
        circle.setImageResource(icon);
        circle.setImageTintList(ColorStateList.valueOf(ORANGE));
        int pad = Ui.dp(c, 15);
        circle.setPadding(pad, pad, pad, pad);
        box.addView(circle, new LinearLayout.LayoutParams(Ui.dp(c, 60), Ui.dp(c, 60)));
        TextView text = new TextView(c);
        text.setText(label);
        text.setTextColor(ORANGE);
        text.setTextSize(21);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(c, 6);
        box.addView(text, lp);
        box.setContentDescription(label);
        box.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            click.onClick(view);
        });
        return box;
    }

    /** Italic pixel wordmark, like the "FLIPPER" logo under the screen. */
    static TextView wordmark(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTypeface(title(c));
        tv.setTextSize(30);
        tv.setTextColor(ORANGE);
        tv.setLetterSpacing(0.08f);
        tv.getPaint().setTextSkewX(-0.3f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, Ui.dp(c, 12), 0);
        return tv;
    }

    /** Circular directional pad: four arrows around an OK button. */
    static final class DPad extends View {
        static final int NONE = -1, UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3, OK = 4;

        interface Listener {
            void onPress(int key);

            void onLongPressOk();
        }

        private static final long LONG_PRESS_MS = 500, REPEAT_DELAY_MS = 400, REPEAT_MS = 120;

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();
        private final RectF oval = new RectF();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private Listener listener;
        private int pressed = NONE;
        private boolean consumed;

        private final Runnable longPress = () -> {
            consumed = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (listener != null) listener.onLongPressOk();
        };

        private final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                consumed = true;
                fire(pressed);
                handler.postDelayed(this, REPEAT_MS);
            }
        };

        DPad(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            setContentDescription("Navigation pad: arrows move, centre opens");
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        private float radius() {
            return Math.min(getWidth(), getHeight()) / 2f - Ui.dp(getContext(), 4);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = radius();
            fill.setColor(ORANGE);
            canvas.drawCircle(cx, cy, r, fill);

            if (pressed != NONE) {
                fill.setColor(ORANGE_PRESSED);
                if (pressed == OK) {
                    canvas.drawCircle(cx, cy, r * 0.3f, fill);
                } else {
                    oval.set(cx - r, cy - r, cx + r, cy + r);
                    canvas.drawArc(oval, 225 + 90 * pressed, 90, true, fill);
                    fill.setColor(ORANGE);
                    canvas.drawCircle(cx, cy, r * 0.3f, fill);
                }
            }

            stroke.setColor(BORDER);
            stroke.setStrokeWidth(Ui.dp(getContext(), 5));
            canvas.drawCircle(cx, cy, r, stroke);

            stroke.setColor(BLACK);
            stroke.setStrokeWidth(Ui.dp(getContext(), 3.5f));
            canvas.drawCircle(cx, cy, r * 0.27f, stroke);
            float s = r * 0.17f, d = r * 0.66f;
            triangle.reset();
            triangle.moveTo(cx, cy - d - s);
            triangle.lineTo(cx + s * 1.1f, cy - d + s * 0.8f);
            triangle.lineTo(cx - s * 1.1f, cy - d + s * 0.8f);
            triangle.close();
            for (int i = 0; i < 4; i++) {
                canvas.save();
                canvas.rotate(90 * i, cx, cy);
                canvas.drawPath(triangle, stroke);
                canvas.restore();
            }
        }

        private int keyAt(float x, float y) {
            float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f, r = radius();
            double dist = Math.hypot(dx, dy);
            if (dist > r) return NONE;
            if (dist < r * 0.32f) return OK;
            if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? RIGHT : LEFT;
            return dy > 0 ? DOWN : UP;
        }

        private void fire(int key) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (listener != null) listener.onPress(key);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = keyAt(e.getX(), e.getY());
                    if (pressed == NONE) return false;
                    consumed = false;
                    handler.postDelayed(pressed == OK ? longPress : repeat,
                            pressed == OK ? LONG_PRESS_MS : REPEAT_DELAY_MS);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacksAndMessages(null);
                    if (!consumed && pressed != NONE && keyAt(e.getX(), e.getY()) == pressed) {
                        performClick();
                        fire(pressed);
                    }
                    pressed = NONE;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacksAndMessages(null);
                    pressed = NONE;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacksAndMessages(null);
            super.onDetachedFromWindow();
        }
    }

    /** Round orange button with a return arrow. */
    static final class BackButton extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final RectF turnBox = new RectF();

        BackButton(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            setClickable(true);
            setContentDescription("Back");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - Ui.dp(getContext(), 3);
            fill.setColor(isPressed() ? ORANGE_PRESSED : ORANGE);
            canvas.drawCircle(cx, cy, r, fill);
            stroke.setColor(BORDER);
            stroke.setStrokeWidth(Ui.dp(getContext(), 4));
            canvas.drawCircle(cx, cy, r, stroke);

            float top = cy - r * 0.22f, bottom = cy + r * 0.3f, turn = r * 0.26f;
            arrow.reset();
            arrow.moveTo(cx - r * 0.42f, top);
            arrow.lineTo(cx + r * 0.12f, top);
            turnBox.set(cx + r * 0.12f - turn, top, cx + r * 0.12f + turn, bottom);
            arrow.arcTo(turnBox, -90, 180);
            arrow.lineTo(cx - r * 0.3f, bottom);
            arrow.moveTo(cx - r * 0.2f, top - r * 0.2f);
            arrow.lineTo(cx - r * 0.42f, top);
            arrow.lineTo(cx - r * 0.2f, top + r * 0.2f);
            stroke.setColor(BLACK);
            stroke.setStrokeWidth(Ui.dp(getContext(), 3.5f));
            canvas.drawPath(arrow, stroke);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }
    }

    private Flipper() {
    }
}
