const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

const SYNC_META = "__lastModifiedBy";
const SYNC_META_VALUE = "system";
const syncCollections = [
  "products",
  "sales",
  "adjustments",
  "categories",
  "purchaseOrders",
  "deliveries",
  "alerts"
];

async function getStaffUidsForAdmin(adminUid) {
  const q = await db.collection("users").where("ownerAdminId", "==", adminUid).get();
  return q.docs.map(d => d.id);
}
async function cloneCollectionForStaff(collection, adminUid, staffUid) {
  const adminColRef = db.collection(`${collection}/${adminUid}/items`);
  const staffColRef = db.collection(`${collection}/${staffUid}/items`);
  const snapshot = await adminColRef.get();
  if (snapshot.empty) return;
  let batch = db.batch();
  let ops = 0;
  for (const doc of snapshot.docs) {
    const data = doc.data();
    const docRef = staffColRef.doc(doc.id);
    const docCopy = Object.assign({}, data, { [SYNC_META]: SYNC_META_VALUE });
    batch.set(docRef, docCopy, { merge: true });
    ops++;
    if (ops >= 400) {
      await batch.commit();
      batch = db.batch();
      ops = 0;
    }
  }
  if (ops > 0) await batch.commit();
}
async function cloneAdminToStaff(adminUid, staffUid) {
  for (const col of syncCollections) {
    try {
      await cloneCollectionForStaff(col, adminUid, staffUid);
    } catch (err) {
      console.error(`Failed cloning ${col} from ${adminUid} to ${staffUid}:`, err);
    }
  }
}

exports.cloneOnStaffCreate = functions.firestore.document("users/{uid}").onCreate(async (snap, context) => {
  const data = snap.data() || {};
  const uid = context.params.uid;
  const ownerAdminId = data.ownerAdminId;
  const role = data.role || "";
  if (!ownerAdminId) return null;
  if (String(role).toLowerCase() !== "staff") return null;
  console.log(`Detected new staff ${uid} for admin ${ownerAdminId}. Starting clone.`);
  await cloneAdminToStaff(ownerAdminId, uid);
  console.log(`Clone complete for staff ${uid}`);
  return null;
});

exports.syncCollections = functions.firestore.document("{collection}/{owner}/items/{docId}").onWrite(async (change, context) => {
  const collection = context.params.collection;
  const owner = context.params.owner;
  const docId = context.params.docId;
  if (!syncCollections.includes(collection)) return null;
  const before = change.before.exists ? change.before.data() : null;
  const after = change.after.exists ? change.after.data() : null;
  if (after && after[SYNC_META] === SYNC_META_VALUE) {
    return null;
  }
  let ownerIsStaff = false;
  let adminUid = owner;
  try {
    const ownerUserSnap = await db.collection("users").doc(owner).get();
    if (ownerUserSnap.exists) {
      const ownerUser = ownerUserSnap.data() || {};
      if (ownerUser.ownerAdminId && ownerUser.ownerAdminId !== owner) {
        ownerIsStaff = true;
        adminUid = ownerUser.ownerAdminId;
      }
    }
  } catch (e) {
    console.warn("Could not read users/" + owner, e);
  }
  async function writeDocFor(targetUid, docData) {
    const ref = db.collection(`${collection}/${targetUid}/items`).doc(docId);
    if (docData === null) {
      await ref.delete().catch(() => {});
    } else {
      const payload = Object.assign({}, docData, { [SYNC_META]: SYNC_META_VALUE });
      await ref.set(payload, { merge: true });
    }
  }
  if (!ownerIsStaff) {
    const staffUids = await getStaffUidsForAdmin(owner);
    if (!staffUids || staffUids.length === 0) return null;
    const tasks = staffUids.map(async (sUid) => {
      if (after) {
        await writeDocFor(sUid, after);
      } else {
        await writeDocFor(sUid, null);
      }
    });
    await Promise.all(tasks);
    return null;
  }
  if (ownerIsStaff) {
    if (after) {
      await writeDocFor(adminUid, after);
    } else {
      await writeDocFor(adminUid, null);
    }
    const staffUids = await getStaffUidsForAdmin(adminUid);
    const otherStaff = staffUids.filter(s => s !== owner);
    if (otherStaff.length === 0) return null;
    const tasks = otherStaff.map(async (sUid) => {
      if (after) {
        await writeDocFor(sUid, after);
      } else {
        await writeDocFor(sUid, null);
      }
    });
    await Promise.all(tasks);
    return null;
  }
  return null;
});

