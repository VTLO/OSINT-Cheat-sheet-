package io.github.osintcheatsheet;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Design kit modelled on the "pipeline" infographic: charcoal background, electric-blue
 * outlines, angled tabs, a circuit spine with nodes, grey kickers and bold white headlines.
 */
final class Panel {

    static final int BG = 0xFF171B22;
    static final int SURFACE = 0xFF1F2530;
    static final int BLUE = 0xFF2F9BEF;
    static final int BLUE_DIM = 0xFF1F5F94;
    static final int TEXT = 0xFFF2F4F7;
    static final int TEXT_DIM = 0xFF8C96A3;
    static final int GREY = 0xFF9AA3AF;

    static Typeface bold(Context c) {
        return c.getResources().getFont(R.font.montserrat_bold);
    }

    static Typeface medium(Context c) {
        return c.getResources().getFont(R.font.montserrat_medium);
    }

    /** Monospace face for notes, commands and EXIF output. */
    static Typeface mono(Context c) {
        return c.getResources().getFont(R.font.vt323);
    }

    static TextView text(Context c, float sp, int color, boolean isBold) {
        TextView tv = new TextView(c);
        tv.setTypeface(isBold ? bold(c) : medium(c));
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    static GradientDrawable rounded(int fill, float strokeDp, int strokeColor, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(Ui.dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(Ui.dp(c, strokeDp), strokeColor);
        return d;
    }

    static void press(View v, View.OnClickListener click) {
        v.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            click.onClick(view);
        });
    }

