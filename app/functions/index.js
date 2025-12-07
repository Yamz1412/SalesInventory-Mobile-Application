const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.adminUpdateUser = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "No auth");
  }
  const callerClaims = context.auth.token || {};
  if (!callerClaims.admin) {
    const db = admin.firestore();
    const adminDoc = await db.collection("admin").doc(context.auth.uid).get();
    if (!(adminDoc.exists && adminDoc.data().approved === true)) {
      throw new functions.https.HttpsError("permission-denied", "Only admins");
    }
  }
  const { uid, approved, role, setAsAdmin } = data || {};
  if (!uid) {
    throw new functions.https.HttpsError("invalid-argument", "Missing uid");
  }
  const db = admin.firestore();
  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  const existing = userSnap.exists ? userSnap.data() : {};
  const phone = existing && existing.phone ? existing.phone : (existing && existing.Phone ? existing.Phone : null);
  const createdAtExisting = existing && existing.createdAt ? existing.createdAt : null;
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
        const adminData = {};
        adminData.uid = uid;
        adminData.email = (existing && existing.email) ? existing.email : (userRecord ? userRecord.email || "" : "");
        adminData.name = (existing && existing.name) ? existing.name : "";
        if (phone) adminData.phone = phone;
        adminData.role = "Admin";
        adminData.approved = true;
        if (createdAtExisting) adminData.createdAt = createdAtExisting;
        else adminData.createdAt = admin.firestore.FieldValue.serverTimestamp();
        await db.collection("admin").doc(uid).set(adminData, { merge: true });
      } else {
        await admin.auth().setCustomUserClaims(uid, {});
        await db.collection("admin").doc(uid).delete().catch(() => {});
      }
    }
    return { success: true };
  } catch (err) {
    console.error("adminUpdateUser error:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed");
  }
});

exports.adminCreateStaffUser = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }
  const adminUid = context.auth.uid;
  const db = admin.firestore();
  const adminDoc = await db.collection("admin").doc(adminUid).get();
  const callerClaims = context.auth.token || {};
  const isAdminClaim = callerClaims.admin === true;
  const isAdminDoc = adminDoc.exists && adminDoc.data() && adminDoc.data().approved === true;
  if (!isAdminClaim && !isAdminDoc) {
    throw new functions.https.HttpsError("permission-denied", "Only admins can create staff");
  }
  const email = (data && data.email ? String(data.email) : "").trim();
  const password = data && data.password ? String(data.password) : "";
  const name = (data && data.name ? String(data.name) : "").trim();
  const phone = (data && data.phone ? String(data.phone) : "").trim();
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
    return { uid: uid, success: true };
  } catch (err) {
    console.error("adminCreateStaffUser error:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed to create staff user");
  }
});

exports.adminSetUserPassword = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }
  const adminUid = context.auth.uid;
  const db = admin.firestore();
  const adminDoc = await db.collection("admin").doc(adminUid).get();
  const callerClaims = context.auth.token || {};
  const isAdminClaim = callerClaims.admin === true;
  const isAdminDoc = adminDoc.exists && adminDoc.data() && adminDoc.data().approved === true;
  if (!isAdminClaim && !isAdminDoc) {
    throw new functions.https.HttpsError("permission-denied", "Only admins can set staff passwords");
  }
  const targetUid = data && data.uid ? String(data.uid) : "";
  const newPassword = data && data.password ? String(data.password) : "";
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
    throw new functions.https.HttpsError("permission-denied", "You can only modify the accessible for staff");
  }
  try {
    await admin.auth().updateUser(targetUid, { password: newPassword });
    return { success: true };
  } catch (err) {
    console.error("adminSetUserPassword error:", err);
    throw new functions.https.HttpsError("internal", err.message || "Failed to update password");
  }
});

exports.onAlertCreated = functions.firestore
  .document('alerts/{owner}/items/{alertId}')
  .onCreate(async (snap, context) => {
    const owner = context.params.owner;
    const data = snap.data() || {};
    const source = data.source || '';
    if (source !== 'system') {
      return null;
    }
    const type = data.type || 'ALERT';
    const message = data.message || '';
    let title = 'Notification';
    if (type === 'CRITICAL_STOCK') title = 'Critical stock alert';
    else if (type === 'LOW_STOCK') title = 'Low stock alert';
    else if (type === 'FLOOR_STOCK') title = 'Floor stock alert';
    else if (type === 'NEAR_EXPIRY') title = 'Near expiry alert';
    const topic = 'owner_' + owner;
    const payload = {
      notification: {
        title: title,
        body: message
      },
      data: {
        alertId: context.params.alertId || '',
        type: type
      }
    };
    try {
      await admin.messaging().sendToTopic(topic, payload);
      return null;
    } catch (err) {
      console.error('onAlertCreated send error', err);
      return null;
    }
  });

