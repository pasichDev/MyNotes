package com.pasich.mynotes.ui.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.history.NoteHistory;
import com.pasich.mynotes.data.history.NoteVersionReason;
import com.pasich.mynotes.databinding.ItemNoteVersionBinding;
import com.pasich.mynotes.databinding.ItemNoteVersionHeaderBinding;

/** One card per version of a note, newest first. */
public class NoteVersionAdapter
        extends ListAdapter<NoteVersionEntity, NoteVersionAdapter.VersionHolder> {

    /** Called when a version is tapped. */
    public interface Listener {
        void onVersionClick(@NonNull NoteVersionEntity version);
    }

    @NonNull private final Listener listener;

    public NoteVersionAdapter(@NonNull Listener listener) {
        super(
                new DiffUtil.ItemCallback<>() {
                    @Override
                    public boolean areItemsTheSame(
                            @NonNull NoteVersionEntity a, @NonNull NoteVersionEntity b) {
                        return a.id == b.id;
                    }

                    @Override
                    public boolean areContentsTheSame(
                            @NonNull NoteVersionEntity a, @NonNull NoteVersionEntity b) {
                        // A version is never edited once kept.
                        return a.id == b.id && a.createdAt == b.createdAt;
                    }
                });
        this.listener = listener;
    }

    @NonNull
    @Override
    public VersionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VersionHolder(
                ItemNoteVersionBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VersionHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class VersionHolder extends RecyclerView.ViewHolder {
        final ItemNoteVersionBinding binding;

        VersionHolder(@NonNull ItemNoteVersionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull NoteVersionEntity version) {
            Context context = itemView.getContext();
            binding.versionTime.setText(NoteVersionText.when(context, version.createdAt));
            binding.versionReason.setText(
                    NoteVersionText.reasonLabel(NoteVersionReason.fromStored(version.reason)));
            String preview = NoteVersionText.readable(version.title, version.value);
            binding.versionPreview.setText(
                    preview.isEmpty() ? context.getString(R.string.version_untitled) : preview);
            binding.versionCard.setOnClickListener(v -> listener.onVersionClick(version));
        }
    }

    /** The explanatory first row: where versions live and how many are kept. */
    public static final class Header extends RecyclerView.Adapter<Header.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(
                    ItemNoteVersionHeaderBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.binding.headerText.setText(
                    holder.itemView
                            .getContext()
                            .getString(
                                    R.string.version_history_info,
                                    NoteHistory.MAX_VERSIONS_PER_NOTE));
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ItemNoteVersionHeaderBinding binding;

            Holder(@NonNull ItemNoteVersionHeaderBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