exports.adminUpdateUser = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "No auth");
  }

  const callerClaims = context.auth.token || {};
  
  if (!callerClaims.admin) {
    const adminDoc = await db.collection("admin").doc(context.auth.uid).get();
    if (!(adminDoc.exists && adminDoc.data().approved === true)) {
      throw new functions.https.HttpsError("permission-denied", "Only admins");
    }
  }

  const { uid, approved, role, setAsAdmin } = data || {};
  if (!uid) {
    throw new functions.https.HttpsError("invalid-argument", "Missing uid");
  }

  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  const existing = userSnap.exists ? userSnap.data() : {};

  const phone = existing?.phone || existing?.Phone || null;
  const createdAtExisting = existing?.createdAt || null;

  const updateUserFields = {};
  if (approved !== undefined) {
    if (typeof approved !== "boolean") {
      throw new functions.https.HttpsError("invalid-argument", "approved must be boolean");
    }
    updateUserFields.approved = approved;
  }

  if (role !== undefined) {
    if (typeof role !== "string") {
      throw new functions.https.HttpsError("invalid-argument", "role must be string");
    }
    updateUserFields.role = role;
  }

  try {
    if (Object.keys(updateUserFields).length > 0) {
      await userRef.set(updateUserFields, { merge: true });
    }

    if (setAsAdmin !== undefined) {
      if (typeof setAsAdmin !== "boolean") {
        throw new functions.https.HttpsError("invalid-argument", "setAsAdmin must be boolean");
      }

      if (setAsAdmin) {
        await admin.auth().setCustomUserClaims(uid, { admin: true });
        const userRecord = await admin.auth().getUser(uid).catch(() => null);

        const adminData = {
          uid: uid,
          email: existing?.email || userRecord?.email || "",
          name: existing?.name || "",
          role: "Admin",
          approved: true,
          createdAt: createdAtExisting || admin.firestore.FieldValue.serverTimestamp()
        };

        if (phone) adminData.phone = phone;

        await db.collection("admin").doc(uid).set(adminData, { merge: true });
        await db.collection("users").doc(uid).set({ 
          ownerAdminId: uid, 
          role: "Admin", 
          approved: true 
        }, { merge: true });
      } else {
        await admin.auth().setCustomUserClaims(uid, {});
        await db.collection("admin").doc(uid).delete().catch(() => {});
      }
    }

    return { success: true };
  } catch (err) {
    console.error("Error in adminUpdateUser:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed");
  }
});

exports.adminCreateStaffUser = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const adminUid = context.auth.uid;
  
  const adminDoc = await db.collection("admin").doc(adminUid).get();
  const callerClaims = context.auth.token || {};
  const isAdminClaim = callerClaims.admin === true;
  const isAdminDoc = adminDoc.exists && adminDoc.data()?.approved === true;

  if (!isAdminClaim && !isAdminDoc) {
    throw new functions.https.HttpsError("permission-denied", "Only admins can create staff");
  }

  const email = (data?.email || "").trim();
  const password = data?.password || "";
  const name = (data?.name || "").trim();
  const phone = (data?.phone || "").trim();

  if (!email || !password || !name) {
    throw new functions.https.HttpsError("invalid-argument", "Name, email and password are required");
  }

  try {
    const userRecord = await admin.auth().createUser({
      email: email,
      password: password,
      displayName: name,
      emailVerified: true
    });

    const uid = userRecord.uid;
    const profile = {
      uid: uid,
      email: email,
      name: name,
      phone: phone,
      role: "Staff",
      approved: true,
      ownerAdminId: adminUid,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await db.collection("users").doc(uid).set(profile);

    console.log(`Staff user ${uid} created for admin ${adminUid}`);
    return { uid: uid, success: true };
  } catch (err) {
    console.error("Error creating staff user:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed to create staff user");
  }
});

exports.adminSetUserPassword = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const adminUid = context.auth.uid;
  
  const adminDoc = await db.collection("admin").doc(adminUid).get();
  const callerClaims = context.auth.token || {};
  const isAdminClaim = callerClaims.admin === true;
  const isAdminDoc = adminDoc.exists && adminDoc.data()?.approved === true;

  if (!isAdminClaim && !isAdminDoc) {
    throw new functions.https.HttpsError("permission-denied", "Only admins can set staff passwords");
  }

  const targetUid = data?.uid || "";
  const newPassword = data?.password || "";

  if (!targetUid || !newPassword) {
    throw new functions.https.HttpsError("invalid-argument", "uid and password are required");
  }

  const staffDoc = await db.collection("users").doc(targetUid).get();
  if (!staffDoc.exists) {
    throw new functions.https.HttpsError("not-found", "User not found");
  }

  const staffData = staffDoc.data() || {};
  const ownerAdminId = staffData.ownerAdminId || "";

  if (ownerAdminId !== adminUid) {
    throw new functions.https.HttpsError("permission-denied", "You can only modify your own staff");
  }

  try {
    await admin.auth().updateUser(targetUid, { password: newPassword });
    return { success: true };
  } catch (err) {
    console.error("Error updating password:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed to update password");
  }
});

