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
    console.error(err);
    throw new functions.https.HttpsError("internal", "Failed");
  }
});