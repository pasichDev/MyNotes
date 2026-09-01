package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable set of live records and tombstones from one local or remote sync endpoint. */
public final class SyncSnapshot {

    private static final Comparator<SyncRecord> RECORD_ORDER =
            Comparator.comparing((SyncRecord record) -> record.getType().getWireValue())
                    .thenComparing(SyncRecord::getId);

    private final Map<RecordKey, SyncRecord> records;

    public SyncSnapshot(@NonNull Collection<SyncRecord> records) {
        Objects.requireNonNull(records, "records");
        Map<RecordKey, SyncRecord> copy = new LinkedHashMap<>();
        for (SyncRecord record : records) {
            SyncRecord nonNullRecord = Objects.requireNonNull(record, "record");
            RecordKey key = new RecordKey(nonNullRecord.getType(), nonNullRecord.getId());
            if (copy.put(key, nonNullRecord) != null) {
                throw new IllegalArgumentException(
                        "A snapshot may contain only one version of " + key);
            }
        }
        this.records = Collections.unmodifiableMap(copy);
    }

    @NonNull
    public static SyncSnapshot empty() {
        return new SyncSnapshot(Collections.emptyList());
    }

    @NonNull
    public List<SyncRecord> getRecords() {
        List<SyncRecord> result = new ArrayList<>(records.values());
        result.sort(RECORD_ORDER);
        return Collections.unmodifiableList(result);
    }

    @NonNull
    public List<SyncRecord> getLiveRecords(@NonNull SyncRecord.Type type) {
        return getByType(type, false);
    }

    @NonNull
    public List<SyncRecord> getTombstones() {
        List<SyncRecord> result = new ArrayList<>();
        for (SyncRecord record : records.values()) {
            if (record.isTombstone()) {
                result.add(record);
            }
        }
        result.sort(RECORD_ORDER);
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public SyncRecord find(@NonNull SyncRecord.Type type, @NonNull String id) {
        return records.get(new RecordKey(type, id));
    }

    @NonNull
    Map<RecordKey, SyncRecord> asMap() {
        return records;
    }

    @NonNull
    private List<SyncRecord> getByType(@NonNull SyncRecord.Type type, boolean tombstone) {
        Objects.requireNonNull(type, "type");
        List<SyncRecord> result = new ArrayList<>();
        for (SyncRecord record : records.values()) {
            if (record.getType() == type && record.isTombstone() == tombstone) {
                result.add(record);
            }
        }
        result.sort(RECORD_ORDER);
        return Collections.unmodifiableList(result);
    }

    static final class RecordKey implements Comparable<RecordKey> {
        private final SyncRecord.Type type;
        private final String id;

        RecordKey(SyncRecord.Type type, String id) {
            this.type = Objects.requireNonNull(type, "type");
            this.id = Objects.requireNonNull(id, "id");
        }

        @Override
        public int compareTo(@NonNull RecordKey other) {
            int typeComparison = type.getWireValue().compareTo(other.type.getWireValue());
            return typeComparison != 0 ? typeComparison : id.compareTo(other.id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RecordKey)) {
                return false;
            }
            RecordKey key = (RecordKey) other;
            return type == key.type && id.equals(key.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }

        @Override
        public String toString() {
            return type.getWireValue() + ":" + id;
        }
    }
}