exports.createAdminOwner = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }

  const uid = data?.uid || "";
  if (!uid || uid !== context.auth.uid) {
    throw new functions.https.HttpsError("permission-denied", "Invalid uid");
  }

  const name = data?.name || "";
  const email = data?.email || "";
  const phone = data?.phone || "";

  
  const adminData = {
    uid: uid,
    name: name,
    email: email,
    phone: phone,
    role: "Admin",
    approved: true,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  };

  try {
    await db.collection("admin").doc(uid).set(adminData, { merge: true });
    await db.collection("users").doc(uid).set({ 
      ownerAdminId: uid, 
      role: "Admin", 
      approved: true 
    }, { merge: true });
    await admin.auth().setCustomUserClaims(uid, { admin: true });

    console.log(`Admin owner created: ${uid}`);
    return { success: true };
  } catch (err) {
    console.error("Error creating admin owner:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed to create admin owner");
  }
});

exports.onAlertCreated = functions.firestore
  .document("alerts/{owner}/items/{alertId}")
  .onCreate(async (snap, context) => {
    const owner = context.params.owner;
    const data = snap.data() || {};
    const source = data.source || "";

    if (source !== "system") {
      return null;
    }

    const type = data.type || "ALERT";
    const message = data.message || "";

    let title = "Notification";
    if (type === "CRITICAL_STOCK") title = "Critical stock alert";
    else if (type === "LOW_STOCK") title = "Low stock alert";
    else if (type === "FLOOR_STOCK") title = "Floor stock alert";
    else if (type === "NEAR_EXPIRY") title = "Near expiry alert";

    const topic = `owner_${owner}`;
    const payload = {
      notification: {
        title: title,
        body: message
      },
      data: {
        alertId: context.params.alertId || "",
        type: type
      }
    };

    try {
      await admin.messaging().sendToTopic(topic, payload);
      console.log(`Alert notification sent to topic: ${topic}`);
      return null;
    } catch (err) {
      console.error("Error sending alert notification:", err);
      return null;
    }
  });

