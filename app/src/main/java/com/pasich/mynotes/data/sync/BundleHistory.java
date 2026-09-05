package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns every bundle an account holds into the one remote state a sync merges against.
 *
 * <p>Bundles are immutable and name their parents, so the remote state is the fold of the frontier
 * heads plus the unresolved alternatives and settled versions they carry. Kept apart from the Drive
 * transport so the same fold serves an in-memory backend in tests: the convergence of two peers is
 * a property of this fold and the store together, and has to be provable without a network.
 */
final class BundleHistory {

    /** Everything a read of the history yields. */
    static final class Fold {
        final List<String> frontier;
        final SyncSnapshot merged;
        final List<SyncMergeResult.Conflict> conflicts;
        final List<SyncRecord> alternatives;
        final Set<String> resolvedAlternativeIds;

        private Fold(
                List<String> frontier,
                SyncSnapshot merged,
                List<SyncMergeResult.Conflict> conflicts,
                List<SyncRecord> alternatives,
                Set<String> resolvedAlternativeIds) {
            this.frontier = Collections.unmodifiableList(frontier);
            this.merged = merged;
            this.conflicts = Collections.unmodifiableList(conflicts);
            this.alternatives = Collections.unmodifiableList(alternatives);
            this.resolvedAlternativeIds = Collections.unmodifiableSet(resolvedAlternativeIds);
        }
    }

    private BundleHistory() {}

    @NonNull
    static Fold fold(
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundlesByLogicalId,
            @NonNull SyncMerger merger)
            throws IOException {
        validateBundleDag(bundlesByLogicalId);
        List<String> frontier = computeFrontier(bundlesByLogicalId);
        SyncSnapshot merged = SyncSnapshot.empty();
        List<SyncMergeResult.Conflict> conflicts = new ArrayList<>();
        for (String bundleId : frontier) {
            // Both sides are remote bundle heads. Naming them explicitly stops the accumulator
            // being reported to the user as "this device".
            SyncMergeResult result =
                    merger.merge(
                            merged,
                            bundlesByLogicalId.get(bundleId).getSnapshot(),
                            SyncMergeResult.Source.REMOTE,
                            SyncMergeResult.Source.REMOTE);
            merged = result.getMergedSnapshot();
            conflicts.addAll(result.getConflicts());
        }
        // Alternatives and the resolutions that retire them travel with the bundles, so a device
        // that has never seen a conflict still discovers it and a device that resolved one still
        // retires it everywhere.
        Set<String> resolvedAlternativeIds = new HashSet<>();
        for (String bundleId : frontier) {
            resolvedAlternativeIds.addAll(
                    bundlesByLogicalId.get(bundleId).getResolvedAlternativeIds());
        }
        Map<String, SyncRecord> alternativesByVersion = new java.util.LinkedHashMap<>();
        for (String bundleId : frontier) {
            for (SyncRecord alternative : bundlesByLogicalId.get(bundleId).getAlternatives()) {
                String versionId = alternative.getCanonicalPayloadHash();
                if (resolvedAlternativeIds.contains(versionId)) continue;
                SyncRecord winner = merged.find(alternative.getType(), alternative.getId());
                if (winner == null || winner.getCanonicalPayloadHash().equals(versionId)) {
                    // Nothing to choose between: the alternative is the current value, or its
                    // record no longer exists at all.
                    continue;
                }
                alternativesByVersion.putIfAbsent(versionId, alternative);
            }
        }
        List<SyncRecord> alternatives = new ArrayList<>(alternativesByVersion.values());
        for (SyncRecord alternative : alternatives) {
            SyncRecord winner = merged.find(alternative.getType(), alternative.getId());
            conflicts.add(
                    new SyncMergeResult.Conflict(
                            winner,
                            alternative,
                            SyncMergeResult.Source.REMOTE,
                            SyncMergeResult.Source.REMOTE));
        }
        return new Fold(frontier, merged, conflicts, alternatives, resolvedAlternativeIds);
    }

    /**
     * Checks the ancestry graph without requiring every historical bundle to still exist.
     *
     * <p>A missing ancestor used to be fatal, which inverted the rule that cleanup must never be
     * needed for correctness: one bundle trashed by hand, or aged out of Drive's own trash, and
     * sync failed forever with no way back. It is safe to tolerate because a bundle is a complete
     * snapshot rather than a delta — every descendant already contains everything its ancestors
     * held, including their unresolved alternatives — so an absent ancestor removes nothing from
     * the state a head describes. It also cannot be a frontier head itself, since a head is a
     * bundle no present bundle claims as a parent.
     */
    private static void validateBundleDag(
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundles) throws IOException {
        Map<String, List<String>> parentsById = new HashMap<>();
        for (Map.Entry<String, SyncBundleCodec.DecodedBundle> entry : bundles.entrySet()) {
            parentsById.put(entry.getKey(), entry.getValue().getParentBundleIds());
        }
        validateAncestry(parentsById);
    }

    /**
     * Rejects a cycle in the parent graph.
     *
     * <p>Iterative on purpose: the recursive walk went one frame deeper per ancestor, so a long
     * linear history — exactly what an account that syncs after every edit accumulates — could
     * overflow the worker's stack, and a {@code StackOverflowError} is not an {@code IOException}
     * the sync knows how to report.
     */
    static void validateAncestry(@NonNull Map<String, ? extends Collection<String>> parentsById)
            throws IOException {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Deque<Frame> stack = new ArrayDeque<>();
        for (String root : parentsById.keySet()) {
            if (visited.contains(root)) {
                continue;
            }
            visiting.add(root);
            stack.push(new Frame(root, parentsById.get(root).iterator()));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (!frame.parents.hasNext()) {
                    stack.pop();
                    visiting.remove(frame.bundleId);
                    visited.add(frame.bundleId);
                    continue;
                }
                String parent = frame.parents.next();
                Collection<String> grandparents = parentsById.get(parent);
                if (grandparents == null || visited.contains(parent)) {
                    // An ancestor that is no longer stored, or one already walked.
                    continue;
                }
                if (!visiting.add(parent)) {
                    throw new IOException("Drive bundle ancestry contains a cycle");
                }
                stack.push(new Frame(parent, grandparents.iterator()));
            }
        }
    }

    private static final class Frame {
        private final String bundleId;
        private final Iterator<String> parents;

        private Frame(String bundleId, Iterator<String> parents) {
            this.bundleId = bundleId;
            this.parents = parents;
        }
    }

    @NonNull
    private static List<String> computeFrontier(
            @NonNull Map<String, SyncBundleCodec.DecodedBundle> bundles) {
        Set<String> ancestors = new HashSet<>();
        for (SyncBundleCodec.DecodedBundle bundle : bundles.values()) {
            ancestors.addAll(bundle.getParentBundleIds());
        }
        List<String> frontier = new ArrayList<>();
        for (String bundleId : bundles.keySet()) {
            if (!ancestors.contains(bundleId)) frontier.add(bundleId);
        }
        frontier.sort(Comparator.naturalOrder());
        return frontier;
    }
}
