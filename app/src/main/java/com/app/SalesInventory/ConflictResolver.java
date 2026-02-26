package com.app.SalesInventory.database.firestore;

import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Handles conflicts when data exists both locally and remotely
 */
public class ConflictResolver {
    private static final String TAG = "ConflictResolver";

    public enum ResolutionStrategy {
        REMOTE_WINS,        // Firebase data overwrites local
        LOCAL_WINS,         // Local data overwrites Firebase
        MERGE,              // Merge both versions
        TIMESTAMP_LATEST    // Latest timestamp wins
    }

    private ResolutionStrategy strategy;

    public ConflictResolver(ResolutionStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Resolve conflict for a field
     */
    public Object resolveConflict(Object localValue, Object remoteValue, long localTimestamp, long remoteTimestamp) {
        Log.d(TAG, "Resolving conflict for: " + localValue + " vs " + remoteValue);

        switch (strategy) {
            case REMOTE_WINS:
                return remoteValue;

            case LOCAL_WINS:
                return localValue;

            case TIMESTAMP_LATEST:
                return remoteTimestamp > localTimestamp ? remoteValue : localValue;

            case MERGE:
                return mergeValues(localValue, remoteValue);

            default:
                return remoteValue;
        }
    }

    /**
     * Merge values (for numeric values like quantities)
     */
    private Object mergeValues(Object localValue, Object remoteValue) {
        if (localValue instanceof Number && remoteValue instanceof Number) {
            // For numeric values, take the average
            double local = ((Number) localValue).doubleValue();
            double remote = ((Number) remoteValue).doubleValue();
            return (local + remote) / 2.0;
        }
        // For non-numeric, remote wins
        return remoteValue;
    }

    /**
     * Check if document has conflicts
     */
    public boolean hasConflict(DocumentSnapshot local, DocumentSnapshot remote) {
        if (local == null || remote == null) {
            return false;
        }

        // Check if any field is different
        for (String key : remote.getData().keySet()) {
            if (local.contains(key)) {
                Object localVal = local.get(key);
                Object remoteVal = remote.get(key);

                if (!isEqual(localVal, remoteVal)) {
                    Log.w(TAG, "Conflict detected in field: " + key);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Compare two objects for equality
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * Set resolution strategy
     */
    public void setStrategy(ResolutionStrategy strategy) {
        this.strategy = strategy;
    }
}