exports.onProductWrite = functions.firestore
  .document("products/{owner}/items/{productId}")
  .onWrite(async (change, context) => {
    const owner = context.params.owner;
    const productId = context.params.productId;
    
    const before = change.before.exists ? change.before.data() : null;
    const after = change.after.exists ? change.after.data() : null;

    if (!after) return null;

    function getNumber(obj, keys, defaultVal) {
      if (!obj) return defaultVal;
      for (const k of keys) {
        if (obj[k] !== undefined && obj[k] !== null) {
          const v = obj[k];
          if (typeof v === "number") return v;
          const n = Number(v);
          if (!isNaN(n)) return n;
        }
      }
      return defaultVal;
    }

    function getTimestampMs(obj, keys) {
      if (!obj) return null;
      for (const k of keys) {
        if (obj[k] !== undefined && obj[k] !== null) {
          const v = obj[k];
          if (v && typeof v.toDate === "function") {
            return v.toDate().getTime();
          }
          if (typeof v === "number") {
            return v > 1e12 ? v : v * 1000;
          }
          if (typeof v === "string") {
            const parsed = Date.parse(v);
            if (!isNaN(parsed)) return parsed;
          }
        }
      }
      return null;
    }

    const typeCandidates = ["productType", "product_type", "type", "productCategory", "category"];
    let productType = null;
    for (const k of typeCandidates) {
      if (after[k]) {
        productType = String(after[k]).trim();
        break;
      }
    }

    if (productType) {
      const pt = productType.toLowerCase();
      if (pt.includes("menu") || pt.includes("for sale") || pt.includes("sale") || pt.includes("food")) {
        return null;
      }
    }

    const quantity = getNumber(after, ["quantity", "qty", "stock", "available"], 0);
    const prevQuantity = getNumber(before, ["quantity", "qty", "stock", "available"], null);
    const lowThreshold = getNumber(after, ["lowThreshold", "reorderLevel", "minStock"], 5);
    const criticalThreshold = getNumber(after, ["criticalThreshold", "minCritical"], 1);
    const floorLevel = getNumber(after, ["floorLevel", "floor", "minimumFloor"], 0);
    const prevFloorLevel = getNumber(before, ["floorLevel", "floor", "minimumFloor"], floorLevel);

    const expiryMs = getTimestampMs(after, ["expiry", "expiryDate", "expiryTimestamp"]);
    const prevExpiryMs = getTimestampMs(before, ["expiry", "expiryDate", "expiryTimestamp"]);
    const nearExpiryDays = getNumber(after, ["nearExpiryDays"], 7);
    const nowMs = Date.now();

    const wasLow = prevQuantity !== null ? (prevQuantity <= lowThreshold) : false;
    const isLow = quantity <= lowThreshold;
    const wasCritical = prevQuantity !== null ? (prevQuantity <= criticalThreshold) : false;
    const isCritical = quantity <= criticalThreshold;

    let wasNearExpiry = false;
    let isNearExpiry = false;
    if (expiryMs) {
      const diffDaysPrev = prevExpiryMs ? (prevExpiryMs - nowMs) / (1000 * 60 * 60 * 24) : null;
      const diffDaysNow = (expiryMs - nowMs) / (1000 * 60 * 60 * 24);
      wasNearExpiry = diffDaysPrev !== null ? (diffDaysPrev <= nearExpiryDays) : false;
      isNearExpiry = diffDaysNow <= nearExpiryDays;
    }

    const wasBelowFloor = prevQuantity !== null ? (prevFloorLevel > 0 && prevQuantity <= prevFloorLevel) : false;
    const isBelowFloor = floorLevel > 0 && quantity <= floorLevel;

    const status = (after.status || "").toString().toLowerCase();

    const alertsToCreate = [];

    if (isCritical && !wasCritical) {
      alertsToCreate.push({
        type: "CRITICAL_STOCK",
        message: `Product ${productId} has critical stock (${quantity}).`
      });
    } else if (isLow && !wasLow && !isCritical) {
      alertsToCreate.push({
        type: "LOW_STOCK",
        message: `Product ${productId} is low on stock (${quantity}).`
      });
    }

    if (isBelowFloor && !wasBelowFloor) {
      alertsToCreate.push({
        type: "FLOOR_STOCK",
        message: `Product ${productId} is at or below floor level (${quantity}).`
      });
    }

    if (isNearExpiry && !wasNearExpiry) {
      const daysLeft = Math.max(0, Math.ceil((expiryMs - nowMs) / (1000 * 60 * 60 * 24)));
      alertsToCreate.push({
        type: "NEAR_EXPIRY",
        message: `Product ${productId} is near expiry in ${daysLeft} day(s).`
      });
    }

    if (status === "damaged") {
      alertsToCreate.push({
        type: "DAMAGED_PRODUCT",
        message: `Product ${productId} marked as damaged.`
      });
    } else if (status === "missing" || status === "lost") {
      alertsToCreate.push({
        type: "MISSING_PRODUCT",
        message: `Product ${productId} is reported missing.`
      });
    }

    if (alertsToCreate.length === 0) return null;

    const alertsCollection = db.collection(`alerts/${owner}/items`);
    const promises = alertsToCreate.map(async (a) => {
      try {
        const q = await alertsCollection
          .where("productId", "==", productId)
          .where("type", "==", a.type)
          .where("read", "==", false)
          .limit(1)
          .get();

        if (!q.empty) return null;

        const alertDoc = {
          productId: productId,
          type: a.type,
          message: a.message,
          read: false,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          source: "system",
          createdBy: "system"
        };

        await alertsCollection.add(alertDoc);
        console.log(`Alert created: ${a.type} for product ${productId}`);
        return true;
      } catch (err) {
        console.error(`Error creating alert for ${productId}:`, err);
        return null;
      }
    });

    await Promise.all(promises);
    return null;
  });

exports.onDocumentDeletedArchive = functions.firestore
  .document("{collection}/{owner}/items/{docId}")
  .onDelete(async (snap, context) => {
    const collection = context.params.collection;
    const owner = context.params.owner;
    const docId = context.params.docId;
    
    const data = snap.data() || {};
    const archivePath = `archives/${owner}/${collection}/${docId}`;

    const archiveDoc = Object.assign({}, data, {
      _archivedAt: admin.firestore.FieldValue.serverTimestamp(),
      _archivedFrom: `${collection}/${owner}/items/${docId}`
    });

    try {
      await db.doc(archivePath).set(archiveDoc, { merge: true });
      console.log(`Archived document: ${archivePath}`);
      return null;
    } catch (err) {
      console.error(`Error archiving document ${archivePath}:`, err);
      return null;
    }
  });