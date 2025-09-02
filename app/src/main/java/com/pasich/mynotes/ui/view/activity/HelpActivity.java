package com.pasich.mynotes.ui.view.activity;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityHelpBinding;
import com.pasich.mynotes.utils.adapters.HelpSectionAdapter;
import com.pasich.mynotes.data.model.HelpSection;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HelpActivity extends BaseActivity {

    private ActivityHelpBinding binding;

    @Inject
    @Named("NotesItemSpaceDecoration")
    public SpacesItemDecoration itemDecoration;

    private final String actualVersionHelp = "2.1.33";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivityHelpBinding.inflate(getLayoutInflater());
        
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activityHelp));
        getWindow().setAllowEnterTransitionOverlap(true);
        
        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());
        
        setupToolbar();
        setupRecyclerView();
        loadHelpContent();
        
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void setupToolbar() {
        binding.toolbar.setTitle(getString(R.string.help_title));
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        // Створюємо LinearLayoutManager з вертикальною орієнтацією
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        binding.recyclerView.setLayoutManager(layoutManager);
        
        // Додаємо декорацію для відступів
        binding.recyclerView.addItemDecoration(itemDecoration);
        
        // Вимикаємо nested scrolling для кращої продуктивності
        binding.recyclerView.setNestedScrollingEnabled(false);

    }

    private void loadHelpContent() {
        List<HelpSection> sections = createHelpSections();
        HelpSectionAdapter adapter = new HelpSectionAdapter(sections);
        binding.recyclerView.setAdapter(adapter);
    }

    private List<HelpSection> createHelpSections() {
        List<HelpSection> sections = new ArrayList<>();
        
        // Заголовок довідки
        sections.add(new HelpSection(
            HelpSection.TYPE_HEADER,
            getString(R.string.help_title),
            getString(R.string.help_subtitle),
            null, actualVersionHelp
        ));

        // Розділ навігації (перший)
        sections.add(new HelpSection(
            HelpSection.TYPE_SECTION_TITLE,
            getString(R.string.help_navigation_title),
            null,
            R.drawable.ic_navigation_help,
            null
        ));

        // Головне меню
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_navigation_drawer_title),
            getString(R.string.help_navigation_drawer_description),
            R.drawable.ic_menu,
            null
        ));

        // Пошук
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_navigation_search_title),
            getString(R.string.help_navigation_search_description),
            R.drawable.ic_search,
            null
        ));

        // Сортування
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_navigation_sort_title),
            getString(R.string.help_navigation_sort_description),
            R.drawable.ic_sort,
            null
        ));

        // Форматування
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_navigation_format_title),
            getString(R.string.help_navigation_format_description),
            R.drawable.ic_edit_format_tiles,
            null
        ));

        // Жести свайп
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_navigation_swipe_title),
            getString(R.string.help_navigation_swipe_description),
            R.drawable.ic_gesture,
            null
        ));

        // Розділ про теги (другий)
        sections.add(new HelpSection(
            HelpSection.TYPE_SECTION_TITLE,
            getString(R.string.help_tags_title),
            null,
            R.drawable.ic_tag_help,
            null
        ));

        // Створення тегів
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_tags_create_title),
            getString(R.string.help_tags_create_description),
            R.drawable.ic_add_tag,
            null
        ));

        // Редагування тегів
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_tags_edit_title),
            getString(R.string.help_tags_edit_description),
            R.drawable.ic_edit,
            null
        ));

        // Приховування нотаток
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_tags_hide_title),
            getString(R.string.help_tags_hide_description),
            R.drawable.ic_visibility_off,
            null
        ));

        // Обмеження тегів
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_tags_limit_title),
            getString(R.string.help_tags_limit_description),
            R.drawable.ic_info,
            null
        ));

        // Додавання тегів до нотаток
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_tags_assign_title),
            getString(R.string.help_tags_assign_description),
            R.drawable.ic_label,
            null
        ));

        // Оновлення тегів
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_tags_update_title),
            getString(R.string.help_tags_update_description),
            R.drawable.ic_update,
            null
        ));

        // Розділ "Функції нотаток"
        sections.add(new HelpSection(
            HelpSection.TYPE_SECTION_TITLE,
            getString(R.string.help_notes_title),
            null,
            R.drawable.ic_notebook,
            null
        ));

        // Обмеження тексту
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_notes_limit_title),
            getString(R.string.help_notes_limit_description),
            R.drawable.ic_text_fields,
            null
        ));

        // Переклад
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_notes_translate_title),
            getString(R.string.help_notes_translate_description),
            R.drawable.ic_translate,
            null
        ));

        // Розмір тексту
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_notes_text_size_title),
            getString(R.string.help_notes_text_size_description),
            R.drawable.ic_text_size,
            null
        ));

        // Редагувати копію
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_notes_copy_title),
            getString(R.string.help_notes_copy_description),
            R.drawable.ic_copy,
            null
        ));

        // Поділитися та експорт
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_notes_share_title),
            getString(R.string.help_notes_share_description),
            R.drawable.ic_share_app,
            null
        ));

        // Розділ "Інше"
        sections.add(new HelpSection(
            HelpSection.TYPE_SECTION_TITLE,
            getString(R.string.help_other_title),
            null,
            R.drawable.ic_other,
            null
        ));

        // Захист екрану
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_other_security_title),
            getString(R.string.help_other_security_description),
            R.drawable.ic_security,
            null
        ));

        // App Shortcuts
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_other_shortcuts_title),
            getString(R.string.help_other_shortcuts_description),
            R.drawable.ic_button_help,
            null
        ));

        // Синхронізація та експорт даних
        sections.add(new HelpSection(
            HelpSection.TYPE_FEATURE,
            getString(R.string.help_other_backup_export_title),
            getString(R.string.help_other_backup_export_description),
            R.drawable.ic_export,
            null
        ));

        return sections;
    }

    @Override
    public void initListeners() {
    }

}
