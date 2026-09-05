package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drive without the network: the same codec and the same history fold as the Drive backend, over
 * bundles kept in memory, so two real stores can be synced against each other on the device.
 */
public final class InMemoryBundleBackend implements SyncBackend {
    private final Clock clock;
    private final SyncBundleCodec codec = new SyncBundleCodec();
    private final Map<String, byte[]> bundles = new LinkedHashMap<>();
    private final Map<String, byte[]> attachments = new LinkedHashMap<>();
    private String lastReadToken = "";

    public InMemoryBundleBackend(@NonNull Clock clock) {
        this.clock = clock;
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return "memory";
    }

    @NonNull
    @Override
    public RemoteSnapshot readSnapshotResult() throws IOException {
        Map<String, SyncBundleCodec.DecodedBundle> decoded = new LinkedHashMap<>();
        for (byte[] bytes : bundles.values()) {
            SyncBundleCodec.DecodedBundle bundle = codec.decode(new ByteArrayInputStream(bytes));
            decoded.put(bundle.getBundleId(), bundle);
        }
        BundleHistory.Fold fold = BundleHistory.fold(decoded, new SyncMerger());
        lastReadToken = UUID.randomUUID().toString();
        return new RemoteSnapshot(
                fold.merged,
                fold.conflicts,
                fold.frontier,
                fold.alternatives,
                fold.resolvedAlternativeIds,
                lastReadToken);
    }

    @Override
    public void publish(@NonNull SyncPublication publication) throws IOException {
        if (!publication.getReadContext().getReadToken().equals(lastReadToken)) {
            throw new IOException("stale read context");
        }
        byte[] bytes =
                codec.encode(
                        publication.getSnapshot(),
                        clock.instant(),
                        publication.getReadContext().getFrontierBundleIds(),
                        publication.getUnresolvedAlternatives(),
                        publication.getResolvedAlternativeIds());
        bundles.put(codec.decode(new ByteArrayInputStream(bytes)).getBundleId(), bytes);
    }

    @Override
    public boolean hasAttachment(@NonNull String sha256) {
        return attachments.containsKey(sha256);
    }

    @Nullable
    @Override
    public InputStream readAttachment(@NonNull String sha256) {
        byte[] bytes = attachments.get(sha256);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    @Override
    public void writeAttachment(
            @NonNull String sha256, long sizeBytes, @NonNull InputStream content)
            throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = content.read(buffer)) != -1) out.write(buffer, 0, read);
        attachments.put(sha256, out.toByteArray());
    }
}
