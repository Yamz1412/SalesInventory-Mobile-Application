package com.app.SalesInventory;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class SalesOrderWithItems {
    @Embedded
    public SalesOrderEntity order;
    @Relation(parentColumn = "localId", entityColumn = "orderLocalId")
    public List<SalesOrderItemEntity> items;
}