// Product.java
package com.app.SalesInventory;

import com.google.firebase.firestore.Exclude;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Product {

    @Exclude
    private long localId;

    private String productId;
    private String productName;
    private String categoryId;
    private String categoryName;
    private String description;
    private double costPrice;
    private double sellingPrice;
    private double quantity;
    private int reorderLevel;
    private int criticalLevel;
    private int ceilingLevel;
    private int floorLevel;

    private int leadTimeDays;
    private double safetyStock;

    private double deductionAmount = 1.0;
    private String unit;
    private String barcode;
    private String supplier;
    private String productLine;

    private Date dateAdded;

    private String addedBy;
    private boolean isActive;
    private boolean isSellable;
    private String productType;
    private String ownerAdminId;

    private Date expiryDate;

    private String imagePath;
    private String imageUrl;
    private String salesUnit;
    private int piecesPerUnit = 1;
    private Map<String, Integer> linkedMaterials;

    private List<Map<String, Object>> unifiedVariations = new ArrayList<>();
    private List<Map<String, Object>> bomList = new ArrayList<>();
    private List<Map<String, Object>> sizesList;
    private List<Map<String, Object>> addonsList;
    private List<Map<String, String>> notesList;

    public Product() {
        this.productType = "Raw";
        this.expiryDate  = null;
        this.floorLevel  = 0;
        this.isActive    = true;
        this.leadTimeDays = 0;
        this.safetyStock = 0.0;
    }

    public Product(long localId, String productId, String productName,
                   String categoryId, String categoryName, String description,
                   double costPrice, double sellingPrice, double quantity,
                   int reorderLevel, int criticalLevel, int ceilingLevel,
                   String unit, String barcode, String supplier,
                   long dateAdded, String addedBy, boolean isActive) {
        this.localId       = localId;
        this.productId     = productId;
        this.productName   = productName;
        this.categoryId    = categoryId;
        this.categoryName  = categoryName;
        this.description   = description;
        this.costPrice     = costPrice;
        this.sellingPrice  = sellingPrice;
        this.quantity      = Math.max(0.0, quantity);
        this.reorderLevel  = reorderLevel;
        this.criticalLevel = criticalLevel;
        this.ceilingLevel  = ceilingLevel;
        this.floorLevel    = 0;
        this.leadTimeDays  = 0;
        this.safetyStock   = 0.0;
        this.unit          = unit;
        this.barcode       = barcode;
        this.supplier      = supplier;
        this.dateAdded     = (dateAdded > 0) ? new Date(dateAdded) : new Date();
        this.addedBy       = addedBy;
        this.isActive      = isActive;
        this.productType   = "Raw";
        this.expiryDate    = null;
    }

    @Exclude public long getLocalId()             { return localId; }
    @Exclude public void setLocalId(long localId) { this.localId = localId; }

    public String getOwnerAdminId()                          { return ownerAdminId; }
    public void   setOwnerAdminId(String ownerAdminId)       { this.ownerAdminId = ownerAdminId; }

    public String getProductLine()                           { return productLine == null ? "" : productLine; }
    public void   setProductLine(String productLine)         { this.productLine = productLine; }

    public Map<String, Integer> getLinkedMaterials()         { return linkedMaterials == null ? new HashMap<>() : linkedMaterials; }
    public void setLinkedMaterials(Map<String, Integer> m)   { this.linkedMaterials = m; }

    public String getSalesUnit()                             { return salesUnit; }
    public void   setSalesUnit(String salesUnit)             { this.salesUnit = salesUnit; }

    public int  getPiecesPerUnit()                           { return piecesPerUnit; }
    public void setPiecesPerUnit(int piecesPerUnit)          { this.piecesPerUnit = piecesPerUnit; }

    public String getProductId()                             { return productId == null ? "" : productId; }
    public void   setProductId(String productId)             { this.productId = productId; }

    public String getProductName()                           { return productName == null ? "" : productName; }
    public void   setProductName(String productName)         { this.productName = productName; }

    public String getCategoryId()                            { return categoryId == null ? "" : categoryId; }
    public void   setCategoryId(String categoryId)           { this.categoryId = categoryId; }

    public String getCategoryName()                          { return categoryName == null ? "" : categoryName; }
    public void   setCategoryName(String categoryName)       { this.categoryName = categoryName; }

    public String getDescription()                           { return description == null ? "" : description; }
    public void   setDescription(String description)         { this.description = description; }

    public double getCostPrice()                             { return costPrice; }
    public void   setCostPrice(double costPrice)             { this.costPrice = costPrice; }

    public double getSellingPrice()                          { return sellingPrice; }
    public void   setSellingPrice(double sellingPrice)       { this.sellingPrice = sellingPrice; }

    public double getQuantity()                              { return quantity; }
    public void   setQuantity(double quantity)               { this.quantity = Math.max(0.0, quantity); }

    public int  getReorderLevel()                            { return reorderLevel; }
    public void setReorderLevel(int reorderLevel)            { this.reorderLevel = reorderLevel; }

    public int  getCriticalLevel()                           { return criticalLevel; }
    public void setCriticalLevel(int criticalLevel)          { this.criticalLevel = criticalLevel; }

    public int  getCeilingLevel()                            { return ceilingLevel; }
    public void setCeilingLevel(int ceilingLevel)            { this.ceilingLevel = ceilingLevel; }

    public int  getFloorLevel()                              { return floorLevel; }
    public void setFloorLevel(int floorLevel)                { this.floorLevel = Math.max(0, floorLevel); }

    public int getLeadTimeDays()                             { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays)            { this.leadTimeDays = Math.max(0, leadTimeDays); }

    public double getSafetyStock()                           { return safetyStock; }
    public void setSafetyStock(double safetyStock)           { this.safetyStock = Math.max(0.0, safetyStock); }

    public String getUnit()                                  { return unit == null ? "" : unit; }
    public void   setUnit(String unit)                       { this.unit = unit; }

    public String getBarcode()                               { return barcode == null ? "" : barcode; }
    public void   setBarcode(String barcode)                 { this.barcode = barcode; }

    public String getSupplier()                              { return supplier == null ? "" : supplier; }
    public void   setSupplier(String supplier)               { this.supplier = supplier; }

    public Date getDateAddedAsDate()                         { return dateAdded; }
    public void setDateAddedAsDate(Date date)                { this.dateAdded = date; }

    @Exclude public long getDateAdded()                      { return dateAdded != null ? dateAdded.getTime() : 0L; }
    @Exclude public void setDateAdded(long millis)           { this.dateAdded = (millis > 0) ? new Date(millis) : null; }

    public Date getExpiryDateAsDate()                        { return expiryDate; }
    public void setExpiryDateAsDate(Date expiryDate)         { this.expiryDate = expiryDate; }

    @Exclude public long getExpiryDate()                     { return expiryDate != null ? expiryDate.getTime() : 0L; }
    @Exclude public void setExpiryDate(long millis)          { this.expiryDate = (millis > 0) ? new Date(millis) : null; }

    public double getDeductionAmount()                       { return deductionAmount <= 0 ? 1.0 : deductionAmount; }
    public void   setDeductionAmount(double d)               { this.deductionAmount = d; }

    public List<Map<String, Object>> getUnifiedVariations()          { return unifiedVariations; }
    public void setUnifiedVariations(List<Map<String, Object>> list) { this.unifiedVariations = list; }

    public List<Map<String, Object>> getBomList()                    { return bomList; }
    public void setBomList(List<Map<String, Object>> bomList)        { this.bomList = bomList; }

    public String getAddedBy()                               { return addedBy == null ? "" : addedBy; }
    public void   setAddedBy(String addedBy)                 { this.addedBy = addedBy; }

    public boolean isActive()                                { return isActive; }
    public void    setActive(boolean active)                 { isActive = active; }
    public boolean isSellable()                              { return isSellable; }
    public void    setSellable(boolean sellable)             { isSellable = sellable; }

    public String getProductType()                           { return productType == null || productType.isEmpty() ? "Raw" : productType; }
    public void   setProductType(String productType)         { this.productType = productType; }

    public String getImagePath()                             { return imagePath; }
    public void   setImagePath(String imagePath)             { this.imagePath = imagePath; }

    public String getImageUrl()                              { return imageUrl; }
    public void   setImageUrl(String imageUrl)               { this.imageUrl = imageUrl; }

    @Exclude public boolean isCriticalStock() { return criticalLevel > 0 && quantity <= criticalLevel; }
    @Exclude public boolean isLowStock()      { return quantity > criticalLevel && reorderLevel > 0 && quantity <= reorderLevel; }
    @Exclude public boolean isOverstock()     { return ceilingLevel > 0 && quantity > ceilingLevel; }
    @Exclude public boolean isBelowFloor()    { return floorLevel > 0 && quantity <= floorLevel; }

    public boolean isAvailableForSale(List<Product> masterInventory) {
        if ((unifiedVariations == null || unifiedVariations.isEmpty()) && (bomList == null || bomList.isEmpty())) return true;

        if (bomList != null && !bomList.isEmpty()) {
            for (Map<String, Object> bomItem : bomList) {
                String materialName = (String) bomItem.get("materialName");
                double reqQty = bomItem.get("quantity") instanceof Number ? ((Number) bomItem.get("quantity")).doubleValue() : 0.0;

                boolean isEssential = true;
                if (bomItem.containsKey("isEssential")) {
                    Object essObj = bomItem.get("isEssential");
                    if (essObj instanceof Boolean) isEssential = (Boolean) essObj;
                    else if (essObj instanceof String) isEssential = Boolean.parseBoolean((String) essObj);
                }

                double currentStock = 0.0;
                for (Product invItem : masterInventory) {
                    if (materialName != null && materialName.equalsIgnoreCase(invItem.getProductName())) {
                        currentStock = invItem.getQuantity();
                        break;
                    }
                }

                if (isEssential && currentStock < reqQty) {
                    return false;
                }
            }
        }
        return true;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("productId",       productId);
        m.put("productName",     productName);
        m.put("categoryId",      categoryId);
        m.put("categoryName",    categoryName);
        m.put("description",     description);
        m.put("costPrice",       costPrice);
        m.put("sellingPrice",    sellingPrice);
        m.put("quantity",        quantity);
        m.put("reorderLevel",    reorderLevel);
        m.put("criticalLevel",   criticalLevel);
        m.put("ceilingLevel",    ceilingLevel);
        m.put("floorLevel",      floorLevel);
        m.put("leadTimeDays",    leadTimeDays);
        m.put("safetyStock",     safetyStock);
        m.put("deductionAmount", deductionAmount);
        m.put("unit",            unit);
        m.put("barcode",         barcode);
        m.put("supplier",        supplier);
        m.put("productLine",     productLine);
        m.put("dateAdded",       dateAdded);
        m.put("addedBy",         addedBy);
        m.put("isActive",        isActive);
        m.put("isSellable",      isSellable);
        m.put("productType",     productType);
        m.put("ownerAdminId",    ownerAdminId);
        m.put("expiryDate",      expiryDate);
        m.put("imagePath", imagePath);
        m.put("imageUrl", imageUrl);
        m.put("salesUnit",       salesUnit);
        m.put("piecesPerUnit",   piecesPerUnit);
        m.put("linkedMaterials", linkedMaterials);
        m.put("unifiedVariations", unifiedVariations);
        m.put("sizesList", sizesList);
        m.put("addonsList", addonsList);
        m.put("notesList", notesList);
        m.put("bomList", bomList);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Product fromMap(Map<String, Object> m) {
        Product p = new Product();
        if (m == null) return p;
        Object o;

        o = m.get("productId");     if (o != null) p.productId     = String.valueOf(o);
        o = m.get("productName");   if (o != null) p.productName   = String.valueOf(o);
        o = m.get("categoryId");    if (o != null) p.categoryId    = String.valueOf(o);
        o = m.get("categoryName");  if (o != null) p.categoryName  = String.valueOf(o);
        o = m.get("description");   if (o != null) p.description   = String.valueOf(o);
        o = m.get("costPrice");     if (o instanceof Number) p.costPrice     = ((Number)o).doubleValue();
        o = m.get("sellingPrice");  if (o instanceof Number) p.sellingPrice  = ((Number)o).doubleValue();
        o = m.get("quantity");      if (o instanceof Number) p.quantity      = ((Number)o).doubleValue();
        o = m.get("reorderLevel");  if (o instanceof Number) p.reorderLevel  = ((Number)o).intValue();
        o = m.get("criticalLevel"); if (o instanceof Number) p.criticalLevel = ((Number)o).intValue();
        o = m.get("ceilingLevel");  if (o instanceof Number) p.ceilingLevel  = ((Number)o).intValue();
        o = m.get("floorLevel");    if (o instanceof Number) p.floorLevel    = ((Number)o).intValue();
        o = m.get("leadTimeDays");  if (o instanceof Number) p.leadTimeDays  = ((Number)o).intValue();
        o = m.get("safetyStock");   if (o instanceof Number) p.safetyStock   = ((Number)o).doubleValue();
        o = m.get("deductionAmount"); if (o instanceof Number) p.deductionAmount = ((Number)o).doubleValue();
        o = m.get("unit");          if (o != null) p.unit          = String.valueOf(o);
        o = m.get("barcode");       if (o != null) p.barcode       = String.valueOf(o);
        o = m.get("supplier");      if (o != null) p.supplier      = String.valueOf(o);
        o = m.get("productLine");   if (o != null) p.productLine   = String.valueOf(o);
        o = m.get("addedBy");       if (o != null) p.addedBy       = String.valueOf(o);
        o = m.get("ownerAdminId");  if (o != null) p.ownerAdminId  = String.valueOf(o);
        o = m.get("productType");   if (o != null) p.productType   = String.valueOf(o);
        o = m.get("salesUnit");     if (o != null) p.salesUnit     = String.valueOf(o);
        o = m.get("piecesPerUnit"); if (o instanceof Number) p.piecesPerUnit = ((Number)o).intValue();
        o = m.get("isActive");      if (o instanceof Boolean) p.isActive    = (Boolean)o;
        o = m.get("isSellable");    if (o instanceof Boolean) p.isSellable  = (Boolean)o;

        if (m.get("imagePath") != null) p.imagePath = (String) m.get("imagePath");
        if (m.get("imageUrl") != null) p.imageUrl = (String) m.get("imageUrl");

        o = m.get("dateAdded");
        if      (o instanceof com.google.firebase.Timestamp) p.dateAdded = ((com.google.firebase.Timestamp)o).toDate();
        else if (o instanceof Date)   p.dateAdded = (Date)o;
        else if (o instanceof Number) { long v = ((Number)o).longValue(); p.dateAdded = v > 0 ? new Date(v) : null; }

        o = m.get("expiryDate");
        if      (o instanceof com.google.firebase.Timestamp) p.expiryDate = ((com.google.firebase.Timestamp)o).toDate();
        else if (o instanceof Date)   p.expiryDate = (Date)o;
        else if (o instanceof Number) { long v = ((Number)o).longValue(); p.expiryDate = v > 0 ? new Date(v) : null; }

        Object lm = m.get("linkedMaterials");
        if (lm instanceof Map) p.linkedMaterials = (Map<String,Integer>) lm;

        if (m.get("sizesList") instanceof List) p.sizesList = (List<Map<String,Object>>) m.get("sizesList");
        if (m.get("addonsList") instanceof List) p.addonsList = (List<Map<String,Object>>) m.get("addonsList");
        if (m.get("notesList") instanceof List) p.notesList = (List<Map<String,String>>) m.get("notesList");
        if (m.get("bomList") instanceof List) p.bomList = (List<Map<String,Object>>) m.get("bomList");
        return p;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(getProductId(), product.getProductId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProductId());
    }
    public List<Map<String, Object>> getSizesList() { return sizesList; }
    public void setSizesList(List<Map<String, Object>> sizesList) { this.sizesList = sizesList; }

    public List<Map<String, Object>> getAddonsList() { return addonsList; }
    public void setAddonsList(List<Map<String, Object>> addonsList) { this.addonsList = addonsList; }

    public List<Map<String, String>> getNotesList() { return notesList; }
    public void setNotesList(List<Map<String, String>> notesList) { this.notesList = notesList; }
}