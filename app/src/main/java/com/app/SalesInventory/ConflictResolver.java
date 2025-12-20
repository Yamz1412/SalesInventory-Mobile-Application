package com.app.SalesInventory.database.firestore;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;

public class ConflictResolver {
    private static final String TAG = "ConflictResolver";

    public enum ResolutionStrategy {
        REMOTE_WINS,
        LOCAL_WINS,
        MERGE,
        TIMESTAMP_LATEST
    }

    private ResolutionStrategy strategy;

    public ConflictResolver(ResolutionStrategy strategy) {
        this.strategy = strategy;
    }

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

    private Object mergeValues(Object localValue, Object remoteValue) {
        if (localValue instanceof Number && remoteValue instanceof Number) {
            double local = ((Number) localValue).doubleValue();
            double remote = ((Number) remoteValue).doubleValue();
            return (local + remote) / 2.0;
        }
        return remoteValue;
    }

    public boolean hasConflict(DocumentSnapshot local, DocumentSnapshot remote) {
        if (local == null || remote == null) {
            return false;
        }
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

    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    public void setStrategy(ResolutionStrategy strategy) {
        this.strategy = strategy;
    }
}