    // ------------------------------------------------------------------ tabs and cards

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
        TextView tab = text(c, 13, TEXT, true);
        tab.setText(label);
        tab.setLetterSpacing(0.04f);
        tab.setSingleLine(true);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setBackground(new TabDrawable(BLUE));
        tab.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 30), Ui.dp(c, 6));
        return tab;
    }

    /** Blue-outlined card with a tab sitting on its top edge; add children to {@link #content}. */
    static final class Card extends FrameLayout {
        final TextView tab;
        final LinearLayout content;

        Card(Context c, String tabText) {
            super(c);
            content = new LinearLayout(c);
            content.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable outline = new GradientDrawable();
            outline.setStroke(Ui.dp(c, 1.5f), BLUE);
            content.setBackground(outline);
            content.setPadding(Ui.dp(c, 16), Ui.dp(c, 26), Ui.dp(c, 16), Ui.dp(c, 14));
            LayoutParams boxLp = new LayoutParams(-1, -2);
            boxLp.topMargin = Ui.dp(c, 15);
            addView(content, boxLp);
            tab = tab(c, tabText);
            LayoutParams tabLp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            tabLp.rightMargin = Ui.dp(c, 40);
            addView(tab, tabLp);
        }

        /** Grey kicker, bold white headline and dim body, the infographic's text stack. */
        void heading(String kicker, String headline, String body) {
            Context c = getContext();
            if (kicker != null) {
                TextView k = text(c, 15, TEXT_DIM, true);
                k.setText(kicker);
                content.addView(k);
            }
            TextView h = text(c, 21, TEXT, true);
            h.setText(headline);
            h.setLineSpacing(0, 1.05f);
            content.addView(h);
            if (body != null) {
                TextView b = text(c, 13.5f, 0xFFC9CED6, false);
                b.setText(body);
                b.setLineSpacing(0, 1.15f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.dp(c, 6);
                content.addView(b, lp);
            }
        }
    }

    /** The angled double-outline frame of the infographic's highlight quote. */
    static final class AngledDrawable extends Drawable {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float unit;

        AngledDrawable(Context c) {
            unit = Ui.dp(c, 1);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(BLUE);
            fill.setColor(SURFACE);
        }

        private void outline(Rect b, float inset, float cut) {
            path.reset();
            path.moveTo(b.left + inset + cut, b.top + inset);
            path.lineTo(b.right - inset, b.top + inset);
            path.lineTo(b.right - inset, b.bottom - inset - cut);
            path.lineTo(b.right - inset - cut, b.bottom - inset);
            path.lineTo(b.left + inset, b.bottom - inset);
            path.lineTo(b.left + inset, b.top + inset + cut);
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            outline(b, unit, unit * 16);
            canvas.drawPath(path, fill);
            stroke.setStrokeWidth(unit * 3);
            canvas.drawPath(path, stroke);
            outline(b, unit * 8, unit * 12);
            stroke.setStrokeWidth(unit * 1.2f);
            canvas.drawPath(path, stroke);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static TextView quote(Context c, String text) {
        TextView q = text(c, 19, TEXT, true);
        q.setText(text);
        q.setLineSpacing(0, 1.1f);
        q.setBackground(new AngledDrawable(c));
        int p = Ui.dp(c, 24);
        q.setPadding(p, Ui.dp(c, 22), p, Ui.dp(c, 22));
        return q;
    }

    // ------------------------------------------------------------------ circuit spine

    /**
     * One segment of the vertical circuit spine: parallel traces, a square node at nodeY and a
     * connector to the card on its right. Stack segments next to cards to draw the whole pipeline.
     */
    static final class SpineView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float unit;
        private final boolean first;
        private final boolean last;
        private float nodeY;
        private float pulse = 1f;

        SpineView(Context c, boolean first, boolean last) {
            super(c);
            this.first = first;
            this.last = last;
            unit = Ui.dp(c, 1);
            nodeY = unit * 46;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(unit * 1.5f);
            line.setColor(BLUE);
            nodeFill.setColor(SURFACE);
            dot.setColor(BLUE);
        }

        void setNodeY(float dp) {
            nodeY = unit * dp;
            invalidate();
        }

        /** 0..1 scale of the node's inner dot, for the guide's animation. */
        void setPulse(float value) {
            pulse = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, h = getHeight();
            float bottom = last ? nodeY : h;
            float[] offsets = {-7, 0, 7};
            for (float o : offsets) {
                float x = cx + o * unit;
                float y0 = first ? nodeY - (o == 0 ? 30 * unit : 0) : 0;
                canvas.drawLine(x, Math.max(0, y0), x, bottom, line);
            }
            // Small vias on the side traces, like the infographic's spine.
            canvas.drawCircle(cx - 7 * unit, Math.min(h - 6 * unit, nodeY + 52 * unit), 3 * unit, line);
            if (first) canvas.drawCircle(cx, Math.max(4 * unit, nodeY - 30 * unit), 3.5f * unit, line);
            // Node: rounded square with a ring and a dot, plus the connector to the card.
            float half = 15 * unit;
            canvas.drawRoundRect(cx - half, nodeY - half, cx + half, nodeY + half, 5 * unit, 5 * unit, nodeFill);
            canvas.drawRoundRect(cx - half, nodeY - half, cx + half, nodeY + half, 5 * unit, 5 * unit, line);
            canvas.drawCircle(cx, nodeY, 7 * unit, line);
            canvas.drawCircle(cx, nodeY, 3.5f * unit * pulse, dot);
            canvas.drawLine(cx + half, nodeY, getWidth(), nodeY, line);
        }
    }

    /** Puts a card to the right of a spine segment. */
    static LinearLayout spineRow(Context c, View card, boolean first, boolean last, float nodeYdp) {
        LinearLayout row = new LinearLayout(c);
        SpineView spine = new SpineView(c, first, last);
        spine.setNodeY(nodeYdp);
        row.addView(spine, new LinearLayout.LayoutParams(Ui.dp(c, 44), -1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1f);
        cardLp.bottomMargin = Ui.dp(c, 18);
        row.addView(card, cardLp);
        return row;
    }

    // ------------------------------------------------------------------ controls

    /** Square, blue-outlined quick-access button with an icon and a caption. */
    static LinearLayout quickButton(Context c, int icon, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView square = new ImageView(c);
        square.setBackground(rounded(SURFACE, 1.5f, BLUE_DIM, 6, c));
        square.setImageResource(icon);
        square.setImageTintList(ColorStateList.valueOf(BLUE));
        int pad = Ui.dp(c, 11);
        square.setPadding(pad, pad, pad, pad);
        box.addView(square, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        TextView caption = text(c, 11, TEXT_DIM, true);
        caption.setText(label.toUpperCase(java.util.Locale.ROOT));
        caption.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(c, 5);
        box.addView(caption, lp);
        box.setContentDescription(label);
        press(box, click);
        return box;
    }

    /** "WEB" (grey outline) or ">_ TERMINAL" (blue) marker for a tool. */
    static TextView tag(Context c, boolean terminal) {
        TextView t = text(c, 10, terminal ? TEXT : GREY, true);
        t.setText(terminal ? ">_ TERMINAL" : "WEB");
        t.setLetterSpacing(0.06f);
        t.setBackground(terminal ? rounded(BLUE_DIM, 0, 0, 3, c) : rounded(0, 1, 0xFF5C6675, 3, c));
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        return t;
    }

    /** A tool (or notes) line inside a section card. */
    static LinearLayout toolRow(Context c, String name, String detail, Boolean terminal) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 9), 0, Ui.dp(c, 9));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(c, 15, TEXT, true);
        n.setText(name);
        texts.addView(n);
        if (detail != null && !detail.isEmpty()) {
            TextView d = text(c, 12, TEXT_DIM, false);
            d.setText(detail);
            d.setMaxLines(2);
            d.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(d);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (terminal != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Ui.dp(c, 10);
            row.addView(tag(c, terminal), lp);
        }
        return row;
    }

    static View divider(Context c) {
        View d = new View(c);
        d.setBackgroundColor(0xFF2A313D);
        d.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(c, 1)));
        return d;
    }

    private Panel() {
    }
}
