package com.app.SalesInventory;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class InventoryMovementsRepository {
    private static InventoryMovementsRepository instance;
    private final MutableLiveData<List<InventoryMovement>> allMovements;
    private DatabaseReference ref;
    private final ValueEventListener listener;

    private InventoryMovementsRepository(Application app) {
        allMovements = new MutableLiveData<>(new ArrayList<>());
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) ownerId = AuthManager.getInstance().getCurrentUserId();
        ref = FirebaseDatabase.getInstance().getReference("InventoryMovements").child(ownerId).child("items");
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<InventoryMovement> list = new ArrayList<>();
                if (snapshot != null) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        InventoryMovement im = ds.getValue(InventoryMovement.class);
                        if (im != null) {
                            if (im.getMovementId() == null || im.getMovementId().isEmpty()) {
                                im.setMovementId(ds.getKey() == null ? "" : ds.getKey());
                            }
                            list.add(im);
                        }
                    }
                }
                allMovements.postValue(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        ref.addValueEventListener(listener);
    }

    public static synchronized InventoryMovementsRepository getInstance(Application app) {
        if (instance == null) {
            instance = new InventoryMovementsRepository(app);
        }
        return instance;
    }

    public LiveData<List<InventoryMovement>> getAllMovements() {
        return allMovements;
    }

    public void shutdown() {
        try {
            ref.removeEventListener(listener);
        } catch (Exception ignored) {
        }
    }
}