package com.comm.pingscore;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Shared presentation rules for the match console's dynamic controls and dialogs. */
public final class MatchUiStyler {
    private MatchUiStyler() {
    }

    public static String historySubtitle(String mode, int bestOf, int setCount) {
        String label = modeLabel(mode);
        if (bestOf <= 0) {
            return setCount > 0 ? label + " · " + setCount + " 场" : label + " · 对局记录";
        }
        return label + " · BO" + bestOf + " · " + Math.max(0, setCount) + " 局";
    }

    public static String resultLabel(int setNumber, int playerOne, int playerTwo, String winner) {
        String winnerLabel = winner == null ? "" : winner.trim();
        String suffix = winnerLabel.isEmpty() ? "" : "   " + winnerLabel + " 胜";
        return "第 " + setNumber + " 局   " + playerOne + ":" + playerTwo + suffix;
    }

    public static void styleInput(EditText input) {
        Context context = input.getContext();
        int horizontal = dp(context, 12);
        input.setBackgroundResource(R.drawable.bg_input_item);
        input.setPadding(horizontal, 0, horizontal, 0);
        input.setTextColor(context.getColor(R.color.score_ink));
        input.setHintTextColor(context.getColor(R.color.score_muted));
        input.setTextSize(16);
    }

    public static void styleSpinner(Spinner spinner) {
        Context context = spinner.getContext();
        int horizontal = dp(context, 12);
        spinner.setBackgroundResource(R.drawable.bg_spinner_input);
        spinner.setPadding(horizontal, 0, horizontal, 0);
        spinner.setPopupBackgroundDrawable(new ColorDrawable(context.getColor(R.color.score_surface)));
    }

    public static void styleModeChoice(RadioButton button) {
        Context context = button.getContext();
        button.setMinHeight(dp(context, 44));
        button.setPadding(dp(context, 4), 0, dp(context, 8), 0);
        button.setTextColor(context.getColor(R.color.score_ink));
        button.setTextSize(15);
        button.setButtonTintList(ColorStateList.valueOf(context.getColor(R.color.score_blue)));
    }

    public static void styleToggle(CheckBox toggle) {
        Context context = toggle.getContext();
        int horizontal = dp(context, 12);
        toggle.setMinHeight(dp(context, 48));
        toggle.setPadding(horizontal, 0, horizontal, 0);
        toggle.setBackgroundResource(R.drawable.bg_input_item);
        toggle.setTextColor(context.getColor(R.color.score_ink));
        toggle.setTextSize(15);
        toggle.setButtonTintList(ColorStateList.valueOf(context.getColor(R.color.score_blue)));
    }

    public static void styleDialog(AlertDialog dialog, Context context, int horizontalMarginDp) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            window.setDimAmount(0.38f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels
                    - dp(context, horizontalMarginDp), dp(context, 440));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(context.getColor(R.color.score_ink));
            title.setTextSize(20);
        }

        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(context.getColor(R.color.score_muted));
            message.setTextSize(15);
            message.setLineSpacing(dp(context, 3), 1.0f);
        }

        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                context.getColor(R.color.score_blue));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                context.getColor(R.color.score_muted));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                context.getColor(R.color.score_muted));

        ListView list = dialog.getListView();
        if (list != null) {
            list.setDivider(new ColorDrawable(context.getColor(R.color.score_border)));
            list.setDividerHeight(1);
            list.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        }
    }

    private static String modeLabel(String mode) {
        if ("entertainment".equals(mode) || "娱乐赛".equals(mode)) return "娱乐赛";
        if ("wheel".equals(mode) || "车轮赛".equals(mode)) return "车轮赛";
        if ("doubles".equals(mode) || "双打".equals(mode)) return "双打";
        if ("team".equals(mode) || "团队赛".equals(mode)) return "团队赛";
        if ("regular".equals(mode) || "正规赛".equals(mode)) return "正规赛";
        return mode == null || mode.trim().isEmpty() ? "比赛" : mode.trim();
    }

    private static void styleDialogButton(Button button, int color) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(color);
        button.setTextSize(15);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
