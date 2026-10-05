package com.pasich.mynotes.ui.main;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.RadioButton;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.pasich.mynotes.R;
import com.pasich.mynotes.ui.view.dialogs.main.ViewOptionsDialog;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.tool.FormatListTool;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ViewOptionsDialogTest {

    /** Stands in for MainActivity: records what the sheet asks it to apply. */
    public static class Host extends AppCompatActivity implements ViewOptionsDialog.Listener {
        final List<String> sorts = new ArrayList<>();
        final List<Integer> formats = new ArrayList<>();

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            setTheme(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar);
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onViewSortSelected(String sortParam) {
            sorts.add(sortParam);
        }

        @Override
        public void onViewLayoutSelected(int format) {
            formats.add(format);
        }
    }

    private Host host;

    private View open(String sort, int format) {
        host = Robolectric.buildActivity(Host.class).setup().get();
        ViewOptionsDialog dialog = ViewOptionsDialog.newInstance(sort, format);
        dialog.show(host.getSupportFragmentManager(), ViewOptionsDialog.TAG);
        host.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        return dialog.requireView();
    }

    @Test
    public void showsTheCurrentSortAndLayout() {
        View root = open(SortParam.DataReserve, FormatListTool.FORMAT_GRID);

        assertThat(((RadioButton) root.findViewById(R.id.sortOldest)).isChecked()).isTrue();
        assertThat(((RadioButton) root.findViewById(R.id.sortNewest)).isChecked()).isFalse();
        assertThat(((MaterialButton) root.findViewById(R.id.layoutGrid)).isChecked()).isTrue();
        assertThat(((MaterialButton) root.findViewById(R.id.layoutList)).isChecked()).isFalse();
        assertThat(host.sorts).isEmpty();
        assertThat(host.formats).isEmpty();
    }

    @Test
    public void choosingAnotherSort_appliesIt() {
        View root = open(SortParam.DataSort, FormatListTool.FORMAT_LIST);

        root.findViewById(R.id.sortOldest).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(host.sorts).containsExactly(SortParam.DataReserve);
        assertThat(host.formats).isEmpty();
    }

    @Test
    public void choosingAnotherLayout_appliesIt() {
        View root = open(SortParam.DataSort, FormatListTool.FORMAT_LIST);

        root.findViewById(R.id.layoutGrid).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(host.formats).containsExactly(FormatListTool.FORMAT_GRID);
        assertThat(host.sorts).isEmpty();
    }

    @Test
    public void tappingTheCurrentChoices_appliesNothing() {
        View root = open(SortParam.DataSort, FormatListTool.FORMAT_GRID);

        root.findViewById(R.id.sortNewest).performClick();
        root.findViewById(R.id.layoutGrid).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(host.sorts).isEmpty();
        assertThat(host.formats).isEmpty();
        assertThat(((MaterialButton) root.findViewById(R.id.layoutGrid)).isChecked()).isTrue();
    }

    @Test
    public void choosingTheCustomOrder_appliesItAndShowsHowToDrag() {
        View root = open(SortParam.DataSort, FormatListTool.FORMAT_LIST);
        assertThat(root.findViewById(R.id.sortCustomHint).getVisibility()).isEqualTo(View.GONE);

        root.findViewById(R.id.sortCustom).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(host.sorts).containsExactly(SortParam.Custom);
        assertThat(root.findViewById(R.id.sortCustomHint).getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void reopenedInTheCustomOrder_showsItChecked() {
        View root = open(SortParam.Custom, FormatListTool.FORMAT_GRID);

        assertThat(((RadioButton) root.findViewById(R.id.sortCustom)).isChecked()).isTrue();
        assertThat(root.findViewById(R.id.sortCustomHint).getVisibility()).isEqualTo(View.VISIBLE);
    }
}
