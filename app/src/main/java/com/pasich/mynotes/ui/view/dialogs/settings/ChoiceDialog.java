package com.pasich.mynotes.ui.view.dialogs.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;

/**
 * A settings choice in the style of the theme mode dialog: each option has an icon, a name and a
 * line saying what it does, and the current one is checked (and announced as selected).
 */
public final class ChoiceDialog {

    /** One option. */
    public record Option(@DrawableRes int icon, @StringRes int name, @StringRes int summary) {}

    public interface Callback {
        void onSelected(int which);
    }

    private ChoiceDialog() {}

    public static void show(
            @NonNull Context ctx,
            @StringRes int title,
            @NonNull Option[] options,
            int checked,
            @NonNull Callback callback) {
        BaseAdapter adapter =
                new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return options.length;
                    }

                    @Override
                    public Option getItem(int position) {
                        return options[position];
                    }

                    @Override
                    public long getItemId(int position) {
                        return position;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view =
                                convertView != null
                                        ? convertView
                                        : LayoutInflater.from(parent.getContext())
                                                .inflate(
                                                        R.layout.item_choice_dialog, parent, false);
                        Option option = options[position];
                        boolean selected = position == checked;

                        ImageView icon = view.findViewById(R.id.choiceIcon);
                        TextView name = view.findViewById(R.id.choiceName);
                        TextView summary = view.findViewById(R.id.choiceSummary);
                        ImageView check = view.findViewById(R.id.choiceSelected);

                        icon.setImageResource(option.icon());
                        name.setText(option.name());
                        name.setTypeface(
                                Typeface.create(
                                        name.getTypeface(),
                                        selected ? Typeface.BOLD : Typeface.NORMAL));
                        summary.setText(option.summary());
                        check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                        ViewCompat.setStateDescription(
                                view, selected ? ctx.getString(R.string.choice_selected) : null);
                        return view;
                    }
                };

        new MaterialAlertDialogBuilder(ctx, R.style.Theme_MyNotes_Dialog)
                .setTitle(title)
                .setAdapter(adapter, (dialog, which) -> callback.onSelected(which))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