exports.onProductWrite = functions.firestore
  .document('products/{owner}/items/{productId}')
  .onWrite(async (change, context) => {
    const owner = context.params.owner;
    const productId = context.params.productId;
    const db = admin.firestore();

    const before = change.before.exists ? change.before.data() : null;
    const after = change.after.exists ? change.after.data() : null;

    if (!after) {
      return null;
    }

    function getNumber(obj, keys, defaultVal) {
      if (!obj) return defaultVal;
      for (const k of keys) {
        if (obj[k] !== undefined && obj[k] !== null) {
          const v = obj[k];
          if (typeof v === 'number') return v;
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
          if (v && typeof v.toDate === 'function') {
            return v.toDate().getTime();
          }
          if (typeof v === 'number') {
            return v > 1e12 ? v : v * 1000;
          }
          if (typeof v === 'string') {
            const parsed = Date.parse(v);
            if (!isNaN(parsed)) return parsed;
          }
        }
      }
      return null;
    }

    const quantity = getNumber(after, ['quantity', 'qty', 'stock', 'available', 'quantityAvailable'], 0);
    const prevQuantity = getNumber(before, ['quantity', 'qty', 'stock', 'available', 'quantityAvailable'], null);

    const lowThreshold = getNumber(after, ['lowThreshold', 'reorderLevel', 'minStock', 'lowStockThreshold'], 5);
    const criticalThreshold = getNumber(after, ['criticalThreshold', 'minCritical', 'criticalStockThreshold'], 1);
    const floorLevel = getNumber(after, ['floorLevel', 'floor', 'minimumFloor'], 0);
    const prevFloorLevel = getNumber(before, ['floorLevel', 'floor', 'minimumFloor'], floorLevel);

    const expiryMs = getTimestampMs(after, ['expiry', 'expiryDate', 'expiryTimestamp', 'expirationDate']);
    const prevExpiryMs = getTimestampMs(before, ['expiry', 'expiryDate', 'expiryTimestamp', 'expirationDate']);
    const nearExpiryDays = getNumber(after, ['nearExpiryDays'], 7);

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

    const alertsToCreate = [];

    if (isCritical && !wasCritical) {
      alertsToCreate.push({
        type: 'CRITICAL_STOCK',
        message: `Product ${productId} has critical stock (${quantity}).`
      });
    } else if (isLow && !wasLow && !isCritical) {
      alertsToCreate.push({
        type: 'LOW_STOCK',
        message: `Product ${productId} is low on stock (${quantity}).`
      });
    }

    if (isBelowFloor && !wasBelowFloor) {
      alertsToCreate.push({
        type: 'FLOOR_STOCK',
        message: `Product ${productId} is at or below floor level (${quantity}).`
      });
    }

    if (isNearExpiry && !wasNearExpiry) {
      const daysLeft = Math.max(0, Math.ceil((expiryMs - nowMs) / (1000 * 60 * 60 * 24)));
      alertsToCreate.push({
        type: 'NEAR_EXPIRY',
        message: `Product ${productId} is near expiry in ${daysLeft} day(s).`
      });
    }

    if (alertsToCreate.length === 0) {
      return null;
    }

    const alertsCollection = db.collection(`alerts/${owner}/items`);
    const promises = alertsToCreate.map(async (a) => {
      try {
        const q = await alertsCollection
          .where('productId', '==', productId)
          .where('type', '==', a.type)
          .where('read', '==', false)
          .limit(1)
          .get();
        if (!q.empty) {
          return null;
        }
        const alertDoc = {
          productId: productId,
          type: a.type,
          message: a.message,
          read: false,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          source: 'system',
          createdBy: 'system'
        };
        await alertsCollection.add(alertDoc);
        return true;
      } catch (err) {
        console.error('onProductWrite alert creation error for', productId, a.type, err);
        return null;
      }
    });

    await Promise.all(promises);

    return null;
  });

exports.onDocumentDeletedArchive = functions.firestore
  .document('{collection}/{owner}/items/{docId}')
  .onDelete(async (snap, context) => {
    const collection = context.params.collection;
    const owner = context.params.owner;
    const docId = context.params.docId;
    const db = admin.firestore();
    const data = snap.data() || {};
    const archivePath = `archives/${owner}/${collection}/${docId}`;
    const archiveDoc = Object.assign({}, data);
    archiveDoc._archivedAt = admin.firestore.FieldValue.serverTimestamp();
    archiveDoc._archivedFrom = `${collection}/${owner}/items/${docId}`;
    try {
      await db.doc(archivePath).set(archiveDoc, { merge: true });
      return null;
    } catch (err) {
      console.error('archive onDelete error', err);
      return null;
    }
  });